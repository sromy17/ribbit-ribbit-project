import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService, User } from '../../../core/services/auth.service';
import { AuthGuard } from '../../../core/guards/auth.guard';
import { AnalystDashboardComponent } from './analyst-dashboard.component';

describe('Frontend-only analyst integration', () => {
  let user: User | null;
  const router = { navigate: vi.fn(), createUrlTree: vi.fn(() => 'login-redirect') };
  const logout = vi.fn();
  let http: HttpTestingController;

  beforeEach(() => {
    user = { id: '1', username: 'analyst@example.com', firstName: 'Test', lastName: 'User', role: 'analyst' };
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { getUser: () => user, logout } },
      { provide: Router, useValue: router }
    ] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('renders the analytics workspace without unsupported requests or zero-shaped metrics', () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Commercial analytics');
    expect(fixture.nativeElement.textContent).toContain('Activity overview');
    expect(fixture.nativeElement.textContent).toContain('Reporting features excluded');
    const values: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.metric-value');
    expect(values).toHaveLength(4);
    expect([...values].every(value => value.textContent === 'Unavailable')).toBe(true);
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    http.expectNone(() => true);
  });

  it('preserves sign-out behavior', () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('header button').click();
    expect(logout).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/employee-login']);
  });

  it.each(['admin', 'analyst'] as const)('keeps the existing %s role gate without granting investor access', role => {
    const route = new ActivatedRouteSnapshot();
    route.data = { role };
    const guard = AuthGuard as CanActivateFn;
    const state = { url: `/${role}-dashboard` } as RouterStateSnapshot;
    user = { ...user!, role };
    expect(TestBed.runInInjectionContext(() => guard(route, state))).toBe(true);
    user = { ...user!, role: 'investor' };
    expect(TestBed.runInInjectionContext(() => guard(route, state))).toBe('login-redirect');
    user = null;
    expect(TestBed.runInInjectionContext(() => guard(route, state))).toBe('login-redirect');
    expect(router.createUrlTree).toHaveBeenCalledWith(['/employee-login']);
  });
});
