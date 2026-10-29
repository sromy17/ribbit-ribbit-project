import { CommonModule } from '@angular/common';
import { Component, Input, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, timeout } from 'rxjs';
import { ApiService, SupportTicket } from '../../../core/services/api.service';
import { UI_COMPONENTS } from '../../../shared/ui';

@Component({
  selector: 'app-support-queue', standalone: true, imports: [CommonModule, FormsModule, ...UI_COMPONENTS],
  template: `
    <section class="support-queue">
      <h2 class="text-xl font-semibold text-ink">General support requests</h2>
      <p class="my-3 text-sm text-muted">Platform questions and account assistance submitted through Help.</p>
      <div class="flex flex-wrap items-end gap-3 mb-5">
        <label>Status<select uiInput [(ngModel)]="status" (ngModelChange)="load(0)" [disabled]="busy"><option>OPEN</option><option>IN_PROGRESS</option><option>RESOLVED</option><option>ALL</option></select></label>
        <button uiButton="secondary" type="button" (click)="load(page)" [disabled]="loading || busy">Refresh requests</button>
      </div>
      <p *ngIf="error" role="alert" class="text-danger">{{ error }}</p>
      <p *ngIf="loading" role="status">Loading support requests…</p>
      <p *ngIf="!loading && !error && !total" class="text-muted">No requests with this status.</p>
      <article *ngFor="let ticket of tickets" class="rounded-xl border border-line p-5 mb-4">
        <div class="flex flex-wrap items-center justify-between gap-3"><h3 class="font-semibold">#{{ ticket.ticketId }} · {{ ticket.subject }}</h3><app-status-badge [status]="ticket.status" /></div>
        <p class="my-2 text-xs text-muted">Account #{{ ticket.accountId }} · {{ ticket.category.replaceAll('_', ' ') }} · {{ ticket.createdAtUtc | date:'medium' }}</p>
        <p class="whitespace-pre-wrap break-words">{{ ticket.message || 'No additional details supplied.' }}</p>
        <p *ngIf="ticket.resolution" class="mt-3 whitespace-pre-wrap break-words"><strong>Support response:</strong> {{ ticket.resolution }}</p>
        <button *ngIf="canWork && ticket.status !== 'RESOLVED' && selected?.ticketId !== ticket.ticketId" uiButton="secondary" type="button" class="mt-4" (click)="select(ticket)" [disabled]="busy">Respond to request</button>
        <form *ngIf="selected?.ticketId === ticket.ticketId" (ngSubmit)="save()" class="grid gap-3 mt-5">
          <label>Status<select uiInput name="nextStatus" [(ngModel)]="nextStatus" [disabled]="busy"><option>IN_PROGRESS</option><option>RESOLVED</option></select></label>
          <label>Response visible to the customer<textarea uiInput name="response" [(ngModel)]="response" rows="4" maxlength="2000" [disabled]="busy" [required]="nextStatus === 'RESOLVED'"></textarea></label>
          <div class="flex gap-3"><button uiButton="primary" type="submit" [disabled]="busy || (nextStatus === 'RESOLVED' && !response.trim())">{{ busy ? 'Saving…' : 'Save response' }}</button><button uiButton="ghost" type="button" [disabled]="busy" (click)="selected = null">Cancel</button></div>
        </form>
      </article>
      <div *ngIf="total > 20" class="flex justify-between gap-3"><button uiButton="secondary" type="button" (click)="load(page - 1)" [disabled]="!page || loading || busy">Previous</button><span>Page {{ page + 1 }}</span><button uiButton="secondary" type="button" (click)="load(page + 1)" [disabled]="(page + 1) * 20 >= total || loading || busy">Next</button></div>
    </section>
  `
})
export class SupportQueueComponent implements OnInit, OnDestroy {
  @Input() canWork = false;
  tickets: SupportTicket[] = []; total = 0; page = 0; status = 'OPEN';
  selected: SupportTicket | null = null; nextStatus = 'IN_PROGRESS'; response = '';
  loading = false; busy = false; error = '';
  private listRequest?: Subscription; private updateRequest?: Subscription;
  constructor(private api: ApiService, private cdr: ChangeDetectorRef) {}
  ngOnInit(): void { this.load(0); }
  ngOnDestroy(): void { this.listRequest?.unsubscribe(); this.updateRequest?.unsubscribe(); }
  load(page: number): void {
    this.listRequest?.unsubscribe(); this.loading = true; this.error = ''; this.selected = null;
    this.listRequest = this.api.getAdminSupportTickets(this.status, page).pipe(timeout(10000)).subscribe({
      next: result => { this.tickets = result.items; this.total = result.total; this.page = result.page; this.loading = false; this.cdr.markForCheck(); },
      error: () => { this.loading = false; this.error = 'Unable to load support requests.'; this.cdr.markForCheck(); }
    });
  }
  select(ticket: SupportTicket): void { this.selected = ticket; this.nextStatus = 'IN_PROGRESS'; this.response = ticket.resolution || ''; }
  save(): void {
    if (!this.selected || !this.canWork || this.busy || (this.nextStatus === 'RESOLVED' && !this.response.trim())) return;
    this.busy = true; this.error = '';
    this.updateRequest = this.api.updateSupportTicket(this.selected, this.nextStatus, this.response.trim()).pipe(timeout(10000)).subscribe({
      next: () => { this.busy = false; this.load(this.page); this.cdr.markForCheck(); },
      error: error => { this.busy = false; this.error = error.status === 409 ? 'This ticket changed. Refresh the queue before retrying.' : 'Unable to confirm this response. Refresh the queue before retrying.'; this.selected = null; this.cdr.markForCheck(); }
    });
  }
}
