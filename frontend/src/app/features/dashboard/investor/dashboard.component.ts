import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService, User } from '../../../core/services/auth.service';
import { ApiService, Holding, Order, Trade, SymbolTrend, TradingAccount } from '../../../core/services/api.service';

interface WalletBankAccount {
  id: number;
  nickname: string;
  routingNumber: string;
  accountNumber: string;
}

interface WalletActivity {
  id: number;
  type: 'Deposit' | 'Withdrawal';
  amount: number;
  bankNickname: string;
  date: Date;
}

interface TradeTicker {
  symbol: string;
  name: string;
  sector: string;
  changePercent: number;
  price: number;
  chartPoints: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  activeView: 'portfolio' | 'trade' | 'history' | 'wallet' | 'help' = 'portfolio';
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
  error = '';

  // Investor Dashboard Data
  portfolio = {
    cash: 0,
    holdings: [] as Holding[],
    totalValue: 0
  };

  orders: Order[] = [];
  symbolTrends: SymbolTrend[] = [];
  readonly tradeTickers: TradeTicker[] = [
    { symbol: 'AAPL', name: 'Apple', sector: 'TECH', changePercent: 1.84, price: 227.16, chartPoints: '0,30 12,28 24,32 36,23 48,25 60,18 72,20 84,12 96,15 108,8' },
    { symbol: 'MSFT', name: 'Microsoft', sector: 'TECH', changePercent: 0.72, price: 509.45, chartPoints: '0,28 12,24 24,26 36,20 48,22 60,18 72,20 84,14 96,16 108,10' },
    { symbol: 'NVDA', name: 'NVIDIA', sector: 'CHIP', changePercent: 2.61, price: 183.22, chartPoints: '0,34 12,31 24,27 36,30 48,18 60,21 72,13 84,16 96,7 108,4' },
    { symbol: 'AMZN', name: 'Amazon', sector: 'CONSUMER', changePercent: -0.38, price: 227.03, chartPoints: '0,10 12,14 24,12 36,19 48,16 60,23 72,20 84,28 96,24 108,32' },
    { symbol: 'TSLA', name: 'Tesla', sector: 'AUTO', changePercent: -1.12, price: 438.07, chartPoints: '0,8 12,12 24,10 36,18 48,14 60,22 72,19 84,26 96,24 108,34' },
    { symbol: 'GOOGL', name: 'Alphabet', sector: 'TECH', changePercent: 1.16, price: 251.83, chartPoints: '0,29 12,26 24,28 36,22 48,24 60,16 72,18 84,13 96,15 108,8' }
  ];
  bankAccounts: WalletBankAccount[] = [];
  walletActivity: WalletActivity[] = [];
  showBankAccountModal = false;
  showWalletTransactionModal = false;
  walletTransactionType: 'Deposit' | 'Withdrawal' = 'Deposit';
  bankAccountForm = { nickname: '', routingNumber: '', accountNumber: '' };
  walletTransactionForm = { amount: 0, bankAccountId: 0 };
  walletFormError = '';
  private nextWalletItemId = 1;

  orderForm = { symbol: '', side: 'BUY', quantity: 0 };
  orderFormErrors: { [key: string]: string } = {};

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
    if (!this.currentUser) {
      return;
    }

    this.route.paramMap.subscribe((params) => {
      const accountId = params.get('accountId');
      if (!accountId) {
        this.router.navigate(['/accounts']);
        return;
      }

      this.accountId = accountId;
      this.activeView = 'portfolio';
      this.resetDashboardData();
      this.loadAccountDetails();
      this.loadAccounts();
      this.loadDashboardData();
    });
  }

  backToAccounts(): void {
    this.router.navigate(['/accounts']);
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

  get filteredTradeTickers(): TradeTicker[] {
    const search = this.tradeSearch.trim().toUpperCase();
    if (!search) {
      return this.tradeTickers;
    }
    return this.tradeTickers.filter((ticker) =>
      ticker.symbol.includes(search) || ticker.name.toUpperCase().includes(search)
    );
  }

  selectTicker(symbolOverride?: string): void {
    const symbol = (symbolOverride ?? this.tradeSearch).trim().toUpperCase();
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

  setView(view: 'portfolio' | 'trade' | 'history' | 'wallet' | 'help'): void {
    this.activeView = view;
  }

  openBankAccountModal(): void {
    this.bankAccountForm = { nickname: '', routingNumber: '', accountNumber: '' };
    this.walletFormError = '';
    this.showBankAccountModal = true;
  }

  closeWalletModal(): void {
    this.showBankAccountModal = false;
    this.showWalletTransactionModal = false;
    this.walletFormError = '';
  }

  addBankAccount(): void {
    const { nickname, routingNumber, accountNumber } = this.bankAccountForm;
    if (!nickname.trim() || !/^\d{9}$/.test(routingNumber) || !/^\d{4,17}$/.test(accountNumber)) {
      this.walletFormError = 'Enter a nickname, a 9-digit routing number, and an account number up to 17 digits.';
      return;
    }

    this.bankAccounts = [
      ...this.bankAccounts,
      {
        id: this.nextWalletItemId++,
        nickname: nickname.trim(),
        routingNumber,
        accountNumber
      }
    ];
    this.closeWalletModal();
  }

  openWalletTransaction(type: 'Deposit' | 'Withdrawal'): void {
    this.walletTransactionType = type;
    this.walletTransactionForm = {
      amount: 0,
      bankAccountId: this.bankAccounts[0]?.id ?? 0
    };
    this.walletFormError = '';
    this.showWalletTransactionModal = true;
  }

  submitWalletTransaction(): void {
    const amount = Number(this.walletTransactionForm.amount);
    const bankAccount = this.bankAccounts.find((account) => account.id === this.walletTransactionForm.bankAccountId);
    if (!bankAccount || amount <= 0) {
      this.walletFormError = 'Select a bank account and enter an amount greater than zero.';
      return;
    }
    if (this.walletTransactionType === 'Withdrawal' && amount > this.portfolio.cash) {
      this.walletFormError = 'The withdrawal amount cannot exceed the available cash balance.';
      return;
    }

    this.portfolio.cash += this.walletTransactionType === 'Deposit' ? amount : -amount;
    this.walletActivity = [{
      id: this.nextWalletItemId++,
      type: this.walletTransactionType,
      amount,
      bankNickname: bankAccount.nickname,
      date: new Date()
    }, ...this.walletActivity];
    this.updateTotalValue();
    this.closeWalletModal();
  }

  maskBankAccount(accountNumber: string): string {
    return `•••• ${accountNumber.slice(-4)}`;
  }

  maskRoutingNumber(routingNumber: string): string {
    return `••••••${routingNumber.slice(-3)}`;
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
    this.bankAccounts = [];
    this.walletActivity = [];
    this.closeWalletModal();
  }

  loadDashboardData() {
    this.loadingBalance = true;
    this.loadingHoldings = true;
    this.loadingOrders = true;
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

  getOrderTotalPrice(order: Order): number {
    return order.quantity * (order.price ?? 0);
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
