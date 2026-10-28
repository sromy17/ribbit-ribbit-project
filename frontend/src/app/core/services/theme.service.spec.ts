import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeService } from './theme.service';

describe('Everforest theme preferences', () => {
  beforeEach(() => {
    localStorage.removeItem('theme');
    document.body.classList.remove('theme-dark');
  });
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem('theme');
    document.body.classList.remove('theme-dark');
    TestBed.resetTestingModule();
  });

  it('preserves dark as the default and restores a saved light preference', () => {
    const service = TestBed.inject(ThemeService);
    service.initializeTheme();
    expect(service.mode()).toBe('dark');
    expect(document.body.classList.contains('theme-dark')).toBe(true);
    localStorage.setItem('theme', 'light');
    service.initializeTheme();
    expect(service.mode()).toBe('light');
    expect(document.body.classList.contains('theme-dark')).toBe(false);
  });

  it('toggles both CSS theme and the persistent preference without losing other body classes', () => {
    const service = TestBed.inject(ThemeService);
    service.initializeTheme();
    document.body.classList.add('test-body');
    service.toggleTheme();
    expect(localStorage.getItem('theme')).toBe('light');
    expect(service.mode()).toBe('light');
    expect(document.body.classList.contains('test-body')).toBe(true);
    service.toggleTheme();
    expect(localStorage.getItem('theme')).toBe('dark');
    expect(document.body.classList.contains('theme-dark')).toBe(true);
    document.body.classList.remove('test-body');
  });

  it('logs blocked storage and keeps a usable in-memory theme', () => {
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new DOMException('Blocked', 'SecurityError'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Blocked', 'SecurityError'); });
    const service = TestBed.inject(ThemeService);
    expect(() => service.initializeTheme()).not.toThrow();
    expect(() => service.toggleTheme()).not.toThrow();
    expect(service.mode()).toBe('light');
    expect(warning).toHaveBeenCalledTimes(2);
  });

  it('does not access browser storage during server rendering', () => {
    TestBed.configureTestingModule({ providers: [{ provide: PLATFORM_ID, useValue: 'server' }] });
    const read = vi.spyOn(Storage.prototype, 'getItem');
    const write = vi.spyOn(Storage.prototype, 'setItem');
    const service = TestBed.inject(ThemeService);
    service.initializeTheme();
    service.toggleTheme();
    expect(read).not.toHaveBeenCalled();
    expect(write).not.toHaveBeenCalled();
  });
});
