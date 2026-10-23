import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  dark = true;
  initializeTheme(): void {
    this.dark = localStorage.getItem('leap-theme') !== 'light';
    this.applyTheme();
  }
  toggleTheme(): void {
    this.dark = !this.dark;
    localStorage.setItem('leap-theme', this.dark ? 'dark' : 'light');
    this.applyTheme();
  }
  private applyTheme(): void {
    document.body.classList.toggle('theme-dark', this.dark);
    document.documentElement.style.colorScheme = this.dark ? 'dark' : 'light';
  }
}
