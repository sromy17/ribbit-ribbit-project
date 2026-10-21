import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  initializeTheme(): void {
    document.body.classList.add('theme-dark');
  }
}
