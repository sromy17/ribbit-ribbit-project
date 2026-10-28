import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="tw:inline-flex tw:size-10 tw:items-center tw:justify-center tw:rounded-lg tw:border tw:border-solid tw:border-line tw:bg-panel tw:p-2 tw:text-ink tw:transition-colors tw:hover:bg-subtle tw:focus-visible:outline-2 tw:focus-visible:outline-offset-2 tw:focus-visible:outline-brand"
      (click)="theme.toggleTheme()" [attr.aria-pressed]="theme.mode() === 'dark'"
      [attr.aria-label]="'Dark mode. Switch to Everforest ' + (theme.mode() === 'dark' ? 'light' : 'dark')"
      [attr.title]="'Switch to Everforest ' + (theme.mode() === 'dark' ? 'light' : 'dark')">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
        @if (theme.mode() === 'dark') {
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2m0 16v2M2 12h2m16 0h2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42" />
        } @else {
          <path d="M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13Z" />
        }
      </svg>
    </button>
  `,
  styles: [':host { display: inline-flex; flex-shrink: 0; }']
})
export class ThemeToggleComponent {
  readonly theme = inject(ThemeService);
}
