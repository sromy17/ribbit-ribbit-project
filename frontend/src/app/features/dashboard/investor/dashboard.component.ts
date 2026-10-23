import { UI_COMPONENTS } from '../../../shared/ui';
import { Component, OnDestroy, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { Subject, forkJoin } from 'rxjs';
import { AuthService, User } from '../../../core/services/auth.service';
import { ApiService, Holding, MarketInstrument, Order, Trade, SymbolTrend, TradingAccount, WalletBankAccount as ApiWalletBankAccount, WalletActivity as ApiWalletActivity } from '../../../core/services/api.service';

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

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [...UI_COMPONENTS, CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
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
  marketInstruments: MarketInstrument[] = [];
  loadingInstrumentCatalog = false;
  instrumentCatalogError = '';
  bankAccounts: WalletBankAccount[] = [];
  walletActivity: WalletActivity[] = [];
  showBankAccountModal = false;
  showWalletTransactionModal = false;
  walletTransactionType: 'Deposit' | 'Withdrawal' = 'Deposit';
  bankAccountForm = { nickname: '', routingNumber: '', accountNumber: '' };
  walletTransactionForm = { amount: 1, bankAccountId: 0 };
  walletFormError = '';
  walletSuccessMessage = '';
  submittingBankAccount = false;
  submittingWalletTransaction = false;

  orderForm = { symbol: '', side: 'BUY', quantity: 1 };
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
  private readonly destroy$ = new Subject<void>();

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
      this.loadInstrumentCatalog();
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
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

  get filteredTradeTickers(): MarketInstrument[] {
    const search = this.tradeSearch.trim().toUpperCase();
    if (!search) {
      return [];
    }
    return this.marketInstruments
      .filter((instrument) =>
        instrument.symbol.startsWith(search) ||
        instrument.name.toUpperCase().startsWith(search) ||
        instrument.name.toUpperCase().includes(` ${search}`)
      )
      .sort((left, right) =>
        Number(right.symbol.startsWith(search)) - Number(left.symbol.startsWith(search)) ||
        left.symbol.localeCompare(right.symbol)
      )
      .slice(0, 10);
  }

  loadInstrumentCatalog(): void {
    if (this.marketInstruments.length > 0 || this.loadingInstrumentCatalog) {
      return;
    }
    this.loadingInstrumentCatalog = true;
    this.instrumentCatalogError = '';
    this.api.getMarketInstruments().subscribe({
      next: (response) => {
        this.marketInstruments = response.symbols ?? [];
        this.loadingInstrumentCatalog = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading local instrument catalog:', err);
        this.instrumentCatalogError = 'Ticker search is currently unavailable.';
        this.loadingInstrumentCatalog = false;
        this.cdr.detectChanges();
      }
    });
  }

  onTradeSearchChange(): void {
    this.tradeSearchError = '';
  }

  blockInvalidNumberKey(event: KeyboardEvent, allowDecimal: boolean): void {
    if (event.ctrlKey || event.metaKey || event.altKey || event.key.length !== 1) {
      return;
    }

    const isDigit = event.key >= '0' && event.key <= '9';
    const isDecimal = allowDecimal && event.key === '.' &&
      !(event.target as HTMLInputElement).value.includes('.');

    if (!isDigit && !isDecimal) {
      event.preventDefault();
    }
  }

  sanitizeNumberPaste(event: ClipboardEvent, allowDecimal: boolean): void {
    const pastedText = event.clipboardData?.getData('text') ?? '';
    const pattern = allowDecimal ? /[^0-9.]/g : /[^0-9]/g;
    const sanitizedText = pastedText.replace(pattern, '');

    if (sanitizedText !== pastedText) {
      event.preventDefault();
      const input = event.target as HTMLInputElement;
      const start = input.selectionStart ?? input.value.length;
      const end = input.selectionEnd ?? input.value.length;
      input.setRangeText(sanitizedText, start, end, 'end');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }

  selectTicker(symbolOverride?: string): void {
    const symbol = (symbolOverride ?? this.tradeSearch).trim().toUpperCase();
    if (!/^[A-Z]{1,5}$/.test(symbol)) {
      this.tradeSearchError = 'Enter a valid ticker (e.g., AAPL, MSFT)';
      return;
    }
    this.tradeSearchError = '';
    this.selectedTicker = symbol;
    this.orderForm = { symbol, side: 'BUY', quantity: 1 };
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
        this.onOrderFormChange();
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
    this.walletSuccessMessage = '';
    this.showBankAccountModal = true;
  }

  closeWalletModal(force = false): void {
    if (!force && (this.submittingBankAccount || this.submittingWalletTransaction)) {
      return;
    }
    this.showBankAccountModal = false;
    this.showWalletTransactionModal = false;
    this.walletFormError = '';
  }

  addBankAccount(): void {
    if (this.submittingBankAccount) {
      return;
    }
    const { nickname, routingNumber, accountNumber } = this.bankAccountForm;
    if (!nickname.trim() || !/^\d{9}$/.test(routingNumber) || !/^\d{4,17}$/.test(accountNumber)) {
      this.walletFormError = 'Enter a nickname, a 9-digit routing number, and an account number up to 17 digits.';
      return;
    }

    this.submittingBankAccount = true;
    this.api.createWalletBank(this.accountId, {
      nickname: nickname.trim(),
      routingNumber,
      accountNumber
    }).subscribe({
      next: (account) => {
        this.bankAccounts = [...this.bankAccounts, this.mapBankAccount(account)];
        this.submittingBankAccount = false;
        this.walletSuccessMessage = 'Bank account connected successfully.';
        this.closeWalletModal(true);
        this.loadWalletData();
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.walletFormError = error.status === 409
          ? 'This bank account is already connected.'
          : 'Unable to connect this bank account.';
        this.submittingBankAccount = false;
        this.cdr.detectChanges();
      }
    });
  }

  openWalletTransaction(type: 'Deposit' | 'Withdrawal'): void {
    this.walletTransactionType = type;
    this.walletTransactionForm = {
      amount: 1,
      bankAccountId: this.bankAccounts[0]?.id ?? 0
    };
    this.walletFormError = '';
    this.walletSuccessMessage = '';
    this.showWalletTransactionModal = true;
  }

  submitWalletTransaction(): void {
    if (this.submittingWalletTransaction) {
      return;
    }
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

    const request = this.walletTransactionType === 'Deposit'
      ? this.api.depositToWallet(this.accountId, bankAccount.id, amount)
      : this.api.withdrawFromWallet(this.accountId, bankAccount.id, amount);
    this.submittingWalletTransaction = true;
    request.subscribe({
      next: (transaction) => {
        this.portfolio.cash = transaction.cashBalance ?? (
          this.walletTransactionType === 'Deposit' ? this.portfolio.cash + amount : this.portfolio.cash - amount
        );
        this.walletActivity = [{
          id: transaction.movementId,
          type: transaction.movementType === 'DEPOSIT' ? 'Deposit' : 'Withdrawal',
          amount: transaction.amount,
          bankNickname: bankAccount.nickname,
          date: new Date(transaction.movementDate)
        }, ...this.walletActivity];
        this.updateTotalValue();
        this.submittingWalletTransaction = false;
        this.walletSuccessMessage = `${this.walletTransactionType} completed successfully.`;
        this.closeWalletModal(true);
        this.loadWalletData();
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.walletFormError = error.status === 400
          ? 'The transaction could not be completed with the available balance.'
          : 'Unable to complete the wallet transaction.';
        this.submittingWalletTransaction = false;
        this.cdr.detectChanges();
      }
    });
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

  private loadWalletData(): void {
    forkJoin({
      accounts: this.api.getWalletBanks(this.accountId),
      activity: this.api.getWalletActivity(this.accountId)
    }).subscribe({
      next: ({ accounts, activity }) => {
        this.bankAccounts = accounts.map((account) => this.mapBankAccount(account));
        this.walletActivity = activity.map((item) => ({
          id: item.movementId,
          type: item.movementType === 'DEPOSIT' ? 'Deposit' : 'Withdrawal',
          amount: item.amount,
          bankNickname: this.bankAccounts.find((bank) => bank.id === item.bankId)?.nickname ?? 'Bank account',
          date: new Date(item.movementDate)
        }));
      },
      error: () => {
        this.walletFormError = 'Unable to load wallet data.';
      }
    });
  }

  private mapBankAccount(account: ApiWalletBankAccount): WalletBankAccount {
    return {
      id: account.bankId,
      nickname: account.accountName,
      routingNumber: account.routingNumber,
      accountNumber: account.accountNumber
    };
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
        this.onOrderFormChange();
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
        this.onOrderFormChange();
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
    this.loadWalletData();
  }

  // Form Validation Methods
  onOrderFormChange(): void {
    if (this.showOrderModal) {
      this.validateOrderForm();
    }
  }

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

    if (!this.orderFormErrors['quantity']) {
      if (this.orderForm.side === 'BUY' && this.currentPrice !== null) {
        const totalCost = this.getEstimatedTotal();
        if (totalCost > this.portfolio.cash) {
          this.orderFormErrors['quantity'] =
            `This order costs $${totalCost.toFixed(2)}, but only $${this.portfolio.cash.toFixed(2)} is available.`;
        }
      }

      if (this.orderForm.side === 'SELL') {
        const holding = this.portfolio.holdings.find(
          (item) => item.symbol.toUpperCase() === this.orderForm.symbol.toUpperCase()
        );
        const availableQuantity = holding?.quantity ?? 0;
        if (this.orderForm.quantity > availableQuantity) {
          this.orderFormErrors['quantity'] =
            `You can only sell ${availableQuantity} share${availableQuantity === 1 ? '' : 's'} of ${this.orderForm.symbol.toUpperCase()}.`;
        }
      }
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
    this.orderForm = { symbol: '', side: 'BUY', quantity: 1 };
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
