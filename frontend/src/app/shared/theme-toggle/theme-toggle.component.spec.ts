import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { ThemeToggleComponent } from './theme-toggle.component';
import { ThemeService } from '../../core/services/theme.service';
import { UiWorkspaceComponent } from '../ui';

describe('App-wide theme toggle', () => {
  afterEach(() => {
    localStorage.removeItem('theme');
    document.body.classList.remove('theme-dark');
    TestBed.resetTestingModule();
  });

  it('exposes an accessible dark-mode toggle and keeps multiple controls synchronized', async () => {
    localStorage.removeItem('theme');
    TestBed.inject(ThemeService).initializeTheme();
    const first = TestBed.createComponent(ThemeToggleComponent);
    const second = TestBed.createComponent(ThemeToggleComponent);
    first.detectChanges();
    second.detectChanges();
    const button: HTMLButtonElement = first.nativeElement.querySelector('button');
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.getAttribute('aria-label')).toContain('Switch to Everforest light');
    expect(button.classList.contains('tw:bg-panel')).toBe(true);
    expect(button.textContent?.trim()).toBe('');
    expect(button.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    expect(button.querySelector('circle')).not.toBeNull();
    expect(button.getAttribute('title')).toBe('Switch to Everforest light');
    button.click();
    await first.whenStable();
    await second.whenStable();
    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(second.nativeElement.querySelector('button').getAttribute('aria-label')).toContain('Switch to Everforest dark');
    expect(button.querySelector('circle')).toBeNull();
    expect(button.getAttribute('title')).toBe('Switch to Everforest dark');
    expect(localStorage.getItem('theme')).toBe('light');
    button.click();
    await first.whenStable();
    expect(button.querySelector('circle')).not.toBeNull();
    expect(button.getAttribute('aria-pressed')).toBe('true');
  });

  it('places the staff theme icon before account information without changing sign out', () => {
    const fixture = TestBed.createComponent(UiWorkspaceComponent);
    fixture.componentRef.setInput('username', 'staff_user');
    let signedOut = false;
    fixture.componentInstance.logout.subscribe(() => { signedOut = true; });
    fixture.detectChanges();
    const actions: HTMLElement = fixture.nativeElement.querySelector('.actions');
    expect(actions.children[0].tagName).toBe('APP-THEME-TOGGLE');
    expect(actions.children[1].textContent).toBe('staff_user');
    const signOut = actions.children[2] as HTMLButtonElement;
    expect(signOut.textContent).toBe('Sign out');
    signOut.click();
    expect(signedOut).toBe(true);
  });
});
