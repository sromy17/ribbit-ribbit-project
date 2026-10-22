import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay, timeout } from 'rxjs/operators';
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

export type FinnhubNewsCategory = 'general' | 'technology' | 'merger' | 'crypto';

const FALLBACK_NEWS: FinnhubNewsArticle[] = [
  {
    category: 'general',
    datetime: Math.floor(Date.now() / 1000) - 15 * 60,
    headline: 'Markets monitor earnings, rates, and fresh economic signals',
    id: -1,
    image: '',
    related: 'SPY,QQQ',
    source: 'Market Desk',
    summary: 'Investors are weighing corporate earnings and macroeconomic data as they assess the next major market catalyst.',
    url: 'https://finnhub.io'
  },
  {
    category: 'general',
    datetime: Math.floor(Date.now() / 1000) - 42 * 60,
    headline: 'Technology shares remain in focus as investors assess growth outlooks',
    id: -2,
    image: '',
    related: 'AAPL,MSFT,NVDA',
    source: 'Market Desk',
    summary: 'Large-cap technology names remain closely watched as traders look for durable demand and resilient margins.',
    url: 'https://finnhub.io'
  },
  {
    category: 'general',
    datetime: Math.floor(Date.now() / 1000) - 90 * 60,
    headline: 'Risk assets trade cautiously ahead of upcoming policy updates',
    id: -3,
    image: '',
    related: 'SPY,TSLA',
    source: 'Market Desk',
    summary: 'Cross-asset positioning remains measured while markets await additional guidance from policymakers and company executives.',
    url: 'https://finnhub.io'
  }
];

@Injectable({ providedIn: 'root' })
export class FinnhubNewsService {
  private readonly baseUrl = 'https://finnhub.io/api/v1';
  private readonly categoryCache = new Map<string, { loadedAt: number; request: Observable<FinnhubNewsArticle[]> }>();
  private readonly companyCache = new Map<string, { loadedAt: number; request: Observable<FinnhubNewsArticle[]> }>();
  private readonly requestTimeoutMs = 4000;

  constructor(private http: HttpClient) {}

  getMarketNews(category: FinnhubNewsCategory = 'general'): Observable<FinnhubNewsArticle[]> {
    if (!environment.finnhubApiKey) {
      return of(this.getFallbackNews(category));
    }
    const cached = this.categoryCache.get(category);
    if (cached && Date.now() - cached.loadedAt < environment.finnhubNewsRefreshMs) {
      return cached.request;
    }

    const params = new HttpParams()
      .set('category', category)
      .set('token', environment.finnhubApiKey);
    const request = this.http.get<unknown>(`${this.baseUrl}/news`, { params }).pipe(
      timeout(this.requestTimeoutMs),
      mapNewsResponse(),
      catchError((error) => {
        console.warn(`Finnhub ${category} news unavailable; using local fallback.`, error);
        return of(this.getFallbackNews(category));
      }),
      shareReplay({ bufferSize: 1, refCount: true })
    );
    this.categoryCache.set(category, { loadedAt: Date.now(), request });
    return request;
  }

  getCompanyNews(symbol: string, from: string, to: string): Observable<FinnhubNewsArticle[]> {
    if (!environment.finnhubApiKey) {
      return of(this.getFallbackNews(symbol.trim().toUpperCase()));
    }
    const normalizedSymbol = symbol.trim().toUpperCase();
    const cacheKey = `${normalizedSymbol}:${from}:${to}`;
    const cached = this.companyCache.get(cacheKey);
    if (cached && Date.now() - cached.loadedAt < environment.finnhubNewsRefreshMs) {
      return cached.request;
    }

    const params = new HttpParams()
      .set('symbol', normalizedSymbol)
      .set('from', from)
      .set('to', to)
      .set('token', environment.finnhubApiKey);
    const request = this.http.get<unknown>(`${this.baseUrl}/company-news`, { params }).pipe(
      timeout(this.requestTimeoutMs),
      mapNewsResponse(),
      catchError((error) => {
        console.warn(`Finnhub company news unavailable for ${normalizedSymbol}; using local fallback.`, error);
        return of(this.getFallbackNews(normalizedSymbol));
      }),
      shareReplay({ bufferSize: 1, refCount: true })
    );
    this.companyCache.set(cacheKey, { loadedAt: Date.now(), request });
    return request;
  }

  private getFallbackNews(categoryOrSymbol: string): FinnhubNewsArticle[] {
    const isSymbol = /^[A-Z]{1,5}$/.test(categoryOrSymbol);
    return FALLBACK_NEWS.map((article, index) => ({
      ...article,
      id: article.id - index,
      category: isSymbol ? 'company' : categoryOrSymbol,
      related: isSymbol ? categoryOrSymbol : article.related
    }));
  }
}

function mapNewsResponse() {
  return (source: Observable<unknown>): Observable<FinnhubNewsArticle[]> =>
    source.pipe(
      map((response) => {
        if (!Array.isArray(response)) {
          throw new Error('Finnhub returned an invalid news response.');
        }
        return response.filter((item): item is FinnhubNewsArticle =>
          typeof item === 'object' && item !== null &&
          typeof (item as FinnhubNewsArticle).headline === 'string' &&
          typeof (item as FinnhubNewsArticle).url === 'string' &&
          Number.isFinite((item as FinnhubNewsArticle).datetime)
        );
      })
    );
}
