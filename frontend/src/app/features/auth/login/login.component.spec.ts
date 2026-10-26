import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { LoginComponent } from './login.component';

describe('Separate user and staff sign-in', () => {
  let auth: {login: ReturnType<typeof vi.fn>};
  let router: {navigate: ReturnType<typeof vi.fn>};
  beforeEach(() => {
    auth = {login: vi.fn(() => of({role: 'analyst'}))};
    router = {navigate: vi.fn()};
    TestBed.configureTestingModule({imports: [LoginComponent], providers: [{provide: AuthService, useValue: auth}, {provide: Router, useValue: router}]});
  });
  it('starts with two choices and no credentials form or toggle', () => {
    const f = TestBed.createComponent(LoginComponent); f.detectChanges();
    expect(f.nativeElement.textContent).toContain('User sign-in');
    expect(f.nativeElement.textContent).toContain('Staff sign-in');
    expect(f.nativeElement.querySelector('form')).toBeNull();
    expect(f.nativeElement.querySelector('input[type=checkbox]')).toBeNull();
  });
  it('uses staff auth and the server-assigned workspace after choosing staff', () => {
    const f = TestBed.createComponent(LoginComponent); f.detectChanges();
    f.nativeElement.querySelectorAll('section button')[1].click(); f.detectChanges();
    f.componentInstance.username = 'priya'; f.componentInstance.password = 'test-password';
    f.componentInstance.login();
    expect(auth.login).toHaveBeenCalledWith('priya', 'test-password', true);
    expect(router.navigate).toHaveBeenCalledWith(['/analyst-dashboard']);
    expect(f.nativeElement.textContent).not.toContain('Create an account');
  });
  it('clears credentials when switching paths and uses customer auth for users', () => {
    const f = TestBed.createComponent(LoginComponent); f.detectChanges();
    f.nativeElement.querySelectorAll('section button')[1].click(); f.detectChanges();
    f.componentInstance.password = 'old-password'; f.componentInstance.error = 'Old error';
    f.nativeElement.querySelector('section button').click(); f.detectChanges();
    f.nativeElement.querySelectorAll('section button')[0].click(); f.detectChanges();
    expect(f.componentInstance.password).toBe(''); expect(f.componentInstance.error).toBe('');
    expect(f.nativeElement.textContent).toContain('Create an account');
    auth.login.mockReturnValue(of({role: 'investor'}));
    f.componentInstance.username = 'joanna'; f.componentInstance.password = 'test-password'; f.componentInstance.login();
    expect(auth.login).toHaveBeenCalledWith('joanna', 'test-password', false);
    expect(router.navigate).toHaveBeenCalledWith(['/accounts']);
  });
});
