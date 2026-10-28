import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ThemeToggleComponent } from '../theme-toggle/theme-toggle.component';

@Component({
  selector: 'app-workspace',
  standalone: true,
  imports: [ThemeToggleComponent],
  template: `
    <a class="skip-link" href="#workspace-content" (click)="$event.preventDefault(); content.focus()">Skip to workspace</a>
    <header>
      <div class="brand"><strong>Leap Trader Pro</strong><span>{{ area }}</span></div>
      <div class="actions"><app-theme-toggle /><span>{{ username }}</span><button type="button" (click)="logout.emit()">Sign out</button></div>
    </header>
    <main #content id="workspace-content" class="tw:min-h-0 tw:flex-1 tw:overflow-auto tw:p-4 tw:sm:p-7" tabindex="-1"><ng-content /></main>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; color: var(--md-on-surface); background: var(--md-surface); }
    header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; padding: 20px 28px; border-bottom: 1px solid var(--md-outline-variant); }
    .brand, .actions { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; }
    .brand span { color: var(--md-on-surface-variant); }
    .actions { margin-left: auto; }
    .actions span { overflow-wrap: anywhere; }
    button { border: 1px solid var(--md-outline); border-radius: 8px; padding: 8px 14px; color: inherit; background: var(--md-surface-container); cursor: pointer; }
    .skip-link { position: fixed; top: -100px; left: 16px; z-index: 100; padding: 12px; color: var(--md-on-primary); background: var(--md-primary); }
    .skip-link:focus { top: 8px; }
    @media (max-width: 640px) { header { padding: 16px; } }
  `]
})
export class UiWorkspaceComponent {
  @Input() username = '';
  @Input() area = 'Workspace';
  @Output() logout = new EventEmitter<void>();
}

@Component({
  selector: 'app-page-heading',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="heading">
      <p class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p *ngIf="description">{{ description }}</p>
    </div>
  `,
  styles: [`
    .heading { margin-bottom: 24px; }
    .eyebrow { color: var(--md-primary); text-transform: uppercase; letter-spacing: 0.1em; font-size: 0.75rem; }
    h1 { margin: 8px 0; font-size: 2rem; }
    p { color: var(--md-on-surface-variant); line-height: 1.6; margin: 0; }
  `]
})
export class UiPageHeadingComponent {
  @Input() eyebrow = 'Workspace';
  @Input() title = '';
  @Input() description = '';
}

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span [class.success]="success" [class.danger]="danger">{{ (status || 'Unknown').replaceAll('_', ' ') }}</span>`,
  styles: [`
    span { display: inline-block; padding: 4px 8px; border-radius: 6px; background: var(--md-warning-bg); color: var(--md-warning-fg); font-size: 0.8rem; }
    .success { background: var(--md-success-bg); color: var(--md-success-fg); }
    .danger { background: var(--md-danger-bg); color: var(--md-danger-fg); }
  `]
})
export class UiStatusBadgeComponent {
  @Input() status: string | null | undefined;
  get success(): boolean { return /^(EXECUTED|FILLED|BUY|DEPOSIT)$/i.test(this.status ?? ''); }
  get danger(): boolean { return /^(REJECTED|FAILED|SELL|WITHDRAWAL)$/i.test(this.status ?? ''); }
}

@Component({
  selector: 'app-empty-state',
  standalone: true,
  template: `<div role="status"><h3>{{ title }}</h3><p>{{ description }}</p></div>`,
  styles: [`
    div { border: 1px dashed var(--md-outline-variant); border-radius: 12px; padding: 24px; margin: 16px 0; text-align: center; }
    h3 { margin: 0 0 8px; } p { margin: 0; color: var(--md-on-surface-variant); line-height: 1.6; }
  `]
})
export class UiEmptyStateComponent {
  @Input() title = 'Nothing here yet';
  @Input() description = '';
}
