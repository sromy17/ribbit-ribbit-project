import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';
import { authInterceptor } from '../interceptors/auth.interceptor';
describe('Merged authentication connection', () => {
  let service: AuthService;
  let http: HttpTestingController;
  const response = {token: 'token', refreshToken: 'refresh', expiresInSeconds: 3600, userId: 7, username: 'staff', firstName: 'Test', lastName: 'User'};
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()]});
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    service.logout();
    http.match(r => r.url.endsWith('/logout')).forEach(r => r.flush({}));
    http.verify();
    localStorage.clear();
  });
  it('loads server permissions before admitting staff', () => {
    service.login('staff', 'password', true).subscribe(user => expect(user.role).toBe('admin'));
    http.expectOne('/api/login').flush(response);
    expect(service.getUser()).toBeNull();
    const session = http.expectOne('/api/admin/session');
    expect(session.request.headers.get('Authorization')).toBe('Bearer token');
    session.flush({permissions: ['PERM_ACCOUNT_READ']});
    expect(Number(localStorage.getItem('sessionExpiresAt'))).toBeGreaterThan(Date.now());
  });
  it('routes reporting staff to analytics and refreshes through the backend', () => {
    service.login('staff', 'password', true).subscribe(user => expect(user.role).toBe('analyst'));
    http.expectOne('/api/login').flush(response);
    http.expectOne('/api/admin/session').flush({permissions: ['PERM_REPORTING']});
    service.refreshSession().subscribe();
    const refresh = http.expectOne('/api/refresh');
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush({...response, token: 'rotated'});
    expect(localStorage.getItem('accessToken')).toBe('rotated');
  });
  it('keeps customer authentication and logout on the stub', () => {
    service.login('customer', 'password').subscribe(user => expect(user.role).toBe('investor'));
    http.expectOne('/auth/login').flush(response);
    service.logout();
    const logout = http.expectOne('/auth/logout');
    expect(logout.request.body.accessToken).toBe('token');
    logout.flush({});
    expect(service.isLoggedIn()).toBe(false);
  });
  it('rejects staff without server permissions and clears tokens', () => {
    let rejected = false;
    service.login('staff', 'password', true).subscribe({error: () => rejected = true});
    http.expectOne('/api/login').flush(response);
    http.expectOne('/api/admin/session').flush({permissions: []});
    http.expectOne('/api/logout').flush({});
    expect(rejected).toBe(true);
    expect(localStorage.getItem('accessToken')).toBeNull();
  });
});
