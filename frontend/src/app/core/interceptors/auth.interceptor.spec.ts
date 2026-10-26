import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { authInterceptor } from './auth.interceptor';

describe('Platform authorization scope', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting()
    ] });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    localStorage.setItem('accessToken', 'test-platform-token');
  });
  afterEach(() => {
    controller.verify();
    localStorage.removeItem('accessToken');
  });

  it('authenticates platform API calls', () => {
    http.get('/api/accounts/123/balance').subscribe();
    const request = controller.expectOne('/api/accounts/123/balance');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-platform-token');
    request.flush({});
  });

  it('does not send platform credentials to Finnhub or other external origins', () => {
    for (const url of [
      'https://finnhub.io/api/v1/company-news?symbol=ALLE',
      'https://finnhub.io/api/v1/news?category=general',
      'https://example.com/api/accounts', '//example.com/api/accounts', '/auth/login', '/data/us-v1.json'
    ]) {
      http.get(url).subscribe();
      const request = controller.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({});
    }
  });
});
