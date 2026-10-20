import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService, User } from '../../../core/services/auth.service';
import { ApiService, Holding, Order, Trade, SymbolTrend, TradingAccount } from '../../../core/services/api.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  activeView: 'portfolio' | 'trade' | 'history' | 'help' = 'portfolio';
  accountTypeLabel = 'Trading Account';
  currentUser: User | null = null;
  currentRole: string = '';
  accountId: string = ''; // Store account ID for API calls
  accounts: TradingAccount[] = [];
  selectedAccount: TradingAccount | null = null;
  loadingAccounts = false;
  accountMenuOpen = false;
  loading = false;
  loadingBalance = false;
  loadingHoldings = false;
  loadingOrders = false;
  loadingTrades = false;
  error = '';

  // Investor Dashboard Data
  portfolio = {
    cash: 0,
    holdings: [] as Holding[],
    totalValue: 0
  };

  orders: Order[] = [];
  successfulOrders: Order[] = [];
  symbolTrends: SymbolTrend[] = [];

  orderForm = { symbol: '', side: 'BUY', quantity: 0 };
  orderFormErrors: { [key: string]: string } = {};
  cancelingOrderId: string | null = null;

  // Trade view state
  tradeSearch = '';
  tradeSearchError = '';
  selectedTicker = '';
  showOrderModal = false;
  orderPlaced = false;
  currentPrice: number | null = null;
  loadingQuote = false;
  quoteError = '';
  currentChange: number | null = null;
  currentChangePercent: number | null = null;
  private readonly feeRate = 0.001;
  private readonly minFee = 1;

  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private api: ApiService,
    private cdr: ChangeDetectorRef
  ) {
    this.currentUser = this.auth.getUser();
    if (!this.currentUser) {
      this.router.navigate(['/login']);
    } else {
      this.currentRole = this.currentUser.role;
    }
  }

  ngOnInit() {
    const accountId = this.route.snapshot.paramMap.get('accountId');
    if (!this.currentUser) {
      return;
    }
    if (!accountId) {
      this.router.navigate(['/accounts']);
      return;
    }
    this.accountId = accountId;
    this.loadAccountDetails();
    this.loadAccounts();
    this.loadDashboardData();
  }

  loadAccounts(): void {
    if (!this.currentUser) {
      return;
    }

    this.loadingAccounts = true;
    this.api.getAccountsForUser(this.currentUser.id).subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.selectedAccount = accounts.find((account) => account.accountId === this.accountId) ?? null;
        this.loadingAccounts = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loadingAccounts = false;
        this.cdr.detectChanges();
      }
    });
  }

  toggleAccountMenu(): void {
    this.accountMenuOpen = !this.accountMenuOpen;
  }

  selectAccount(account: TradingAccount): void {
    this.accountMenuOpen = false;
    if (account.accountId !== this.accountId) {
      this.router.navigate(['/investor-dashboard', account.accountId]);
    }
  }

  createAccount(): void {
    this.accountMenuOpen = false;
    this.router.navigate(['/accounts/create']);
  }

  formatAccountType(type: string): string {
    const normalizedType = (type ?? '').toLowerCase();
    return normalizedType
      ? `${normalizedType.charAt(0).toUpperCase()}${normalizedType.slice(1)} Account`
      : 'Trading Account';
  }

  selectTicker(): void {
    const symbol = this.tradeSearch.trim().toUpperCase();
    if (!/^[A-Z]{1,5}$/.test(symbol)) {
      this.tradeSearchError = 'Enter a valid ticker (e.g., AAPL, MSFT)';
      return;
    }
    this.tradeSearchError = '';
    this.selectedTicker = symbol;
    this.orderForm = { symbol, side: 'BUY', quantity: 0 };
    this.orderFormErrors = {};
    this.orderPlaced = false;
    this.showOrderModal = true;
    this.loadQuote(symbol);
  }

  loadQuote(symbol: string): void {
    this.loadingQuote = true;
    this.quoteError = '';
    this.currentPrice = null;
    this.currentChange = null;
    this.currentChangePercent = null;
    this.api.getQuote(symbol).subscribe({
      next: (quote) => {
        this.currentPrice = quote.price;
        this.currentChange = quote.change ?? null;
        this.currentChangePercent = quote.changePercent ?? null;
        this.loadingQuote = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading quote:', err);
        this.quoteError = 'Price unavailable';
        this.loadingQuote = false;
        this.cdr.detectChanges();
      }
    });
  }

  getEstimatedNotional(): number {
    return (this.currentPrice ?? 0) * (this.orderForm.quantity || 0);
  }

  getEstimatedFee(): number {
    const notional = this.getEstimatedNotional();
    if (!notional) {
      return 0;
    }
    return Math.max(notional * this.feeRate, this.minFee);
  }

  getEstimatedTotal(): number {
    const notional = this.getEstimatedNotional();
    const fee = this.getEstimatedFee();
    return this.orderForm.side === 'BUY' ? notional + fee : notional - fee;
  }

  closeOrderModal(): void {
    this.showOrderModal = false;
    this.orderPlaced = false;
    this.orderFormErrors = {};
  }

  backToPortfolio(): void {
    this.closeOrderModal();
    this.tradeSearch = '';
    this.setView('portfolio');
  }

  setView(view: 'portfolio' | 'trade' | 'history' | 'help'): void {
    this.activeView = view;
  }

  loadAccountDetails(): void {
    this.api.getAccountDetails(this.accountId).subscribe({
      next: (account) => {
        this.selectedAccount = account;
        const normalizedType = (account.type ?? '').toLowerCase();
        this.accountTypeLabel = normalizedType
          ? `${normalizedType.charAt(0).toUpperCase()}${normalizedType.slice(1)} Account`
          : 'Trading Account';
      },
      error: () => {
        this.accountTypeLabel = 'Trading Account';
      }
    });
  }

  resetDashboardData(): void {
    this.portfolio = { cash: 0, holdings: [], totalValue: 0 };
    this.orders = [];
    this.successfulOrders = [];
  }

  loadDashboardData() {
    this.loadingBalance = true;
    this.loadingHoldings = true;
    this.loadingOrders = true;
    this.loadingTrades = true;
    this.error = '';

    // Load balance
    this.api.getAccountBalance(this.accountId).subscribe({
      next: (balance) => {
        this.portfolio.cash = balance.cashBalance;
        this.updateTotalValue();
        this.loadingBalance = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading balance:', err);
        this.error = 'Failed to load account balance';
        this.loadingBalance = false;
        this.cdr.detectChanges();
      }
    });

    // Load holdings
    this.api.getHoldings(this.accountId).subscribe({
      next: (holdings) => {
        this.portfolio.holdings = holdings.filter((holding) => holding.quantity > 0);
        this.updateTotalValue();
        this.loadingHoldings = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading holdings:', err);
        this.error = 'Failed to load holdings';
        this.loadingHoldings = false;
        this.cdr.detectChanges();
      }
    });

    // Load orders
    this.api.getOrders(this.accountId).subscribe({
      next: (orders) => {
        this.orders = orders;
        this.successfulOrders = orders.filter((order) => {
          const status = order.status as string;
          return status === 'FILLED' || status === 'EXECUTED';
        });
        this.loadingOrders = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading orders:', err);
        this.error = 'Failed to load orders';
        this.loadingOrders = false;
        this.cdr.detectChanges();
      }
    });

    // Successful orders represent the trades that changed the cash balance.
    this.loadingTrades = false;

    // Market trend data is not exposed by the current backend contract.
    this.symbolTrends = [];
  }

  // Form Validation Methods
  validateOrderForm(): boolean {
    this.orderFormErrors = {};

    if (!this.orderForm.symbol || this.orderForm.symbol.trim() === '') {
      this.orderFormErrors['symbol'] = 'Symbol is required';
    } else if (!/^[A-Z]{1,5}$/.test(this.orderForm.symbol.toUpperCase())) {
      this.orderFormErrors['symbol'] = 'Invalid symbol format (e.g., AAPL, MSFT)';
    }

    if (!this.orderForm.quantity || this.orderForm.quantity <= 0) {
      this.orderFormErrors['quantity'] = 'Quantity must be greater than 0';
    } else if (!Number.isInteger(this.orderForm.quantity)) {
      this.orderFormErrors['quantity'] = 'Quantity must be a whole number';
    }

    return Object.keys(this.orderFormErrors).length === 0;
  }

  isFormValid(): boolean {
    return this.validateOrderForm();
  }

  placeOrder() {
    if (!this.validateOrderForm()) {
      return;
    }

    this.loading = true;
    this.api.createOrder(this.accountId, {
      symbol: this.orderForm.symbol.toUpperCase(),
      side: this.orderForm.side as 'BUY' | 'SELL',
      quantity: this.orderForm.quantity,
      status: 'PENDING'
    }).subscribe({
      next: (order) => {
        this.loading = false;
        this.resetOrderForm();
        if (order.status === 'REJECTED') {
          this.orderFormErrors = {
            submit: 'Order rejected: insufficient funds or another trading rule prevented this order.'
          };
          this.loadDashboardData();
          return;
        }
        this.orderFormErrors = {};
        this.orderPlaced = true;
        this.loadDashboardData(); // Refresh orders list
      },
      error: (err) => {
        console.error('Error placing order:', err);
        this.resetOrderForm();
        this.orderFormErrors['submit'] = 'Failed to place order. Please try again.';
        this.loading = false;
      }
    });
  }

  resetOrderForm(): void {
    this.orderForm = { symbol: '', side: 'BUY', quantity: 0 };
  }

  // Cancel Order
  cancelOrder(orderId: string) {
    if (!confirm('Are you sure you want to cancel this order?')) {
      return;
    }

    this.cancelingOrderId = orderId;
    this.api.cancelOrder(this.accountId, orderId).subscribe({
      next: () => {
        this.cancelingOrderId = null;
        this.loadDashboardData(); // Refresh orders list
      },
      error: (err) => {
        console.error('Error canceling order:', err);
        alert('Failed to cancel order');
        this.cancelingOrderId = null;
      }
    });
  }

  // Refresh Data
  refreshData() {
    this.loadDashboardData();
  }

  // Helper Methods
  getOrderStatusClass(status: string): string {
    switch (status) {
      case 'FILLED':
        return 'status-filled';
      case 'PENDING':
        return 'status-pending';
      case 'CANCELLED':
        return 'status-cancelled';
      case 'REJECTED':
        return 'status-rejected';
      default:
        return '';
    }
  }

  canCancelOrder(order: Order): boolean {
    return order.status === 'PENDING';
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  updateTotalValue(): void {
    const holdingsValue = this.portfolio.holdings.reduce((sum, h) => {
      const price = h.currentPrice || h.averageCost;
      return sum + (h.quantity * price);
    }, 0);
    this.portfolio.totalValue = this.portfolio.cash + holdingsValue;
  }

  getTotalValue(): number {
    return this.portfolio.totalValue;
  }

  // Get sector for symbol
  getSymbolSector(symbol: string): string {
    const techSymbols = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'META', 'NFLX', 'TSLA', 'NVIDIA', 'AMD', 'INTEL'];
    const financeSymbols = ['JPM', 'BAC', 'GS', 'MS', 'AXP', 'PYPL', 'SQ', 'CIB', 'USB', 'WFC'];
    const healthSymbols = ['JNJ', 'PFE', 'MRK', 'ABBV', 'LLY', 'AbbVie', 'UNH', 'CVS', 'MCK', 'TMDX'];
    const energySymbols = ['XOM', 'CVX', 'MPC', 'PSX', 'COP', 'SLB', 'EOG', 'MUR', 'FANG', 'DVN'];
    
    if (techSymbols.includes(symbol)) return 'Technology';
    if (financeSymbols.includes(symbol)) return 'Finance';
    if (healthSymbols.includes(symbol)) return 'Healthcare';
    if (energySymbols.includes(symbol)) return 'Energy';
    return 'Other';
  }

  getBuySellDeltaPercent(trend: SymbolTrend): number {
    const total = trend.buyCount + trend.sellCount;
    if (!total) return 0;
    return ((trend.buyCount - trend.sellCount) / total) * 100;
  }

  getHeatmapColor(trend: SymbolTrend): string {
    const delta = this.getBuySellDeltaPercent(trend);
    const intensity = Math.min(Math.abs(delta), 100);

    if (delta >= 0) {
      const lightness = 42 - intensity * 0.12;
      return `hsl(148 62% ${Math.max(lightness, 28)}%)`;
    }

    const lightness = 44 - intensity * 0.12;
    return `hsl(2 72% ${Math.max(lightness, 30)}%)`;
  }

  getSizeMultiplier(trend: SymbolTrend): number {
    const volumeWeight = Math.min(trend.totalVolume / 550, 2.6);
    const popularityWeight = trend.popularityScore / 100;
    return Number((0.9 + volumeWeight + popularityWeight).toFixed(2));
  }

  getHeatmapCellSize(trend: SymbolTrend): number {
    return Math.max(54, Math.min(126, 44 + trend.totalVolume * 0.06 + trend.popularityScore * 0.3));
  }

  getHeatmapTitle(trend: SymbolTrend): string {
    const delta = this.getBuySellDeltaPercent(trend);
    const direction = delta >= 0 ? 'Bullish flow' : 'Bearish flow';
    return `${trend.symbol} | ${direction} ${delta.toFixed(1)}% | Volume ${trend.totalVolume}`;
  }
}
