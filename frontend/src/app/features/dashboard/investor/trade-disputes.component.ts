import { CommonModule } from '@angular/common';
import { Component, ChangeDetectorRef, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil, timeout } from 'rxjs';
import { ApiService, CustomerDispute, DisputeSubmission, Order, TradeDisputeSummary } from '../../../core/services/api.service';
import { UI_COMPONENTS } from '../../../shared/ui';

@Component({
  selector: 'app-trade-disputes', standalone: true, imports: [CommonModule, FormsModule, ...UI_COMPONENTS],
  template: `
    <div class="flex flex-col items-start gap-1">
      <span [class.text-brand]="summary?.status === 'RESOLVED'" [class.text-muted]="!summary?.total">
        {{ !summary ? 'Ticket status unavailable' : summary.active ? 'Under review' : summary.total ? 'Resolved' : 'No disputes' }}
      </span>
      <small *ngIf="summary?.total" class="text-muted">{{ summary!.active }} active · {{ summary!.resolved }} resolved</small>
      <button uiButton="ghost" type="button" (click)="open()" [disabled]="!eligible">{{ summary?.total ? 'View tickets / dispute' : 'Dispute trade' }}</button>
    </div>
    <dialog #dialog class="trade-dispute-dialog" aria-labelledby="dispute-heading" (cancel)="cancel($event)" (click)="backdrop($event)">
      <header class="flex items-start justify-between gap-4">
        <div><h2 id="dispute-heading">Trade #{{ order.orderId }} · {{ order.symbol }}</h2><p class="mt-2 text-xs text-muted">Account #{{ accountId }} · {{ order.side }} · Executed quantity {{ order.executedQuantity ?? order.quantity }}</p></div>
        <button uiButton="ghost" type="button" aria-label="Close trade disputes" [disabled]="submitting" (click)="close()">✕</button>
      </header>
      <p class="my-4 text-sm text-muted">You can open multiple tickets for this trade, including after an earlier ticket is resolved. Resolved tickets remain closed.</p>
      <div *ngIf="success" class="success-banner" role="status">{{ success }}</div>
      <p *ngIf="listError" class="error-banner" role="alert">{{ listError }} <button uiButton="ghost" type="button" (click)="loadTickets()">Retry</button></p>
      <section aria-label="Your dispute tickets" class="my-5 space-y-3">
        <div class="flex justify-between gap-3"><h3>Your tickets ({{ total }})</h3><button uiButton="ghost" type="button" [disabled]="loading || submitting" (click)="loadTickets()">Refresh status</button></div>
        <p *ngIf="loading" role="status">Loading tickets…</p>
        <p *ngIf="!loading && !listError && !total" class="text-muted">No tickets submitted for this trade.</p>
        <article *ngFor="let ticket of tickets" class="rounded-lg border border-line bg-subtle p-4">
          <div class="flex flex-wrap items-center justify-between gap-2"><strong>#{{ ticket.caseId }} · {{ ticket.title }}</strong><app-status-badge [status]="ticket.status" /></div>
          <p class="mt-2 whitespace-pre-wrap break-words">{{ ticket.reason }}</p>
          <p *ngIf="ticket.feeRelated" class="mt-2 text-xs text-brand">Includes trade fee</p>
          <p *ngIf="ticket.status === 'RESOLVED'" class="mt-3 whitespace-pre-wrap break-words"><strong>Resolution:</strong> {{ ticket.resolutionReason }}</p>
          <p class="mt-2 text-xs text-muted">Submitted {{ ticket.createdAtUtc | date:'medium':'UTC' }} UTC · Updated {{ ticket.updatedAtUtc | date:'medium':'UTC' }} UTC</p>
        </article>
        <div *ngIf="total > 10" class="flex items-center justify-between gap-3"><button uiButton="secondary" type="button" [disabled]="!page || loading" (click)="loadTickets(page - 1)">Previous</button><span>Page {{ page + 1 }}</span><button uiButton="secondary" type="button" [disabled]="(page + 1) * 10 >= total || loading" (click)="loadTickets(page + 1)">Next</button></div>
      </section>
      <form (ngSubmit)="submit()" class="space-y-4 border-t border-line pt-5">
        <h3>Submit a new ticket</h3>
        <label class="block">Transaction ID<input uiInput [value]="order.orderId" readonly aria-label="Disputed transaction ID" /></label>
        <label class="block">Short title<input uiInput name="title" [(ngModel)]="title" required maxlength="120" [disabled]="submitting || !!pending" /></label>
        <label class="block">Reason<textarea uiInput name="reason" [(ngModel)]="reason" required maxlength="1000" rows="4" [disabled]="submitting || !!pending"></textarea></label>
        <label class="flex items-center gap-2"><input type="checkbox" name="feeRelated" [(ngModel)]="feeRelated" [disabled]="submitting || !!pending" /> This dispute includes the trade fee</label>
        <p *ngIf="error" class="error-banner" role="alert">{{ error }}</p>
        <button uiButton="primary" type="submit" [disabled]="submitting || (!pending && (!title.trim() || !reason.trim()))">{{ submitting ? 'Submitting…' : pending ? 'Retry same submission' : 'Submit dispute' }}</button>
      </form>
    </dialog>
  `,
  styles: [`
    :host { display: block; min-width: 170px; }
    .trade-dispute-dialog { position: fixed; inset: 0; margin: auto; width: min(720px, calc(100vw - 32px)); max-height: calc(100dvh - 48px); padding: 24px; overflow-y: auto; border: 1px solid var(--ui-line); border-radius: 18px; background: var(--ui-panel); color: var(--ui-ink); text-align: left; white-space: normal; }
    .trade-dispute-dialog::backdrop { background: rgb(0 0 0 / 55%); backdrop-filter: blur(4px); }
    label:has(input[type="checkbox"]) { display: flex; flex-direction: row; justify-content: flex-start; align-items: center; }
    input[type="checkbox"] { width: 1rem; height: 1rem; min-height: 0; flex: none; margin: 0; accent-color: var(--ui-brand); }
  `]
})
export class TradeDisputesComponent implements OnDestroy {
  @Input({ required: true }) accountId = '';
  @Input({ required: true }) order!: Order;
  @Input() summary: TradeDisputeSummary | null = null;
  @Output() changed = new EventEmitter<void>();
  @Output() dialogOpen = new EventEmitter<boolean>();
  @ViewChild('dialog', { static: true }) dialog!: ElementRef<HTMLDialogElement>;
  tickets: CustomerDispute[] = []; total = 0; page = 0;
  title = ''; reason = ''; feeRelated = false; error = ''; listError = ''; success = '';
  submitting = false; loading = false;
  pending: DisputeSubmission | null = null;
  private readonly destroy$ = new Subject<void>();
  private listRevision = 0;
  private dirty = false;
  constructor(private api: ApiService, private cdr: ChangeDetectorRef) {}
  get eligible() { return (this.order.executedQuantity ?? 0) > 0 || ['EXECUTED', 'FILLED'].includes(this.order.status); }
  open() {
    if (!this.eligible) return;
    this.dialog.nativeElement.showModal(); this.dialogOpen.emit(true); this.loadTickets();
  }
  cancel(event: Event) { event.preventDefault(); this.close(); }
  backdrop(event: MouseEvent) { if (event.target === this.dialog.nativeElement) this.close(); }
  close() {
    if (this.submitting) return;
    this.dialog.nativeElement.close(); this.dialogOpen.emit(false);
    this.dirty = false; this.changed.emit();
  }
  loadTickets(page = this.page) {
    const revision = ++this.listRevision;
    this.loading = true; this.listError = ''; this.page = page;
    this.api.getCustomerDisputes(this.accountId, this.order.orderId, page).pipe(timeout(10000), takeUntil(this.destroy$)).subscribe({
      next: result => { if (revision !== this.listRevision) return; this.tickets = result.items; this.total = result.total; this.loading = false; this.cdr.markForCheck(); },
      error: () => { if (revision !== this.listRevision) return; this.loading = false; this.listError = 'Tickets could not be loaded.'; this.cdr.markForCheck(); }
    });
  }
  submit() {
    if (this.submitting || !this.eligible || (!this.pending && (!this.title.trim() || !this.reason.trim()))) return;
    this.pending ??= {transactionType: 'TRADE', transactionId: String(this.order.orderId), title: this.title.trim(), reason: this.reason.trim(), feeRelated: this.feeRelated, idempotencyKey: crypto.randomUUID()};
    this.submitting = true; this.error = ''; this.success = '';
    this.api.submitTradeDispute(this.pending).pipe(timeout(15000), takeUntil(this.destroy$)).subscribe({
      next: ticket => {
        this.pending = null; this.submitting = false; this.dirty = true;
        this.success = `Ticket #${ticket.caseId} submitted to the admin queue.`;
        this.title = this.reason = ''; this.feeRelated = false; this.page = 0; this.loadTickets(); this.cdr.markForCheck();
      },
      error: response => {
        this.submitting = false;
        if ([400,403,404].includes(response.status)) { this.pending = null; this.error = response.error?.message || 'This trade could not be disputed. Refresh and try again.'; }
        else this.error = 'Submission could not be confirmed. Retry this same submission to avoid a duplicate ticket.';
        this.cdr.markForCheck();
      }
    });
  }
  ngOnDestroy() { this.destroy$.next(); this.destroy$.complete(); }
}
