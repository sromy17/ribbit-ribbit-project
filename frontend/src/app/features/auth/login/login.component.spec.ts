import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService, User } from '../../../core/services/auth.service';
import { LoginComponent } from './login.component';

describe('Investor and employee login', () => {
  afterEach(() => TestBed.resetTestingModule());

  function createLogin(employee: boolean, role: User['role'] = 'investor') {
    const user: User = { id: '1', username: 'test', firstName: '', lastName: '', role };
    const auth = { login: vi.fn(() => of(user)) };
    const router = { navigate: vi.fn() };
    TestBed.configureTestingModule({ providers: [
      { provide: AuthService, useValue: auth },
      { provide: Router, useValue: router },
      { provide: ActivatedRoute, useValue: { snapshot: { data: { employeeLogin: employee } } } }
    ] });
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, auth, router };
  }

  it('uses the existing styled form but offers no employee registration', () => {
    const { fixture } = createLogin(true);
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Employee sign in');
    expect(text).toContain('provisioned by your administrator');
    expect(text).not.toContain('Create a user profile');
    expect(fixture.nativeElement.querySelector('.login-box')).not.toBeNull();
  });

  it('keeps investor registration and a link to employee sign in', () => {
    const { fixture } = createLogin(false);
    expect(fixture.nativeElement.textContent).toContain('Create a user profile');
    expect(fixture.nativeElement.textContent).toContain('Employee sign in');
  });

  it.each(['admin', 'analyst', 'investor'] as const)('routes a signed-in %s to its own dashboard', role => {
    const { component, auth, router } = createLogin(role !== 'investor', role);
    component.username = '  test  ';
    component.password = 'test-password';
    component.login();
    expect(auth.login).toHaveBeenCalledWith('test', 'test-password', role !== 'investor');
    expect(router.navigate).toHaveBeenCalledWith([
      role === 'investor' ? '/accounts' : `/${role}-dashboard`
    ]);
  });

  it('requires credentials and shows server rejection without navigating', () => {
    const { component, auth, router } = createLogin(true);
    component.login();
    expect(auth.login).not.toHaveBeenCalled();
    expect(component.error).toContain('required');
    component.username = 'test';
    component.password = 'wrong';
    auth.login.mockReturnValue(throwError(() => ({ error: { message: 'Invalid username or password' } })));
    component.login();
    expect(component.error).toBe('Invalid username or password');
    expect(component.loading).toBe(false);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
