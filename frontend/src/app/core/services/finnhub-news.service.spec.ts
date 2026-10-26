import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subscription } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FinnhubNewsService } from './finnhub-news.service';
const articles = [{id:1,headline:'Nvidia semiconductor demand',datetime:1700000000,source:'Test publisher',summary:'',url:'https://example.com/one'}];
describe('Backend news connection',()=>{
 let service: FinnhubNewsService; let http: HttpTestingController; let subscriptions: Subscription[];
 beforeEach(()=>{
  vi.useFakeTimers(); localStorage.clear(); subscriptions=[];
  TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});
  service=TestBed.inject(FinnhubNewsService); http=TestBed.inject(HttpTestingController);
 });
 afterEach(()=>{subscriptions.forEach(s=>s.unsubscribe());http.verify();localStorage.clear();vi.useRealTimers();});
 it('shares one authenticated backend request across sector tabs without a browser key',async()=>{
  subscriptions.push(service.getMarketNews().subscribe(),service.getMarketNews('technology').subscribe());
  await vi.advanceTimersByTimeAsync(0);
  const request=http.expectOne(r=>r.url==='/api/market/news');
  expect(request.request.params.has('token')).toBe(false);request.flush(articles);
  expect(service.availability$.value).toBe('available');
 });
 it('shows unavailable instead of manufactured headlines and recovers on the next poll',async()=>{
  const received=vi.fn();subscriptions.push(service.getMarketNews().subscribe(received));
  await vi.advanceTimersByTimeAsync(0);
  http.expectOne(r=>r.url==='/api/market/news').flush({}, {status:503,statusText:'Unavailable'});
  expect(service.availability$.value).toBe('unavailable');expect(received).toHaveBeenLastCalledWith([]);
  await vi.advanceTimersByTimeAsync(60000);
  http.expectOne(r=>r.url==='/api/market/news').flush(articles);
  expect(service.availability$.value).toBe('available');expect(received).toHaveBeenLastCalledWith(articles);
 });
 it('routes company symbols and dates to the backend',async()=>{
  subscriptions.push(service.getCompanyNews('aapl','2026-10-01','2026-10-22').subscribe());
  await vi.advanceTimersByTimeAsync(0);
  const request=http.expectOne(r=>r.url==='/api/market/company-news');
  expect(request.request.params.get('symbol')).toBe('AAPL');request.flush([]);
 });
});
