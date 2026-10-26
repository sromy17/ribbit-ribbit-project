import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, concat, defer, of, timer } from 'rxjs';
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

const FALLBACK_NEWS: FinnhubNewsArticle[] = [
  ['Earnings momentum meets renewed rate uncertainty', 'SPY,QQQ', 'Reuters', 'general'],
  ['Semiconductor demand keeps chip leaders in focus', 'NVDA,AMD,TSM', 'Bloomberg', 'technology'],
  ['Dollar steadies ahead of central-bank guidance', 'UUP,EURUSD', 'WSJ', 'finance'],
  ['Healthcare deal pipeline draws investor attention', 'LLY,MRK,ABBV', 'Reuters', 'healthcare'],
  ['Cloud spending remains a key enterprise signal', 'MSFT,CRM,ORCL', 'Bloomberg', 'technology'],
  ['Federal Reserve policy keeps bank shares in focus', 'JPM,BAC,GS', 'CNBC', 'finance'],
  ['Retailers report an uneven consumer demand picture', 'AMZN,WMT,COST', 'WSJ', 'general'],
  ['Auto shares react to delivery and margin outlooks', 'TSLA,GM,F', 'Reuters', 'general'],
  ['Industrial energy demand points to a busy infrastructure cycle', 'GE,MMM,CAT', 'Bloomberg', 'energy'],
  ['Banks prepare for a close read on credit quality', 'JPM,BAC,GS', 'MarketWatch', 'general'],
  ['Pharma pipelines and FDA decisions shape healthcare sentiment', 'PFE,LLY,MRNA', 'Reuters', 'healthcare'],
  ['Energy markets track inventories ahead of winter demand', 'XOM,CVX,USO', 'Financial Times', 'general']
].map(([headline, related, source, category], index) => ({
  category,
  datetime: Math.floor(Date.now() / 1000) - (15 + index * 43) * 60,
  headline,
  id: -index - 1,
  image: '',
  related,
  source,
  summary: `Market participants are monitoring ${headline.toLowerCase()} as the next catalyst for ${related} and the broader trading session.`,
  url: `https://www.google.com/search?q=${encodeURIComponent(headline)}`
}));

@Injectable({ providedIn: 'root' })
export class FinnhubNewsService {
  public static readonly REFRESH_INTERVAL_MS = 60000;
  private readonly baseUrl = 'https://finnhub.io/api/v1';
  private readonly categoryCache = new Map<string, Observable<FinnhubNewsArticle[]>>();
  private readonly companyCache = new Map<string, Observable<FinnhubNewsArticle[]>>();
  private readonly requestTimeoutMs = 4000;
  private readonly storagePrefix = 'leap-trader:finnhub:';
  private rejectedApiKey?: string;
  private fallbackStatusLogged = false;

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
    const apiKey = environment.finnhubApiKey?.trim();
    if (!apiKey || apiKey === this.rejectedApiKey) return this.useFallback(fallbackKey);
    return this.http.get<unknown>(`${this.baseUrl}/${endpoint}`, {
      params: params.set('token', apiKey)
    }).pipe(
      timeout(this.requestTimeoutMs),
      mapNewsResponse(),
      tap(() => { this.fallbackStatusLogged = false; }),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
          this.rejectedApiKey = apiKey;
        }
        return this.useFallback(fallbackKey);
      })
    );
  }

  private useFallback(key: string): Observable<FinnhubNewsArticle[]> {
    if (!this.fallbackStatusLogged) {
      console.info('Using curated market intelligence stream (offline/fallback mode)');
      this.fallbackStatusLogged = true;
    }
    return of(this.getFallbackNews(key));
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
        cached ? of(cached) : of(this.getFallbackNews(fallbackKey)),
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

  private getFallbackNews(categoryOrSymbol: string): FinnhubNewsArticle[] {
    const isSymbol = /^[A-Z]{1,5}$/.test(categoryOrSymbol);
    const fallback = FALLBACK_NEWS.map((article, index) => ({
      ...article,
      source: 'Curated sample',
      id: article.id - index,
      category: isSymbol ? 'company' : categoryOrSymbol,
      related: isSymbol ? categoryOrSymbol : article.related
    }));
    return filterBySector(fallback, categoryOrSymbol as FinnhubNewsCategory);
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
