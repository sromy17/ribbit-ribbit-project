import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, Observable, concat, defer, of, timer } from 'rxjs';
import { catchError, map, shareReplay, switchMap, tap, timeout } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface FinnhubNewsArticle {
  category?: string;
  datetime: number;
  headline: string;
  id: number;
  image?: string;
  related?: string;
  source: string;
  summary: string;
  url: string;
}

export type FinnhubNewsCategory = 'general' | 'technology' | 'healthcare' | 'finance' | 'energy';

@Injectable({ providedIn: 'root' })
export class FinnhubNewsService {
  public static readonly REFRESH_INTERVAL_MS = 60000;
  private readonly baseUrl = '/api/market';
  readonly availability$ = new BehaviorSubject<'loading' | 'available' | 'unavailable'>('loading');
  private readonly categoryCache = new Map<string, Observable<FinnhubNewsArticle[]>>();
  private readonly companyCache = new Map<string, Observable<FinnhubNewsArticle[]>>();
  private readonly requestTimeoutMs = 4000;
  private readonly storagePrefix = 'leap-trader:finnhub:';

  constructor(private http: HttpClient) {}

  getMarketNews(category: FinnhubNewsCategory = 'general'): Observable<FinnhubNewsArticle[]> {
    const cachedRequest = this.categoryCache.get('general');
    if (cachedRequest) {
      return cachedRequest.pipe(map(articles => filterBySector(articles, category)));
    }

    const request = this.createPollingRequest(
      'news:general',
      () => this.fetchNews('news', new HttpParams().set('category', 'general'), 'general'),
      'general'
    );
    this.categoryCache.set('general', request);
    return request.pipe(map(articles => filterBySector(articles, category)));
  }

  getCompanyNews(symbol: string, from: string, to: string): Observable<FinnhubNewsArticle[]> {
    const normalizedSymbol = symbol.trim().toUpperCase();
    const cacheKey = `${normalizedSymbol}:${from}:${to}`;
    const cachedRequest = this.companyCache.get(cacheKey);
    if (cachedRequest) {
      return cachedRequest;
    }

    const request = this.createPollingRequest(
      `company:${cacheKey}`,
      () => this.fetchNews('company-news', new HttpParams()
          .set('symbol', normalizedSymbol)
          .set('from', from)
          .set('to', to), normalizedSymbol),
      normalizedSymbol
    );
    this.companyCache.set(cacheKey, request);
    return request;
  }

  private fetchNews(
    endpoint: 'news' | 'company-news',
    params: HttpParams,
    fallbackKey: string
  ): Observable<FinnhubNewsArticle[]> {
    return this.http.get<unknown>(`${this.baseUrl}/${endpoint}`, {params}).pipe(
      timeout(this.requestTimeoutMs + 1500),
      mapNewsResponse(),
      tap(() => this.availability$.next('available')),
      catchError(() => { this.availability$.next('unavailable'); return of([]); })
    );
  }

  private createPollingRequest(
    storageKey: string,
    fetcher: () => Observable<FinnhubNewsArticle[]>,
    fallbackKey: string
  ): Observable<FinnhubNewsArticle[]> {
    const refresh = timer(0, Math.max(
      environment.finnhubNewsRefreshMs,
      FinnhubNewsService.REFRESH_INTERVAL_MS
    )).pipe(
      switchMap(() => defer(fetcher)),
      map((articles) => {
        this.writeCache(storageKey, articles);
        return articles;
      })
    );
    return defer(() => {
      const stored = this.readCache(storageKey);
      const cached = stored?.every(article => article.id > 0) ? stored : null;
      return concat(
        cached ? of(cached) : of([]),
        refresh
      );
    }).pipe(shareReplay({ bufferSize: 1, refCount: true }));
  }

  private readCache(key: string): FinnhubNewsArticle[] | null {
    try {
      const raw = localStorage.getItem(`${this.storagePrefix}${key}`);
      if (!raw) return null;
      const parsed: unknown = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed as FinnhubNewsArticle[] : null;
    } catch {
      return null;
    }
  }

  private writeCache(key: string, articles: FinnhubNewsArticle[]): void {
    try {
      localStorage.setItem(`${this.storagePrefix}${key}`, JSON.stringify(articles));
    } catch {
      // Storage is an optional optimization; network responses remain authoritative.
    }
  }

}

function filterBySector(articles: FinnhubNewsArticle[], category: FinnhubNewsCategory): FinnhubNewsArticle[] {
  if (category === 'general') return articles;
  const terms: Record<Exclude<FinnhubNewsCategory, 'general'>, RegExp> = {
    technology: /\b(apple|nvidia|microsoft|tech(?:nology)?|ai|software|semis?|chips?|cloud|semiconductors?)\b/i,
    healthcare: /\b(health(?:care)?|pharma(?:ceutical)?s?|fda|biotech|eli lilly|pfizer|pfe|lilly)\b/i,
    finance: /\b(banks?|banking|fed(?:eral reserve)?|interest rates?|wall street|jpmorgan|yields?|credit|dollar|euro)\b/i,
    energy: /\b(energy|oil|crude|exxon|chevron|grid|gas|industrial)\b/i
  };
  const matcher = terms[category as Exclude<FinnhubNewsCategory, 'general'>];
  return matcher ? articles.filter((article) => matcher.test(`${article.headline} ${article.summary}`)) : articles;
}

function mapNewsResponse(category: FinnhubNewsCategory = 'general') {
  return (source: Observable<unknown>): Observable<FinnhubNewsArticle[]> =>
    source.pipe(
      map((response) => {
        if (!Array.isArray(response)) {
          throw new Error('Finnhub returned an invalid news response.');
        }
        const articles = response.filter((item): item is FinnhubNewsArticle =>
          typeof item === 'object' && item !== null &&
          typeof (item as FinnhubNewsArticle).headline === 'string' &&
          typeof (item as FinnhubNewsArticle).url === 'string' &&
          Number.isFinite((item as FinnhubNewsArticle).datetime)
        );
        return filterBySector(articles, category);
      })
    );
}
