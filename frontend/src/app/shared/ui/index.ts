import { Component, Directive, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ThemeService } from '../../core/services/theme.service';

@Directive({
  selector: 'button[uiButton], a[uiButton]',
  standalone: true,
  host: { class: 'ui-button', '[attr.data-variant]': 'uiButton' },
})
export class UiButtonDirective {
  @Input() uiButton: 'primary' | 'secondary' | 'ghost' | 'danger' | '' = 'secondary';
}

@Directive({
  selector: 'input[uiInput], select[uiInput], textarea[uiInput]',
  standalone: true,
  host: { class: 'ui-input' },
})
export class UiInputDirective {}

@Component({
  selector: 'app-brand',
  standalone: true,
  template: ` <span class="inline-flex items-center gap-3" aria-label="Leap Trader Pro">
    <span class="brand-symbol"
      ><svg viewBox="0 0 28 28" width="26" height="26" fill="none" aria-hidden="true">
        <path
          d="M7 6v16h15M11 16l5-5 4 3 5-8M20 6h5v5"
          stroke="currentColor"
          stroke-width="2.3"
          stroke-linecap="round"
          stroke-linejoin="round"
        /></svg
    ></span>
    <span class="brand-copy"
      >Leap<span class="brand-product">Trader <span class="brand-pro">PRO</span></span></span
    >
  </span>`,
})
export class UiBrandComponent {}

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: ` <span class="ui-badge" [attr.data-tone]="tone"
    ><span class="badge-dot" aria-hidden="true"></span>{{ label }}</span
  >`,
})
export class UiStatusBadgeComponent {
  @Input() status: string | null | undefined;
  get label() {
    return (this.status || 'Unknown').replaceAll('_', ' ');
  }
  get tone() {
    if (/^(EXECUTED|FILLED|RESOLVED|SUCCEEDED|BUY|CONNECTED)$/i.test(this.status || ''))
      return 'success';
    if (/^(REJECTED|FAILED|SELL|UNAVAILABLE)$/i.test(this.status || '')) return 'danger';
    if (/^(OPEN|PENDING|CLAIMED|RUNNING|IN_PROGRESS)$/i.test(this.status || '')) return 'warning';
    return 'neutral';
  }
}

@Component({
  selector: 'app-page-heading',
  standalone: true,
  template: ` <div class="page-heading">
    <div>
      <p class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p *ngIf="description" class="description">{{ description }}</p>
    </div>
    <div class="heading-actions"><ng-content /></div>
  </div>`,
  imports: [CommonModule],
})
export class UiPageHeadingComponent {
  @Input() eyebrow = 'Workspace';
  @Input() title = '';
  @Input() description = '';
}

@Component({
  selector: 'app-empty-state',
  standalone: true,
  template: ` <div class="ui-empty-state">
    <span class="empty-icon" aria-hidden="true"
      ><svg
        width="24"
        height="24"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
      >
        <path d="M4 4h16v16H4zM8 9h8M8 13h5" /></svg
    ></span>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <ng-content />
  </div>`,
})
export class UiEmptyStateComponent {
  @Input() title = 'Nothing here yet';
  @Input() description = '';
}

@Component({
  selector: 'app-workspace',
  standalone: true,
  imports: [CommonModule, UiBrandComponent, UiButtonDirective],
  template: ` <a
      href="#workspace-content"
      class="skip-link"
      (click)="$event.preventDefault(); content.focus()"
      >Skip to workspace</a
    >
    <header class="workspace-header">
      <div class="workspace-header-inner">
        <app-brand />
        <div class="workspace-context">
          <span class="context-divider"></span><span>{{ area }}</span>
        </div>
        <div class="workspace-header-actions">
          <ng-content select="[workspaceActions]" /><button
            uiButton="ghost"
            type="button"
            (click)="theme.toggleTheme()"
            [attr.aria-label]="theme.dark ? 'Switch to light theme' : 'Switch to dark theme'"
            title="Change theme"
          >
            <svg
              *ngIf="theme.dark"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              aria-hidden="true"
            >
              <circle cx="12" cy="12" r="4" />
              <path
                d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"
              /></svg
            ><svg
              *ngIf="!theme.dark"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              aria-hidden="true"
            >
              <path d="M20.5 14.5A9 9 0 019.5 3.5a9 9 0 1011 11z" />
            </svg></button
          ><span class="user-avatar" aria-hidden="true">{{
            (username || 'U').slice(0, 1).toUpperCase()
          }}</span
          ><span class="workspace-username">{{ username }}</span
          ><button uiButton="ghost" type="button" (click)="logout.emit()">Sign out</button>
        </div>
      </div>
    </header>
    <main #content id="workspace-content" class="workspace-content" tabindex="-1">
      <ng-content />
    </main>`,
})
export class UiWorkspaceComponent {
  @Input() username = '';
  @Input() area = 'Workspace';
  @Output() logout = new EventEmitter<void>();
  readonly theme = inject(ThemeService);
}

export const UI_COMPONENTS = [
  UiButtonDirective,
  UiInputDirective,
  UiBrandComponent,
  UiStatusBadgeComponent,
  UiPageHeadingComponent,
  UiEmptyStateComponent,
  UiWorkspaceComponent,
] as const;
