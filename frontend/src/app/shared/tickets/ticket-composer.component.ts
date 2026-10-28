import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { DisputeCategory, TicketService } from '../../core/services/ticket.service';

@Component({
  selector: 'app-ticket-composer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <button type="button" class="ticket-trigger" [disabled]="!accountId" (click)="open()">
      {{ orderId ? 'Dispute' : 'Contact support' }}
    </button>
    <dialog #dialog aria-label="Submit a ticket" (cancel)="cancel($event)">
      <form (ngSubmit)="submit()">
        <h2>{{ orderId ? 'Dispute order #' + orderId : 'Contact support' }}</h2>
        <p>Account #{{ accountId }}{{ symbol ? ' - ' + symbol : '' }}</p>
        <p>{{ orderId ? 'Tell us what happened with this order.' : 'For account-wide issues or questions not related to a specific order.' }}</p>
        <ng-container *ngIf="!ticketId">
          <label *ngIf="orderId">Category
            <select name="category" [(ngModel)]="category" [disabled]="submitting">
              <option>Fraud</option><option>Accident</option><option>Incorrect transfer</option>
            </select>
          </label>
          <label>Description
            <textarea name="reason" [(ngModel)]="reason" maxlength="75" required rows="4" [disabled]="submitting"></textarea>
          </label>
          <p>{{ reason.length }}/75 characters. Do not include passwords or bank details.</p>
          <p *ngIf="error" role="alert" class="ticket-error">{{ error }}</p>
          <button type="submit" [disabled]="submitting || !reason.trim()">{{ submitting ? 'Submitting...' : 'Submit ticket' }}</button>
        </ng-container>
        <p *ngIf="ticketId" role="status">Ticket #{{ ticketId }} submitted. You can track its status below.</p>
        <button type="button" [disabled]="submitting" (click)="close()">{{ ticketId ? 'Close' : 'Cancel' }}</button>
      </form>
    </dialog>
  `,
  styleUrl: './tickets.css'
})
export class TicketComposerComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) accountId = '';
  @Input() orderId = '';
  @Input() symbol = '';
  @Output() submitted = new EventEmitter<void>();
  @ViewChild('dialog') dialog?: ElementRef<HTMLDialogElement>;
  reason = '';
  category: DisputeCategory = 'Accident';
  error = '';
  submitting = false;
  ticketId: number | null = null;
  private request?: Subscription;

  constructor(private readonly tickets: TicketService, private readonly cdr: ChangeDetectorRef) {}

  open(): void {
    this.reason = '';
    this.error = '';
    this.ticketId = null;
    this.category = 'Accident';
    this.dialog?.nativeElement.showModal();
  }

  close(): void {
    if (!this.submitting && this.dialog?.nativeElement.open) this.dialog.nativeElement.close();
  }

  cancel(event: Event): void {
    if (this.submitting) event.preventDefault();
  }

  submit(): void {
    if (this.submitting || this.ticketId) return;
    if (!this.accountId || !this.reason.trim() || this.reason.length > 75) {
      this.error = 'Enter a description between 1 and 75 characters.';
      return;
    }
    this.submitting = true;
    this.error = '';
    const request = this.orderId
      ? this.tickets.createDispute(this.accountId, this.orderId, this.category, this.reason.trim())
      : this.tickets.createSupport(this.accountId, this.reason.trim());
    this.request = request.subscribe({
      next: result => {
        this.ticketId = result.ticketId;
        this.submitting = false;
        this.submitted.emit();
        this.cdr.markForCheck();
      },
      error: error => {
        this.submitting = false;
        this.error = error.error?.message ||
          'Submission could not be confirmed. Refresh the ticket list before retrying.';
        this.cdr.markForCheck();
      }
    });
  }

  ngOnChanges(): void {
    this.request?.unsubscribe();
    this.submitting = false;
    if (this.dialog?.nativeElement.open) this.dialog.nativeElement.close();
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
    if (this.dialog?.nativeElement.open) this.dialog.nativeElement.close();
  }
}
