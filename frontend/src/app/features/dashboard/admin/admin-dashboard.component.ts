import { ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable, Subscription, timeout } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService, Holding, Order, Trade, TradingAccount, WalletActivity } from '../../../core/services/api.service';
import { UiEmptyStateComponent, UiPageHeadingComponent, UiStatusBadgeComponent, UiWorkspaceComponent } from '../../../shared/ui';
import { TicketListComponent } from '../../../shared/tickets/ticket-list.component';
import { Ticket } from '../../../core/services/ticket.service';

type HistoryKind = 'orders' | 'trades' | 'holdings' | 'cash';
interface HistoryColumn { key: string; label: string; }
interface HistoryRow { [key: string]: string | number; }

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, UiWorkspaceComponent, UiPageHeadingComponent, UiStatusBadgeComponent, UiEmptyStateComponent, TicketListComponent],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['../../../shared/ui/staff-dashboard.css', './admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnDestroy {
  readonly currentUser;
  activeTab: 'orders' | 'users' | 'tickets' = 'orders';
  readonly tabs = ['orders', 'users', 'tickets'] as const;
  private readonly errors = { lookup: '', orders: '', history: '', filters: '' };
  get error(): string { return Object.values(this.errors).filter(Boolean).join(' '); }
  loading = false;
  historyLoading = false;
  userId: number | null = null;
  lookupComplete = false;
  accounts: TradingAccount[] = [];
  accountId = '';
  orders: Order[] = [];
  symbol = '';
  status = '';
  from = '';
  to = '';
  page = 0;
  readonly pageSize = 25;
  selectedOrder: Order | null = null;
  ordersAsOf = '';
  historyKind: HistoryKind = 'orders';
  historyRows: HistoryRow[] = [];
  historyColumns: HistoryColumn[] = [];
  historyAsOf = '';
  private lookupRequest?: Subscription;
  private ordersRequest?: Subscription;
  private historyRequest?: Subscription;

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly api: ApiService,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.currentUser = auth.getUser();
  }

  lookupUser(preferredAccountId = ''): void {
    if (!this.userId || !Number.isSafeInteger(this.userId) || this.userId < 1) {
      this.errors.lookup = 'Enter a positive whole-number user ID.';
      return;
    }
    this.lookupRequest?.unsubscribe();
    this.ordersRequest?.unsubscribe();
    this.historyRequest?.unsubscribe();
    this.accounts = [];
    this.accountId = '';
    this.orders = [];
    this.ordersAsOf = '';
    this.historyRows = [];
    this.historyColumns = [];
    this.historyAsOf = '';
    this.selectedOrder = null;
    this.page = 0;
    this.lookupComplete = false;
    this.errors.lookup = this.errors.orders = this.errors.history = '';
    this.loading = true;
    this.historyLoading = false;
    this.lookupRequest = this.api.getAccountsForUser(String(this.userId)).pipe(timeout(10000)).subscribe({
      next: accounts => {
        this.accounts = accounts;
        this.lookupComplete = true;
        this.loading = false;
        if (accounts.length) {
          this.accountId = String(accounts.find(account => String(account.accountId) === preferredAccountId)?.accountId
            ?? accounts[0].accountId);
          this.loadAccount();
        }
        this.cdr.markForCheck();
      },
      error: error => {
        this.loading = false;
        this.showError('lookup', 'Unable to look up accounts for this user.', error);
      }
    });
  }

  loadAccount(): void {
    this.historyRequest?.unsubscribe();
    this.historyRows = [];
    this.historyColumns = [];
    this.historyAsOf = '';
    this.historyLoading = false;
    this.errors.history = '';
    this.loadOrders();
    if (this.activeTab === 'users') this.loadHistory();
  }

  inspectTicketAccount(ticket: Ticket): void {
    this.userId = ticket.userId;
    this.activeTab = 'users';
    this.lookupUser(String(ticket.accountId));
  }

  setTab(tab: 'orders' | 'users' | 'tickets'): void {
    this.activeTab = tab;
    if (tab === 'users' && this.accountId) this.loadHistory();
  }

  loadOrders(): void {
    this.ordersRequest?.unsubscribe();
    this.orders = [];
    this.ordersAsOf = '';
    this.selectedOrder = null;
    this.page = 0;
    if (!this.accountId) return;
    this.errors.orders = '';
    this.loading = true;
    this.ordersRequest = this.api.getOrders(this.accountId).pipe(timeout(10000)).subscribe({
      next: orders => {
        this.orders = orders;
        this.ordersAsOf = new Date().toISOString();
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.loading = false;
        this.showError('orders', 'Unable to load account orders.', error);
      }
    });
  }

  applyFilters(): void {
    this.page = 0;
    this.selectedOrder = null;
    this.errors.filters = this.from && this.to && this.from > this.to
      ? 'The start date must be on or before the end date.' : '';
  }

  get filteredOrders(): Order[] {
    if (this.from && this.to && this.from > this.to) return [];
    const symbol = this.symbol.trim().toUpperCase();
    return this.orders.filter(order => {
      const date = order.dateRequested ? new Date(order.dateRequested).toISOString().slice(0, 10) : '';
      return (!symbol || order.symbol.toUpperCase().includes(symbol)) &&
        (!this.status || order.status === this.status) &&
        (!this.from || (!!date && date >= this.from)) &&
        (!this.to || (!!date && date <= this.to));
    });
  }

  get visibleOrders(): Order[] {
    return this.filteredOrders.slice(this.page * this.pageSize, (this.page + 1) * this.pageSize);
  }

  loadHistory(): void {
    this.historyRequest?.unsubscribe();
    this.historyRows = [];
    this.historyColumns = [];
    this.historyAsOf = '';
    if (!this.accountId) return;
    this.errors.history = '';
    this.historyLoading = true;
    switch (this.historyKind) {
      case 'orders':
        this.readHistory(this.api.getOrders(this.accountId), [
          { key: 'id', label: 'Order' }, { key: 'symbol', label: 'Symbol' }, { key: 'side', label: 'Side' },
          { key: 'quantity', label: 'Quantity' }, { key: 'status', label: 'Status' }, { key: 'date', label: 'Requested' }
        ], (order: Order) => ({
          id: order.orderId, symbol: order.symbol, side: order.side, quantity: order.quantity,
          status: order.status, date: order.dateRequested ?? 'Not supplied'
        }));
        break;
      case 'trades':
        this.readHistory(this.api.getTrades(this.accountId), [
          { key: 'id', label: 'Trade' }, { key: 'quantity', label: 'Executed quantity' },
          { key: 'price', label: 'Execution price' }, { key: 'date', label: 'Executed' }
        ], (trade: Trade) => ({
          id: trade.tradeId, quantity: trade.executedQuantity,
          price: trade.executionPrice, date: trade.executedAt ?? 'Not supplied'
        }));
        break;
      case 'holdings':
        this.readHistory(this.api.getHoldings(this.accountId), [
          { key: 'symbol', label: 'Symbol' }, { key: 'quantity', label: 'Quantity' }, { key: 'cost', label: 'Average cost' }
        ], (holding: Holding) => ({ symbol: holding.symbol, quantity: holding.quantity, cost: holding.averageCost }));
        break;
      case 'cash':
        this.readHistory(this.api.getWalletActivity(this.accountId), [
          { key: 'id', label: 'Movement' }, { key: 'bank', label: 'Bank ID' }, { key: 'type', label: 'Type' },
          { key: 'amount', label: 'Amount' }, { key: 'date', label: 'Recorded' }
        ], (movement: WalletActivity) => ({
          id: movement.movementId, bank: movement.bankId, type: movement.movementType,
          amount: movement.amount, date: movement.movementDate
        }));
        break;
    }
  }

  private readHistory<T>(request: Observable<T[]>, columns: HistoryColumn[], row: (item: T) => HistoryRow): void {
    this.historyRequest = request.pipe(timeout(10000)).subscribe({
      next: items => {
        this.historyRows = items.map(row);
        this.historyColumns = columns;
        this.historyAsOf = new Date().toISOString();
        this.historyLoading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.historyLoading = false;
        this.showError('history', 'Unable to load account history.', error);
      }
    });
  }

  get selectedAccount(): TradingAccount | undefined {
    return this.accounts.find(account => String(account.accountId) === this.accountId);
  }

  private showError(scope: keyof AdminDashboardComponent['errors'], message: string, error: unknown): void {
    console.error(message, error);
    this.errors[scope] = `${message} Retry when the service is available.`;
    this.cdr.markForCheck();
  }

  ngOnDestroy(): void {
    this.lookupRequest?.unsubscribe();
    this.ordersRequest?.unsubscribe();
    this.historyRequest?.unsubscribe();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/employee-login']);
  }
}
