import { Component, OnDestroy, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { Subject, Subscription, from, of, timer, takeUntil } from 'rxjs';
import { catchError, timeout } from 'rxjs/operators';
import { concatMap, toArray } from 'rxjs/operators';
import { AuthService, User } from '../../../core/services/auth.service';
import { ApiService, Holding, MarketInstrument, Order, Trade, SymbolTrend, TradingAccount } from '../../../core/services/api.service';
import { HeatmapStock, StockHeatmapComponent } from '../../../shared/heatmap/stock-heatmap.component';
import { FinnhubNewsArticle, FinnhubNewsCategory, FinnhubNewsService } from '../../../core/services/finnhub-news.service';
import { PortfolioHistoryPoint, PortfolioHistoryRange, PortfolioService } from '../../../core/services/portfolio.service';
import { PortfolioAccountState, PortfolioStateService } from '../../../core/services/portfolio-state.service';
import { AssetAllocationComponent } from '../../../shared/asset-allocation/asset-allocation.component';
import { HelpComponent } from './help/help.component';

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
  imports: [CommonModule, FormsModule, StockHeatmapComponent, AssetAllocationComponent, HelpComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  activeView: 'portfolio' | 'trade' | 'history' | 'wallet' | 'graphs' | 'news' | 'help' = 'portfolio';
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
  marketStocks: HeatmapStock[] = [];
  loadingMarketHeatmap = false;
  marketHeatmapError = '';
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
  private nextWalletItemId = 1;

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
  private readonly destroy$ = new Subject<void>();
  newsArticles: FinnhubNewsArticle[] = [];
  newsCategory: FinnhubNewsCategory = 'general';
  newsSearch = '';
  loadingNews = false;
  newsError = '';
  private newsLoaded = false;
  private newsSubscription?: Subscription;
  private readonly failedNewsImages = new Set<number>();
  readonly skeletonBlocks = Array.from({ length: 8 });
  readonly portfolioRanges: PortfolioHistoryRange[] = ['1D', '1W', '1M', '3M', 'YTD', '1Y', 'ALL'];
  portfolioRange: PortfolioHistoryRange = '1M';
  portfolioHistory: PortfolioHistoryPoint[] = [];
  hoveredHistoryIndex: number | null = null;
  valuationNotice = '';
  historyPriceTicks: number[] = [];
  historyDateTicks: string[] = [];
  private historyLow = 0;
  private historyHigh = 1;
  accountState: PortfolioAccountState | null = null;
  private accountStateSubscription?: Subscription;
  private accountLoadSubscription?: Subscription;
  private persistenceMessage = '';
  private readonly chartWidth = 1000;
  private readonly chartHeight = 360;

  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private api: ApiService,
    private finnhubNews: FinnhubNewsService,
    private portfolioService: PortfolioService,
    private portfolioState: PortfolioStateService,
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

    this.portfolioState.persistenceError$.pipe(takeUntil(this.destroy$)).subscribe(message => {
      this.persistenceMessage = message;
      if (message) this.error = message;
    });
    timer(60000, 60000).pipe(takeUntil(this.destroy$)).subscribe(() => {
      if (this.activeView === 'graphs') this.loadMarketHeatmap();
      if (!this.loading && this.accountState &&
          this.accountState.history.at(-1)?.timestamp.toDateString() !== new Date().toDateString()) {
        this.loadDashboardData();
      }
    });
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const accountId = params.get('accountId');
      if (!accountId) {
        this.router.navigate(['/accounts']);
        return;
      }

      this.accountId = accountId;
      this.activeView = 'portfolio';
      this.resetDashboardData();
      this.accountStateSubscription?.unsubscribe();
      this.accountLoadSubscription?.unsubscribe();
      this.accountState = null;
      this.valuationNotice = '';
      this.portfolioHistory = [];
      this.accountStateSubscription = this.portfolioState.watchAccount(accountId)
        .pipe(takeUntil(this.destroy$)).subscribe(state => {
          if (!state) return;
          this.accountState = state;
          this.portfolio = {
            cash: state.cash,
            holdings: state.holdings.map(h => ({
              symbol: h.symbol, quantity: h.shares, averageCost: h.avgPrice, currentPrice: h.currentPrice
            })),
            totalValue: state.cash + state.holdings.reduce((sum, h) => sum + h.shares * h.currentPrice, 0)
          };
          this.orders = state.orders;
          this.error = this.persistenceMessage;
          this.valuationNotice = state.valuationWarning ?? '';
          this.refreshPortfolioHistory();
          this.cdr.markForCheck();
        });
      this.loadInstrumentCatalog();
      this.loadAccountDetails();
      this.loadAccounts();
      this.loadDashboardData();
    });
    this.loadMarketHeatmap();
  }

  loadMarketHeatmap(): void {
    if (this.loadingMarketHeatmap) return;
    this.loadingMarketHeatmap = true;
    this.marketHeatmapError = '';
    this.api.getMarketInstruments().pipe(timeout(10000), takeUntil(this.destroy$)).subscribe({
      next: ({ symbols }) => {
        const instruments = symbols.slice(0, 50);
        const batches = this.chunk(instruments, 25);
        from(batches).pipe(
          concatMap((batch) =>
            this.api.getMarketQuotes(batch.map((instrument) => instrument.symbol)).pipe(
              timeout(15000),
              catchError((error) => {
                console.warn('Skipping unavailable market-data batch:', error);
                return of([]);
              })
            )
          ),
          toArray(),
          takeUntil(this.destroy$)
        ).subscribe({
          next: (responses) => {
            const bySymbol = new Map(responses.flat().map((quote) => [quote.symbol, quote]));
            this.marketStocks = instruments.reduce<HeatmapStock[]>((stocks, instrument) => {
              const quote = bySymbol.get(instrument.symbol);
              const stock = this.toHeatmapStock(instrument, quote);
              if (stock) {
                stocks.push(stock);
              }
              return stocks;
            }, []);
            this.loadingMarketHeatmap = false;
            this.cdr.detectChanges();
          },
          error: (error) => this.handleMarketHeatmapError(error)
        });
      },
      error: (error) => this.handleMarketHeatmapError(error)
    });
  }

  private handleMarketHeatmapError(error: unknown): void {
    console.error('Error loading market heatmap:', error);
    this.marketHeatmapError = 'Market data is temporarily unavailable.';
    this.loadingMarketHeatmap = false;
    this.cdr.detectChanges();
  }

  private toHeatmapStock(
    instrument: MarketInstrument,
    quote?: { symbol: string; price: number; change?: number; changePercent?: number }
  ): HeatmapStock | null {
    if (!quote || !Number.isFinite(quote.price) || quote.price <= 0) {
      return null;
    }
    return {
      ticker: instrument.symbol,
      companyName: instrument.name,
      exchange: instrument.exchange,
      currentPrice: quote.price,
      dailyChange: quote.changePercent ?? 0,
      sector: instrument.type,
    };
  }

  private chunk<T>(items: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let index = 0; index < items.length; index += size) {
      chunks.push(items.slice(index, index + size));
    }
    return chunks;
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
    this.newsSubscription?.unsubscribe();
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
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading quote:', err);
        this.quoteError = err?.status === 503
          ? 'Market data provider is temporarily unavailable'
          : 'Unable to load the market price';
        this.loadingQuote = false;
        this.cdr.detectChanges();
      }
    });
  }

  getEstimatedNotional(): number {
    return (this.currentPrice ?? 0) * (this.orderForm.quantity || 0);
  }

  getEstimatedFee(): number {
    return PortfolioStateService.estimateFee(this.getEstimatedNotional());
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

  setView(view: 'portfolio' | 'trade' | 'history' | 'wallet' | 'graphs' | 'news' | 'help'): void {
    this.activeView = view;
    if (view === 'news' && !this.newsLoaded) {
      this.loadNews();
    }
  }

  getUserDisplayName(): string {
    const username = this.currentUser?.username?.trim() || '';
    const parts = username.split(/[._-]+/).filter(Boolean);
    return parts.length > 1
      ? parts.map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ')
      : username || 'Trader';
  }

  getUserInitials(): string {
    return this.getUserDisplayName().split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase();
  }

  loadNews(): void {
    this.newsSubscription?.unsubscribe();
    this.loadingNews = true;
    this.newsError = '';
    this.newsSubscription = this.finnhubNews.getMarketNews(this.newsCategory).subscribe({
      next: (articles) => {
        this.newsArticles = articles;
        this.newsLoaded = true;
        this.loadingNews = false;
      },
      error: (error) => {
        console.error('Finnhub News Error:', error);
        console.error('Error loading Finnhub news:', error);
        this.newsError = 'Live news is temporarily unavailable. Please try again shortly.';
        this.loadingNews = false;
      }
    });
  }

  setNewsCategory(category: FinnhubNewsCategory): void {
    this.newsSearch = '';
    this.newsCategory = category;
    this.loadNews();
  }

  searchNews(): void {
    const symbol = this.newsSearch.trim().toUpperCase();
    if (!symbol) {
      this.loadNews();
      return;
    }
    if (!/^[A-Z]{1,5}$/.test(symbol)) {
      this.newsError = 'Enter a valid ticker such as AAPL, NVDA, or TSLA.';
      return;
    }

    const today = new Date();
    const fromDate = new Date(today);
    fromDate.setDate(today.getDate() - 7);
    this.loadingNews = true;
    this.newsError = '';
    this.newsSubscription?.unsubscribe();
    this.newsSubscription = this.finnhubNews.getCompanyNews(symbol, this.toNewsDate(fromDate), this.toNewsDate(today)).subscribe({
      next: (articles) => {
        this.newsArticles = articles;
        this.loadingNews = false;
      },
      error: (error) => {
        console.error('Finnhub News Error:', error);
        console.error(`Error loading Finnhub news for ${symbol}:`, error);
        this.newsError = `News for ${symbol} is temporarily unavailable.`;
        this.loadingNews = false;
      }
    });
  }

  get filteredNewsArticles(): FinnhubNewsArticle[] {
    const query = this.newsSearch.trim().toUpperCase();
    if (!query) {
      return this.newsArticles;
    }
    return this.newsArticles.filter((article) =>
      article.headline.toUpperCase().includes(query) ||
      article.summary.toUpperCase().includes(query) ||
      (article.related ?? '').toUpperCase().split(',').includes(query)
    );
  }

  getArticleTickers(article: FinnhubNewsArticle): string[] {
    return (article.related ?? '').split(',').map((ticker) => ticker.trim().toUpperCase()).filter(Boolean).slice(0, 4);
  }

  private readonly fallbackNewsImages = [
    'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1518770660439-4636190af475?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1532938911079-1b06ac7ceec7?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1497366754035-f200968a6e72?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1559526324-593bc073d938?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1473341304170-971dccb5ac1e?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1497435334941-8c899ee9e8e9?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1518546305927-5a555bb7020d?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1560958089-b8a1929cea89?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1526778548025-fa2f459cd5c1?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1494412574643-ff11b0a5c1c3?w=600&auto=format&fit=crop&q=80',
    'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=600&auto=format&fit=crop&q=80'
  ];

  getNewsImage(article: FinnhubNewsArticle, articleIndex = 0): string {
    if (article.image && !this.failedNewsImages.has(article.id)) {
      return article.image;
    }
    return this.getNewsFallbackImage(article, articleIndex);
  }

  handleNewsImageError(article: FinnhubNewsArticle): void {
    this.failedNewsImages.add(article.id);
    this.cdr.detectChanges();
  }

  private getNewsFallbackImage(article: FinnhubNewsArticle, articleIndex: number): string {
    const imageIndex = this.getFallbackImageIndex(article, articleIndex);
    return this.fallbackNewsImages[imageIndex];
  }

  private getFallbackImageIndex(article: FinnhubNewsArticle, articleIndex: number): number {
    const headlineHash = article.headline
      .split('')
      .reduce((hash, character) => hash + character.charCodeAt(0), 0);
    let imageIndex = Math.abs(headlineHash) % this.fallbackNewsImages.length;
    if (articleIndex > 0) {
      const previousArticle = this.filteredNewsArticles[articleIndex - 1];
      if (previousArticle && (!previousArticle.image || this.failedNewsImages.has(previousArticle.id))) {
        const previousIndex = this.getFallbackImageIndex(previousArticle, articleIndex - 1);
        if (imageIndex === previousIndex) {
          imageIndex = (imageIndex + 1) % this.fallbackNewsImages.length;
        }
      }
    }
    return imageIndex;
  }

  getTimeAgo(timestamp: number): string {
    const minutes = Math.max(0, Math.floor((Date.now() - timestamp * 1000) / 60000));
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    return `${Math.floor(hours / 24)}d ago`;
  }

  openNewsTicker(ticker: string): void {
    this.setView('trade');
    this.selectTicker(ticker);
  }

  private toNewsDate(date: Date): string {
    return date.toISOString().slice(0, 10);
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
      amount: 1,
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

  loadDashboardData(recordTrade = false) {
    this.accountLoadSubscription?.unsubscribe();
    this.loadingBalance = this.loadingHoldings = this.loadingOrders = !this.accountState;
    this.error = this.persistenceMessage;
    this.accountLoadSubscription = this.portfolioState.refreshAccount(this.accountId, recordTrade)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: () => {
          this.loadingBalance = this.loadingHoldings = this.loadingOrders = false;
          this.cdr.markForCheck();
        },
        error: error => {
          console.error('Account synchronization failed:', error);
          this.error = recordTrade
            ? 'Trade confirmed, but account synchronization failed. Refresh to retrieve the updated balances; do not resubmit the trade.'
            : 'Unable to refresh account data. Any displayed cached balances may be stale.';
          this.loadingBalance = this.loadingHoldings = this.loadingOrders = false;
          this.cdr.markForCheck();
        }
      });
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
    if (this.loading || !this.validateOrderForm()) {
      return;
    }

    this.loading = true;
    const accountId = this.accountId;
    this.accountLoadSubscription?.unsubscribe();
    this.api.createOrder(accountId, {
      symbol: this.orderForm.symbol.toUpperCase(),
      side: this.orderForm.side as 'BUY' | 'SELL',
      quantity: this.orderForm.quantity,
      status: 'PENDING'
    }).subscribe({
      next: (order) => {
        this.loading = false;
        if (this.accountId !== accountId) {
          this.portfolioState.refreshAccount(accountId, order.status === 'EXECUTED' || order.status === 'FILLED').subscribe({
            error: error => {
              console.error('Previous account trade synchronization failed:', error);
              this.error = 'A trade completed on the previous account. Switch back and refresh to verify its balances.';
              this.cdr.markForCheck();
            }
          });
          return;
        }
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
        this.loadDashboardData(order.status === 'EXECUTED' || order.status === 'FILLED');
      },
      error: (err) => {
        console.error('Error placing order:', err);
        if (this.accountId !== accountId) {
          this.loading = false;
          this.error = 'The previous account order could not be confirmed. Check its History before retrying.';
          return;
        }
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
      case 'EXECUTED':
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
    this.refreshPortfolioHistory();
  }

  getTotalValue(): number {
    return this.portfolio.totalValue;
  }

  setPortfolioRange(range: PortfolioHistoryRange): void {
    this.portfolioRange = range;
    this.refreshPortfolioHistory();
  }

  getPortfolioLinePath(): string {
    return this.portfolioHistory.map((point, index) => {
      const x = this.getChartX(index);
      const y = this.getChartY(point.value);
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
    }).join(' ');
  }

  getPortfolioAreaPath(): string {
    if (this.portfolioHistory.length === 0) return '';
    return `${this.getPortfolioLinePath()} L ${this.chartWidth} ${this.chartHeight} L 0 ${this.chartHeight} Z`;
  }

  getChartX(index: number): number {
    const first = this.portfolioHistory[0]?.date.getTime() ?? 0;
    const last = this.portfolioHistory.at(-1)?.date.getTime() ?? first;
    return last === first ? index * this.chartWidth / Math.max(1, this.portfolioHistory.length - 1)
      : ((this.portfolioHistory[index].date.getTime() - first) / (last - first)) * this.chartWidth;
  }

  getChartY(value: number): number {
    return this.chartHeight - ((value - this.historyLow) / (this.historyHigh - this.historyLow)) * this.chartHeight;
  }

  getHistoryColor(): string {
    return this.portfolioHistory.length > 1 && this.portfolioHistory[this.portfolioHistory.length - 1].value >= this.portfolioHistory[0].value
      ? '#22c55e'
      : '#ef4444';
  }

  getHistoryChange(): number {
    if (this.portfolioHistory.length < 2) return 0;
    return this.portfolioHistory[this.portfolioHistory.length - 1].value - this.portfolioHistory[0].value;
  }

  getHistoryChangePercent(): number {
    const initial = this.portfolioHistory[0]?.value ?? 0;
    return initial ? (this.getHistoryChange() / initial) * 100 : 0;
  }

  formatHistoryDate(date: Date): string {
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
  }

  formatHistoryValue(value: number): string {
    return `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  formatSignedHistoryValue(value: number): string {
    return `${value >= 0 ? '+' : '-'}${this.formatHistoryValue(Math.abs(value))}`;
  }

  private refreshPortfolioHistory(): void {
    this.portfolioHistory = this.accountState
      ? this.portfolioService.getAccountHistory(this.accountState, this.portfolioRange)
      : [];
    const values = this.portfolioHistory.map(point => point.value);
    const min = values.length ? Math.min(...values) : 0;
    const max = values.length ? Math.max(...values) : 0;
    const padding = Math.max((max - min) * 0.16, max * 0.002, 0.04);
    const low = Math.max(0, min - padding);
    const rawStep = (max + padding - low) / 4;
    const magnitude = 10 ** Math.floor(Math.log10(rawStep));
    const step = Math.ceil(rawStep / magnitude) * magnitude;
    this.historyLow = Math.floor(low / step) * step;
    this.historyHigh = this.historyLow + step * 4;
    if (this.historyHigh < max) this.historyHigh += step;
    this.historyPriceTicks = Array.from({ length: 5 }, (_, i) =>
      this.historyHigh - i * (this.historyHigh - this.historyLow) / 4);
    const first = this.portfolioHistory[0]?.date.getTime();
    const last = this.portfolioHistory.at(-1)?.date.getTime();
    this.historyDateTicks = first === undefined || last === undefined ? [] :
      [0, 0.5, 1].map(fraction => {
        const date = new Date(first + fraction * (last - first));
        return last - first < 86400000
          ? date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
          : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
      });
    this.hoveredHistoryIndex = null;
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
