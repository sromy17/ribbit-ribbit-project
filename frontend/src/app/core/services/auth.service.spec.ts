import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';

describe('Database-backed employee authentication', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  const response = {
    token: 'test-token', tokenType: 'Bearer', expiresInSeconds: 1200, refreshToken: 'test-refresh',
    userId: 1, username: 'admin_user', firstName: '', lastName: ''
  };
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    if (auth.getUser()) {
      const employee = auth.getUser()?.role !== 'investor';
      auth.logout();
      if (employee) { http.expectOne('/auth/employee/logout').flush(null); }
    }
    http.verify();
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('preserves the investor endpoint and never grants a staff role from investor login', () => {
    auth.login('admin_user', 'password').subscribe();
    http.expectOne('/auth/login').flush({ ...response, role: 'admin' });
    expect(auth.getUser()?.role).toBe('investor');
  });

  it.each(['admin', 'analyst'] as const)('uses and persists the database-issued %s role', role => {
    auth.login('staff', 'password', true).subscribe();
    const request = http.expectOne('/auth/employee/login');
    expect(request.request.body).toEqual({ username: 'staff', password: 'password' });
    request.flush({ ...response, role });
    expect(auth.getUser()?.role).toBe(role);
    expect(JSON.parse(localStorage.getItem('currentUser')!).role).toBe(role);
    expect(localStorage.getItem('accessToken')).toBe('test-token');
  });

  it('rejects missing or unsupported employee roles before saving tokens', () => {
    const error = vi.fn();
    auth.login('staff', 'password', true).subscribe({ error });
    http.expectOne('/auth/employee/login').flush(response);
    expect(error).toHaveBeenCalled();
    expect(auth.getUser()).toBeNull();
    expect(localStorage.getItem('accessToken')).toBeNull();
  });

  it('refreshes employee sessions through the employee endpoint', () => {
    auth.login('staff', 'password', true).subscribe();
    http.expectOne('/auth/employee/login').flush({ ...response, role: 'admin' });
    auth.refreshSession().subscribe();
    const refresh = http.expectOne('/auth/employee/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'test-refresh' });
    refresh.flush({ ...response, token: 'replacement-token', role: 'admin' });
    expect(auth.getUser()?.role).toBe('admin');
    expect(localStorage.getItem('accessToken')).toBe('replacement-token');
  });

  it('keeps investor refresh and registration separate from employees', () => {
    auth.login('investor', 'password').subscribe();
    http.expectOne('/auth/login').flush(response);
    auth.refreshSession().subscribe();
    http.expectOne('/auth/refresh').flush(response);
    auth.register({ username: 'new@example.com', password: 'password', firstName: 'New', lastName: 'User' }).subscribe();
    http.expectOne('/auth/register').flush({ userId: 2 });
  });

  it('revokes employee tokens on logout and clears the browser session', () => {
    auth.login('staff', 'password', true).subscribe();
    http.expectOne('/auth/employee/login').flush({ ...response, role: 'admin' });
    auth.logout();
    const logout = http.expectOne('/auth/employee/logout');
    expect(logout.request.body).toEqual({ accessToken: 'test-token' });
    logout.flush(null);
    expect(auth.getUser()).toBeNull();
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(auth.loginPath).toBe('/employee-login');
  });

  it('ends a session if refresh returns a different employee role', () => {
    auth.login('staff', 'password', true).subscribe();
    http.expectOne('/auth/employee/login').flush({ ...response, role: 'admin' });
    const error = vi.fn();
    auth.refreshSession().subscribe({ error });
    http.expectOne('/auth/employee/refresh').flush({ ...response, token: 'new-role-token', role: 'analyst' });
    const logout = http.expectOne('/auth/employee/logout');
    expect(logout.request.body).toEqual({ accessToken: 'new-role-token' });
    logout.flush(null);
    expect(error).toHaveBeenCalled();
    expect(auth.getUser()).toBeNull();
    expect(auth.loginPath).toBe('/employee-login');
  });
});
