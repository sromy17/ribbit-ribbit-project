import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { Ticket, TicketKind, TicketService } from '../../core/services/ticket.service';

@Component({
  selector: 'app-ticket-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="ticket-panel">
      <h2>{{ admin ? 'Ticket review' : (kind === 'dispute' ? 'Your order disputes' : 'Your support tickets') }}</h2>
      <div class="ticket-controls">
        <label *ngIf="admin">Type
          <select [(ngModel)]="kind" (ngModelChange)="refresh()" [disabled]="saving">
            <option value="dispute">Order disputes</option><option value="support">Support tickets</option>
          </select>
        </label>
        <label>Status
          <select [(ngModel)]="statusFilter"><option value="">All statuses</option>
            <option *ngFor="let status of statuses">{{ status }}</option>
          </select>
        </label>
        <button type="button" (click)="refresh()" [disabled]="loading || saving">Refresh tickets</button>
      </div>
      <p *ngIf="error" role="alert" class="ticket-error">{{ error }}</p>
      <p *ngIf="notice" role="status">{{ notice }}</p>
      <p *ngIf="loading" role="status">Loading tickets...</p>
      <p *ngIf="!loading && !error && !filtered.length">No tickets in this view.</p>
      <div class="ticket-table" *ngIf="filtered.length">
        <table>
          <thead><tr><th>Ticket</th><th *ngIf="admin">Account / user</th><th *ngIf="kind === 'dispute'">Order</th>
            <th>Description</th><th>Status</th><th>Assigned employee</th><th>Details</th></tr></thead>
          <tbody><tr *ngFor="let ticket of filtered">
            <td>#{{ ticket.ticketId }}</td><td *ngIf="admin">#{{ ticket.accountId }} / {{ ticket.username }}</td>
            <td *ngIf="kind === 'dispute'">#{{ ticket.orderId }} {{ ticket.symbol }}</td>
            <td>{{ ticket.reason }}</td><td>{{ ticket.status }}</td><td>{{ ticket.employeeUsername || 'Unassigned' }}</td>
            <td><button type="button" [disabled]="saving" (click)="selected = ticket">View ticket</button></td>
          </tr></tbody>
        </table>
      </div>
      <section *ngIf="selected" class="ticket-details" aria-label="Ticket details">
        <h3>Ticket #{{ selected.ticketId }} - {{ selected.status }}</h3>
        <p>{{ selected.reason }}</p>
        <p *ngIf="selected.category">Category: {{ selected.category }}</p>
        <p>Account #{{ selected.accountId }} - {{ selected.accountType }}</p>
        <ng-container *ngIf="admin">
          <p>Customer: {{ selected.firstName }} {{ selected.lastName }} ({{ selected.username }}), user #{{ selected.userId }}</p>
          <p>Available cash at retrieval: {{ selected.availableFunds | currency:'USD' }}</p>
          <button type="button" [disabled]="saving" (click)="accountRequested.emit(selected)">View account history</button>
        </ng-container>
        <p *ngIf="selected.orderId">Order #{{ selected.orderId }}: {{ selected.side }} {{ selected.quantity }}
          {{ selected.symbol }} - {{ selected.orderStatus }}</p>
        <p *ngIf="selected.createdAt">Created: {{ selected.createdAt | date:'medium' }}</p>
        <p *ngIf="selected.closedAt">Closed: {{ selected.closedAt | date:'medium' }}</p>
        <p *ngIf="kind === 'dispute'">Dispute submission and completion timestamps are not stored by the current schema.</p>
        <p>Assigned employee: {{ selected.employeeUsername || 'Unassigned' }}</p>
        <p>Resolving a ticket does not reverse orders or change balances or holdings.</p>
        <ng-container *ngIf="admin && !isClosed(selected)">
          <button type="button" [disabled]="saving || selected.status === progressStatus"
            (click)="update(progressStatus)">{{ saving ? 'Saving...' : 'Mark under review' }}</button>
          <button type="button" [disabled]="saving" (click)="update(closedStatus)">Resolve ticket</button>
        </ng-container>
        <button type="button" [disabled]="saving" (click)="selected = null">Close details</button>
      </section>
    </section>
  `,
  styleUrl: './tickets.css'
})
export class TicketListComponent implements OnChanges, OnDestroy {
  @Input() kind: TicketKind = 'dispute';
  @Input() accountId = '';
  @Input() admin = false;
  @Output() accountRequested = new EventEmitter<Ticket>();
  tickets: Ticket[] = [];
  selected: Ticket | null = null;
  statusFilter = '';
  loading = false;
  saving = false;
  error = '';
  notice = '';
  private readRequest?: Subscription;
  private writeRequest?: Subscription;

  constructor(private readonly api: TicketService, private readonly cdr: ChangeDetectorRef) {}

  get progressStatus(): string { return this.kind === 'dispute' ? 'UNDER-REVIEW' : 'IN_PROGRESS'; }
  get closedStatus(): string { return this.kind === 'dispute' ? 'COMPLETED' : 'RESOLVED'; }
  get statuses(): string[] { return [this.kind === 'dispute' ? 'DISPUTED' : 'OPEN', this.progressStatus, this.closedStatus]; }
  get filtered(): Ticket[] { return this.tickets.filter(ticket => !this.statusFilter || ticket.status === this.statusFilter); }
  isClosed(ticket: Ticket): boolean { return ticket.status === 'COMPLETED' || ticket.status === 'RESOLVED'; }

  ngOnChanges(): void {
    this.writeRequest?.unsubscribe();
    this.saving = false;
    this.notice = '';
    this.refresh();
  }

  refresh(): void {
    if (this.saving) return;
    this.readRequest?.unsubscribe();
    this.error = '';
    this.selected = null;
    this.tickets = [];
    if (!this.statuses.includes(this.statusFilter)) this.statusFilter = '';
    if (!this.admin && !this.accountId) return;
    this.loading = true;
    this.readRequest = this.api.list(this.kind, this.admin ? undefined : this.accountId).subscribe({
      next: tickets => { this.tickets = tickets; this.loading = false; this.cdr.markForCheck(); },
      error: error => {
        this.error = error.error?.message || 'Unable to load tickets. Please refresh to retry.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  update(status: string): void {
    if (!this.admin || !this.selected || this.saving) return;
    const selected = this.selected;
    this.saving = true;
    this.error = '';
    this.notice = '';
    this.writeRequest = this.api.update(selected, status).subscribe({
      next: () => {
        this.saving = false;
        this.notice = `Ticket #${selected.ticketId} updated to ${status}.`;
        this.refresh();
        this.cdr.markForCheck();
      },
      error: error => {
        this.saving = false;
        this.error = error.error?.message || 'Update could not be confirmed. Refresh tickets before retrying.';
        this.cdr.markForCheck();
      }
    });
  }

  ngOnDestroy(): void {
    this.readRequest?.unsubscribe();
    this.writeRequest?.unsubscribe();
  }
}
