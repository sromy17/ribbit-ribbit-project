import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subscription } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FinnhubNewsArticle, FinnhubNewsService } from './finnhub-news.service';
import { environment } from '../../../environments/environment';

const originalKey = environment.finnhubApiKey;
const originalInterval = environment.finnhubNewsRefreshMs;

const articles: FinnhubNewsArticle[] = [
  'Nvidia semiconductors and AI', 'Healthcare and Pfizer', 'Banks and interest rates', 'Crude oil and Chevron'
].map((headline, index) => ({
  id: index + 1, headline, datetime: 1700000000, source: 'Test publisher',
  summary: '', image: '', url: `https://example.com/article/${index}`
}));

describe('Finnhub shared news feed', () => {
  let service: FinnhubNewsService;
  let http: HttpTestingController;
  let subscriptions: Subscription[];

  beforeEach(() => {
    environment.finnhubApiKey = 'test-key';
    environment.finnhubNewsRefreshMs = 60000;
    vi.useFakeTimers();
    vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(console, 'info').mockImplementation(() => {});
    localStorage.removeItem('leap-trader:finnhub:news:general');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(FinnhubNewsService);
    http = TestBed.inject(HttpTestingController);
    subscriptions = [];
  });

  afterEach(() => {
    environment.finnhubApiKey = originalKey;
    environment.finnhubNewsRefreshMs = originalInterval;
    subscriptions.forEach(subscription => subscription.unsubscribe());
    http.verify();
    localStorage.removeItem('leap-trader:finnhub:news:general');
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('shares one general request and polling timer across all sector filters', async () => {
    const results = new Map<string, FinnhubNewsArticle[]>();
    for (const category of ['general', 'technology', 'healthcare', 'finance', 'energy'] as const) {
      subscriptions.push(service.getMarketNews(category).subscribe(value => results.set(category, value)));
    }
    await vi.advanceTimersByTimeAsync(0);
    const request = http.expectOne(req => req.url === 'https://finnhub.io/api/v1/news');
    expect(request.request.params.get('category')).toBe('general');
    expect(request.request.params.get('token')).toBe('test-key');
    request.flush(articles);
    expect(results.get('general')).toEqual(articles);
    ['technology', 'healthcare', 'finance', 'energy'].forEach((category, index) => {
      expect(results.get(category)).toEqual([articles[index]]);
    });
    await vi.advanceTimersByTimeAsync(60000);
    http.expectOne(req => req.url.endsWith('/news')).flush(articles);
  });

  it('immediately restores the latest general cache when reconnecting on another tab', async () => {
    const stream = service.getMarketNews();
    const first = stream.subscribe();
    await vi.advanceTimersByTimeAsync(0);
    http.expectOne(req => req.url.endsWith('/news')).flush(articles);
    first.unsubscribe();
    const receive = vi.fn();
    subscriptions.push(service.getMarketNews('healthcare').subscribe(receive));
    expect(receive).toHaveBeenCalledWith([articles[1]]);
    http.expectNone(req => req.url.endsWith('/news'));
    await vi.advanceTimersByTimeAsync(0);
    http.expectOne(req => req.url.endsWith('/news')).flush(articles);
  });

  for (const company of [false, true]) {
    for (const status of [401, 403, 429, 500]) {
      it(`uses fallback without error logs for ${company ? 'company' : 'market'} HTTP ${status}`, async () => {
        const next = vi.fn();
        const error = vi.fn();
        const feed = company ? service.getCompanyNews('ALLE', '2026-10-01', '2026-10-22') : service.getMarketNews();
        subscriptions.push(feed.subscribe({ next, error }));
        expect(next.mock.lastCall?.[0].length).toBeGreaterThanOrEqual(12);
        await vi.advanceTimersByTimeAsync(0);
        http.expectOne(req => req.url.startsWith('https://finnhub.io/')).flush(
          {}, { status, statusText: 'Provider error' }
        );
        expect(next.mock.lastCall?.[0]).toEqual(expect.arrayContaining([
          expect.objectContaining({ source: 'Curated sample' })
        ]));
        expect(error).not.toHaveBeenCalled();
        expect(console.error).not.toHaveBeenCalled();
        expect(console.warn).not.toHaveBeenCalled();
        expect(console.info).toHaveBeenCalledTimes(1);
        if (status === 401 || status === 403) {
          subscriptions.push(service.getCompanyNews('MSFT', '2026-10-01', '2026-10-22').subscribe());
          subscriptions.push(service.getMarketNews().subscribe());
          await vi.advanceTimersByTimeAsync(60000);
          http.expectNone(req => req.url.startsWith('https://finnhub.io/'));
          expect(console.info).toHaveBeenCalledTimes(1);
        }
      });
    }
  }

  it('skips network calls with a missing key and immediately supplies both feeds', async () => {
    environment.finnhubApiKey = ' ';
    const next = vi.fn();
    subscriptions.push(service.getMarketNews().subscribe(next));
    subscriptions.push(service.getCompanyNews('ALLE', '2026-10-01', '2026-10-22').subscribe(next));
    expect(next).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(60000);
    http.expectNone(req => req.url.startsWith('https://finnhub.io/'));
    expect(console.info).toHaveBeenCalledTimes(1);
  });

  it('recovers live data on the next refresh after a timeout', async () => {
    const next = vi.fn();
    subscriptions.push(service.getMarketNews().subscribe(next));
    await vi.advanceTimersByTimeAsync(0);
    const pending = http.expectOne(req => req.url.endsWith('/news'));
    await vi.advanceTimersByTimeAsync(4000);
    expect(pending.cancelled).toBe(true);
    expect(next.mock.lastCall?.[0][0].source).toBe('Curated sample');
    await vi.advanceTimersByTimeAsync(56000);
    http.expectOne(req => req.url.endsWith('/news')).flush(articles);
    expect(next).toHaveBeenLastCalledWith(articles);
    expect(console.error).not.toHaveBeenCalled();
  });

  it('retries with a changed key after credentials were rejected', async () => {
    subscriptions.push(service.getMarketNews().subscribe());
    await vi.advanceTimersByTimeAsync(0);
    http.expectOne(req => req.url.endsWith('/news')).flush({}, { status: 401, statusText: 'Unauthorized' });
    environment.finnhubApiKey = 'replacement-test-key';
    await vi.advanceTimersByTimeAsync(60000);
    const retry = http.expectOne(req => req.url.endsWith('/news'));
    expect(retry.request.params.get('token')).toBe('replacement-test-key');
    retry.flush(articles);
  });
});
