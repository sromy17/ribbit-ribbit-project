import { UI_COMPONENTS } from '../../../shared/ui';
import { Component, OnDestroy, OnInit, ChangeDetectorRef, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService, AdminOrder, DisputeCase, TradingAccount, ReportingStatus } from '../../../core/services/api.service';

type Row = Record<string, any>;
@Component({
  selector: 'app-admin-dashboard', changeDetection: ChangeDetectionStrategy.Default, standalone: true, imports: [...UI_COMPONENTS, CommonModule, FormsModule],
  templateUrl: './admin-dashboard.component.html', styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit, OnDestroy {
  currentUser;
  activeTab = 'orders';
  error = ''; loading = false; busy = false;
  from = new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
  to = new Date().toISOString().slice(0, 10);
  symbol = ''; status = ''; orderPage = 0; orderTotal = 0; orders: AdminOrder[] = [];
  ordersAsOf: string | null = null;
  reportingStatus: ReportingStatus | null = null;
  activity: Row | null = null;
  investigation: Row | null = null; investigationId: number | null = null;
  investigationLoading = false;
  caseStatus = 'OPEN'; disputes: DisputeCase[] = []; casePage = 0; caseTotal = 0; caseAsOf = '';
  selectedCase: DisputeCase | null = null; caseEvents: Row[] = [];
  reason = ''; note = ''; outcome = 'UPHELD'; newAssignee: number | null = null;
  userId: number | null = null; user: {userId: number; username: string} | null = null;
  accounts: TradingAccount[] = []; accountId = ''; historyKind = 'orders'; historyPage = 0;
  history: Row | null = null; historyLoading = false;
  private requests = new Map<string, Subscription>();

  constructor(private auth: AuthService, private router: Router, private api: ApiService, private cdr: ChangeDetectorRef) {
    this.currentUser = auth.getUser();
  }
  ngOnInit() { this.loadOrders(); this.loadCases(); if (this.canReport) this.refreshReporting(); }
  get canWork() { return this.currentUser?.permissions?.includes('PERM_DISPUTE_WORK') ?? false; }
  get canSupervise() { return this.currentUser?.permissions?.includes('PERM_DISPUTE_SUPERVISOR') ?? false; }
  get canReport() { return this.currentUser?.permissions?.includes('PERM_REPORTING') ?? false; }
  ngOnDestroy() { this.requests.forEach(request => request.unsubscribe()); }
  private run<T>(key: string, request: import('rxjs').Observable<T>, done: (value: T) => void) {
    this.requests.get(key)?.unsubscribe();
    this.error = '';
    this.requests.set(key, request.subscribe({next: value => { done(value); this.cdr.detectChanges(); }, error: e => {
      this.loading = this.busy = this.historyLoading = this.investigationLoading = false;
      this.error = e.status === 409 ? 'This record changed. Reload it before retrying.'
        : e.status === 403 ? 'You no longer have permission for this action.'
        : e.error?.message || 'Unable to load data. Please retry.';
      this.cdr.detectChanges();
    }}));
  }
  loadOrders(page = 0) {
    this.loading = true; this.orders = []; this.orderPage = page;
    this.run('orders', this.api.getAdminOrders(this.from, this.to, page, 25, this.status, this.symbol), result => {
      this.orders = result.items; this.orderTotal = result.total; this.ordersAsOf = result.asOf ?? null; this.loading = false;
    });
  }
  refreshReporting() { this.run('reporting', this.api.getAdminReportingStatus(), status => this.reportingStatus = status); this.run('activity', this.api.getAdminActivity(this.from, this.to), data => this.activity = data); }
  openInvestigation(id: number) {
    this.investigationId = id; this.investigation = null; this.investigationLoading = true;
    this.run('investigation', this.api.investigate(id), result => { this.investigation = result; this.investigationLoading = false; });
  }
  loadCases(page = 0) {
    this.disputes = []; this.casePage = page;
    this.run('cases', this.api.getAdminCasePage(this.caseStatus, page), result => { this.disputes = result.items; this.caseTotal = result.total; this.caseAsOf = result.asOf; });
  }
  openCase(id: number) {
    this.selectedCase = null; this.caseEvents = []; this.reason = ''; this.note = ''; this.newAssignee = null;
    this.run('case', forkJoin({detail: this.api.getAdminCase(id), events: this.api.getAdminCaseEvents(id)}), result => {
      this.selectedCase = result.detail; this.caseEvents = result.events;
    });
  }
  get isOwner() { return String(this.selectedCase?.assignedStaffUserId) === this.currentUser?.id; }
  act(action: string) {
    const item = this.selectedCase;
    if (!item || this.busy) return;
    if (['release', 'resolve', 'reassign'].includes(action) && !this.reason.trim()) { this.error = 'A reason is required.'; return; }
    if (action === 'notes' && !this.note.trim()) { this.error = 'Enter a note.'; return; }
    if (action === 'reassign' && (!this.newAssignee || this.newAssignee < 1)) { this.error = 'Enter an admin user ID.'; return; }
    this.busy = true;
    this.run('action', this.api.caseAction(item.caseId, action, {
      expectedVersion: item.version, reason: this.reason.trim(), note: this.note.trim(), outcome: this.outcome, newAssigneeUserId: this.newAssignee
    }), updated => { this.busy = false; this.openCase(updated.caseId); this.loadCases(); });
  }
  lookupUser(id = this.userId) {
    if (!id || !Number.isInteger(id) || id < 1) { this.error = 'Enter a valid user ID.'; return; }
    this.userId = id; this.user = null; this.accounts = []; this.history = null;
    this.requests.get('history')?.unsubscribe();
    this.run('user', forkJoin({user: this.api.lookupUser(id), accounts: this.api.lookupAccounts(id)}), result => {
      this.user = result.user; this.accounts = result.accounts;
      if (this.accounts.length) { this.accountId = String(this.accounts[0].accountId); this.loadHistory(); }
    });
  }
  investigateCustomer() {
    if (!this.selectedCase) return;
    this.activeTab = 'users'; this.lookupUser(this.selectedCase.customerUserId);
  }
  loadHistory(page = 0) {
    if (!this.accountId) return;
    this.historyPage = page; this.history = null; this.historyLoading = true;
    this.run('history', this.api.accountHistory(this.accountId, this.historyKind, page), result => { this.history = result; this.historyLoading = false; });
  }
  columnLabel(key: string): string {
    const labels: Record<string, string> = {requestid: 'Order', ticker: 'Symbol', side: 'Side', quantity: 'Quantity', bidprice: 'Order price', status: 'Status', created_at_utc: 'Created (UTC)', executeid: 'Fill', quantity_executed: 'Executed quantity', askprice: 'Execution price', fee: 'Fee', executed_at_utc: 'Executed (UTC)', holdingid: 'Holding', average_cost: 'Average cost', as_of_date: 'As of date', movement_id: 'Movement', order_id: 'Order', amount: 'Amount', currency: 'Currency', movement_type: 'Type', occurred_at_utc: 'Recorded (UTC)'};
    return labels[key] || key.replaceAll('_', ' ');
  }
  get historyColumns(): string[] { return this.history?.['items']?.length ? Object.keys(this.history['items'][0]) : []; }
  get visibleCases() { return this.disputes; }
  logout() { this.auth.logout(); this.router.navigate(['/login']); }
}
