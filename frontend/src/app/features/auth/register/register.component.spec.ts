import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { RegisterComponent } from './register.component';

describe('Registration email username', () => {
  afterEach(() => TestBed.resetTestingModule());

  function createRegistration() {
    const auth = { register: vi.fn(() => of({})) };
    const router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router }
      ]
    });
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    component.firstName = 'Test';
    component.lastName = 'User';
    component.password = 'long-password-123';
    return { fixture, component, auth, router };
  }

  it.each(['plainusername', 'user@', '@example.com', 'user name@example.com', 'user@@example.com'])(
    'rejects invalid email username %s before calling the API', username => {
      const { component, auth } = createRegistration();
      component.username = username;
      component.register();
      expect(auth.register).not.toHaveBeenCalled();
      expect(component.error).toBe('Username must be a valid email address');
      expect(component.loading).toBe(false);
    }
  );

  it('accepts and trims a valid email username', () => {
    const { component, auth, router } = createRegistration();
    component.username = '  test.user+trading@example.com  ';
    component.register();
    expect(auth.register).toHaveBeenCalledWith(expect.objectContaining({
      username: 'test.user+trading@example.com'
    }));
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('shows an inline error for an invalid email after leaving the input', async () => {
    const { fixture } = createRegistration();
    fixture.detectChanges();
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('[name="username"]');
    input.value = 'not-an-email';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Username must be a valid email address');
  });
});
