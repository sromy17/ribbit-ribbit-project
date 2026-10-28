import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly current = signal<'light' | 'dark'>('dark');
  readonly mode = this.current.asReadonly();

  initializeTheme(): void {
    if (!this.browser) return;
    let saved: string | null = null;
    try {
      saved = this.document.defaultView?.localStorage.getItem('theme') ?? null;
    } catch (error) {
      console.warn('Theme preference could not be read; using Everforest dark.', error);
    }
    this.apply(saved === 'light' ? 'light' : 'dark');
  }

  toggleTheme(): void {
    const mode = this.current() === 'dark' ? 'light' : 'dark';
    this.apply(mode);
    if (this.browser) {
      try {
        this.document.defaultView?.localStorage.setItem('theme', mode);
      } catch (error) {
        console.warn('Theme preference could not be saved; it applies to this page only.', error);
      }
    }
  }

  private apply(mode: 'light' | 'dark'): void {
    this.current.set(mode);
    this.document.body.classList.toggle('theme-dark', mode === 'dark');
  }
}
