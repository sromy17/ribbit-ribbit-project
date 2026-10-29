import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, timeout } from 'rxjs';
import { ApiService, SupportSubmission, SupportTicket } from '../../../../core/services/api.service';
import { UI_COMPONENTS } from '../../../../shared/ui';

@Component({
  selector: 'app-help',
  standalone: true,
  imports: [CommonModule, FormsModule, ...UI_COMPONENTS],
  templateUrl: './help.component.html',
  styleUrl: './help.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HelpComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) accountId = '';
  category = 'PLATFORM_QUESTION'; subject = ''; message = '';
  submitting = false; loading = false; error = ''; listError = ''; success = '';
  tickets: SupportTicket[] = []; total = 0; page = 0;
  pending: SupportSubmission | null = null;
  readonly topics = [
    { value: 'PLATFORM_QUESTION', label: 'Platform question' },
    { value: 'TECHNICAL_ISSUE', label: 'Technical issue' },
    { value: 'ACCOUNT_ASSISTANCE', label: 'Account assistance' }
  ];
  private listRequest?: Subscription;
  private submission?: Subscription;
  constructor(private api: ApiService, private cdr: ChangeDetectorRef) {}
  ngOnChanges(): void {
    this.listRequest?.unsubscribe(); this.submission?.unsubscribe();
    this.pending = null; this.submitting = false; this.tickets = []; this.total = 0;
    this.error = this.success = this.subject = this.message = '';
    if (this.accountId) this.loadTickets(0);
  }
  ngOnDestroy(): void { this.listRequest?.unsubscribe(); this.submission?.unsubscribe(); }
  get valid(): boolean { return !!this.accountId && !!this.subject.trim() && this.subject.length <= 75 && !!this.message.trim() && this.message.length <= 2000; }
  topicLabel(value: string): string { return this.topics.find(topic => topic.value === value)?.label || 'Platform question'; }
  loadTickets(page = this.page): void {
    if (!this.accountId) return;
    this.listRequest?.unsubscribe(); this.loading = true; this.listError = '';
    const accountId = this.accountId;
    this.listRequest = this.api.getSupportTickets(accountId, page).pipe(timeout(10000)).subscribe({
      next: result => {
        if (accountId !== this.accountId) return;
        this.tickets = result.items; this.total = result.total; this.page = result.page;
        this.loading = false; this.cdr.markForCheck();
      },
      error: () => { this.loading = false; this.listError = 'Unable to load your requests. Please retry.'; this.cdr.markForCheck(); }
    });
  }
  submit(): void {
    if (this.submitting || (!this.pending && !this.valid)) return;
    this.pending ??= { category: this.category, subject: this.subject.trim(), message: this.message.trim(), requestId: crypto.randomUUID() };
    this.submitting = true; this.error = ''; this.success = '';
    const accountId = this.accountId;
    this.submission = this.api.submitSupportTicket(accountId, this.pending).pipe(timeout(12000)).subscribe({
      next: ticket => {
        if (accountId !== this.accountId) return;
        this.submitting = false; this.pending = null; this.subject = this.message = '';
        this.success = `Request #${ticket.ticketId} submitted. You can track its status below.`;
        this.loadTickets(0); this.cdr.markForCheck();
      },
      error: error => {
        this.submitting = false;
        if (error.status === 400 || error.status === 409) this.pending = null;
        this.error = error.status === 400 ? 'Check your topic, subject and message.'
          : error.status === 403 ? 'You do not have access to submit for this account.'
          : error.status === 409 ? 'This submission reference has already been used. Refresh your requests before sending again.'
          : 'We could not confirm submission. Refresh your requests or retry this same submission.';
        this.cdr.markForCheck();
      }
    });
  }

  readonly faqs = [
    {
      title: 'Order Execution & Buying Power',
      items: [
        { question: 'How are orders executed?', answer: 'Trade and Heatmap Quick Trade send orders to the backend. Displayed prices are estimates; the server determines execution price, fees, and whether an order is accepted. The estimated fee is 0.1% of notional, with a $1 minimum.' },
        { question: 'Why was my order rejected or left unconfirmed?', answer: 'Available cash, holdings, or trading rules may prevent execution. If confirmation is interrupted, check History before submitting again. Never assume a network error means the order did not execute.' }
      ]
    },
    {
      title: 'Portfolio Tracking & History Snapshots',
      items: [
        { question: 'What does the balance chart show?', answer: 'The chart shows recorded cash plus holdings valuations, not a simulated performance curve. History begins when this browser first observes the account. Earlier values are not invented.' },
        { question: 'When are snapshots recorded?', answer: 'Snapshots are captured after confirmed trades, valuation changes, and daily observations while the app is open or on the next refresh. No background snapshots are recorded when the browser is closed. Unavailable quotes may use cost basis with a valuation notice.' }
      ]
    },
    {
      title: 'Market Heatmap & TradingView Charts',
      items: [
        { question: 'What determines tile size and color?', answer: 'The heatmap displays up to 50 equities. Tile sizes currently use synthetic rank weights, not reported market capitalization. Color reflects daily percentage change on a scale clamped to -5% through +5%.' },
        { question: 'Is TradingView scraped or used for order pricing?', answer: 'No. The official TradingView widget renders its own chart in an iframe. Our app does not extract prices from it. Backend market data supplies heatmap and order prices; chart timing and coverage may differ.' },
        { question: 'Why do some news cards say Curated sample?', answer: 'Missing or rejected Finnhub credentials, timeouts, and other feed failures use locally curated sample content. These are not live publisher articles, and their links open searches. Editorial fallback images do not indicate live feed availability.' }
      ]
    },
    {
      title: 'Data Privacy & Local State Persistence',
      items: [
        { question: 'What is stored on this device?', answer: 'Account snapshots and cached market/news data use browser storage. Backend records remain authoritative for balances and orders. Clearing browser data removes local history; it does not undo server-side trades.' },
        { question: 'What should I do on a shared device?', answer: 'Log out when you finish. Browser storage can retain cached account data, so avoid using shared devices for sensitive activity.' }
      ]
    }
  ];

}
