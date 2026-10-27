import { HistorySortKey, HistorySortDirection, sortTradeHistory } from './trade-history-sort';
import { UI_COMPONENTS } from '../../../shared/ui';
import { Component, OnDestroy, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { Subject, Subscription, from, of, timer, takeUntil, forkJoin } from 'rxjs';
import { catchError, timeout } from 'rxjs/operators';
import { concatMap, toArray } from 'rxjs/operators';
import { AuthService, User } from '../../../core/services/auth.service';
import { ApiService, TradeDisputeSummary, Holding, MarketInstrument, Order, Trade, SymbolTrend, TradingAccount, WalletBankAccount as ApiWalletBankAccount, WalletActivity as ApiWalletActivity } from '../../../core/services/api.service';
import { HeatmapStock, StockHeatmapComponent } from '../../../shared/heatmap/stock-heatmap.component';
import { FinnhubNewsArticle, FinnhubNewsCategory, FinnhubNewsService } from '../../../core/services/finnhub-news.service';
import { PortfolioHistoryPoint, PortfolioHistoryRange, PortfolioService } from '../../../core/services/portfolio.service';
import { PortfolioAccountState, PortfolioStateService } from '../../../core/services/portfolio-state.service';
import { AssetAllocationComponent } from '../../../shared/asset-allocation/asset-allocation.component';
import { TradeDisputesComponent } from './trade-disputes.component';
import { HelpComponent } from './help/help.component';
import { TradingviewWidgetComponent } from '../../../shared/tradingview/tradingview-widget.component';

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
  imports: [...UI_COMPONENTS, CommonModule, FormsModule, StockHeatmapComponent, AssetAllocationComponent, HelpComponent, TradingviewWidgetComponent, TradeDisputesComponent],

  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  quoteCurrency = 'UNKNOWN';
  get currencyLabel(): string { return this.accountState?.currency || 'UNKNOWN'; }

  private static readonly LARGE_CAP_FALLBACK_ORDER = [
    'AAPL', 'MSFT', 'NVDA', 'AMZN', 'GOOGL', 'META', 'TSLA', 'BRK.B', 'BRK-B', 'BRK/B',
    'AVGO', 'LLY', 'JPM', 'V', 'WMT', 'MA', 'XOM', 'UNH', 'COST', 'ORCL',
    'NFLX', 'JNJ', 'HD', 'PG', 'ABBV', 'BAC', 'KO', 'CRM', 'CVX', 'MRK',
    'WFC', 'AMD', 'PEP', 'TMO', 'DIS', 'MCD', 'CSCO', 'ABT', 'INTU', 'LIN',
    'ACN', 'DHR', 'ADBE', 'TXN', 'QCOM', 'VZ', 'PM', 'MS', 'CAT', 'IBM'
  ];

  private static readonly LARGE_CAP_FALLBACK_SCORE = new Map(
    DashboardComponent.LARGE_CAP_FALLBACK_ORDER.map((symbol, index) => [
      symbol.toUpperCase(),
      DashboardComponent.LARGE_CAP_FALLBACK_ORDER.length - index
    ])
  );

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
  disputeSummaries: Record<string, TradeDisputeSummary> | null = null;
  disputeSummaryError = '';
  disputeDialogOpen = false;
  readonly noDisputeSummary: TradeDisputeSummary = {total: 0, active: 0, resolved: 0, status: 'NONE'};
  trackOrder(_: number, order: Order) { return order.orderId; }

  // Investor Dashboard Data
  portfolio = {
    cash: 0,
    holdings: [] as Holding[],
    totalValue: 0
  };

  orders: Order[] = [];
  historySortKey: HistorySortKey = 'date';
  historySortDirection: HistorySortDirection = 'desc';
  readonly historyColumns: {key: HistorySortKey; label: string}[] = [
    {key: 'orderId', label: 'Order ID'}, {key: 'date', label: 'Date'},
    {key: 'symbol', label: 'Symbol'}, {key: 'side', label: 'Side'},
    {key: 'status', label: 'Status'}, {key: 'quantity', label: 'Quantity'},
    {key: 'price', label: 'Order price'}, {key: 'executedNotional', label: 'Executed value'},
    {key: 'disputes', label: 'Dispute tickets'}
  ];
  get sortedOrders(): Order[] {
    return sortTradeHistory(this.orders, this.historySortKey, this.historySortDirection, this.disputeSummaries);
  }
  sortHistory(key: HistorySortKey): void {
    this.historySortDirection = this.historySortKey === key && this.historySortDirection === 'asc' ? 'desc' : 'asc';
    this.historySortKey = key;
  }
  historySortAria(key: HistorySortKey): 'ascending' | 'descending' | 'none' {
    return this.historySortKey === key ? this.historySortDirection === 'asc' ? 'ascending' : 'descending' : 'none';
  }
  historySortLabel(key: HistorySortKey, label: string): string {
    const next = this.historySortKey === key && this.historySortDirection === 'asc' ? 'descending' : 'ascending';
    return `Sort by ${key === 'disputes' ? 'dispute status' : label}, ${next}`;
  }
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
  walletLoadError = '';
  walletSuccessMessage = '';
  submittingBankAccount = false;
  submittingWalletTransaction = false;

  orderForm = { symbol: '', side: 'BUY', quantity: 1 };
  orderFormErrors: { [key: string]: string } = {};

  // Trade view state
  tradeSearch = '';
  tradeSearchError = '';
  selectedTicker = '';
  selectedTradeInstrument: MarketInstrument | null = null;
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
  private walletLoadSubscription?: Subscription;
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

    this.finnhubNews.availability$.pipe(takeUntil(this.destroy$)).subscribe(status => {
      if (status === 'unavailable') this.newsError = 'Market news is temporarily unavailable. Please retry shortly.';
      if (status === 'available') this.newsError = '';
      this.cdr.markForCheck();
    });
    this.portfolioState.persistenceError$.pipe(takeUntil(this.destroy$)).subscribe(message => {
      this.persistenceMessage = message;
      if (message) this.error = message;
    });
    timer(60000, 60000).pipe(takeUntil(this.destroy$)).subscribe(() => {
      if (this.activeView === 'graphs') this.loadMarketHeatmap();
      if (this.activeView === 'history' && !this.disputeDialogOpen && !this.loadingOrders) this.loadOrderHistory();
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
      this.walletLoadSubscription?.unsubscribe();
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
  }

  loadMarketHeatmap(): void {
    if (this.loadingMarketHeatmap) return;
    this.loadingMarketHeatmap = true;
    this.marketHeatmapError = '';
    this.api.getMarketInstruments().pipe(timeout(10000), takeUntil(this.destroy$)).subscribe({
      next: ({ symbols }) => {
        const instruments = symbols;
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
            }, []).sort((left, right) => this.getMarketCapRank(right) - this.getMarketCapRank(left)).slice(0, 50);
            if (!this.marketStocks.length) this.marketHeatmapError = 'Market prices or percentage changes are unavailable.';
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
    if (!quote || !Number.isFinite(quote.price) || quote.price <= 0 || !Number.isFinite(quote.changePercent)) {
      return null;
    }
    const instrumentWithOptionalCap = instrument as MarketInstrument & {
      marketCap?: number;
      market_cap?: number;
      marketCapitalization?: number;
      sizeValue?: number;
    };
    const marketCap = this.toPositiveNumber(
      instrumentWithOptionalCap.marketCap ??
      instrumentWithOptionalCap.market_cap ??
      instrumentWithOptionalCap.marketCapitalization
    );
    const sizeValue = this.toPositiveNumber(instrumentWithOptionalCap.sizeValue) ?? this.getFallbackCapScore(instrument.symbol);

    return {
      ticker: instrument.symbol,
      companyName: instrument.name,
      exchange: instrument.exchange,
      currentPrice: quote.price,
      dailyChange: quote.changePercent ?? 0,
      sector: instrument.type,
      marketCap: marketCap ?? undefined,
      sizeValue: sizeValue ?? undefined,
    };
  }

  private getMarketCapRank(stock: HeatmapStock): number {
    const rank = stock.marketCap ?? stock.sizeValue;
    return Number.isFinite(rank) ? rank as number : 0;
  }

  private getFallbackCapScore(symbol: string): number {
    return DashboardComponent.LARGE_CAP_FALLBACK_SCORE.get(symbol.trim().toUpperCase()) ?? 0;
  }

  private toPositiveNumber(value: unknown): number | null {
    if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
      return null;
    }
    return value;
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

  sanitizeBankNumber(event: Event, field: 'routingNumber' | 'accountNumber'): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) return;
    const maxLength = field === 'routingNumber' ? 9 : 17;
    const digits = input.value.replace(/[^0-9]/g, '').slice(0, maxLength);
    input.value = digits;
    this.bankAccountForm[field] = digits;
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
    if (!/^[A-Z][A-Z0-9.-]{0,9}$/.test(symbol)) {
      this.tradeSearchError = 'Enter a valid ticker (e.g., AAPL, MSFT)';
      return;
    }
    this.tradeSearchError = '';
    this.selectedTicker = symbol;
    this.selectedTradeInstrument = this.marketInstruments.find(
      (instrument) => instrument.symbol.toUpperCase() === symbol
    ) ?? null;
    this.orderForm = { symbol, side: 'BUY', quantity: 1 };
    this.orderFormErrors = {};
    this.orderPlaced = false;
    this.showOrderModal = true;
    this.loadQuote(symbol);
  }

  get ownedTradeShares(): number | null {
    if (!this.accountState) return null;
    return this.portfolio.holdings.find(
      holding => holding.symbol.toUpperCase() === this.selectedTicker.toUpperCase()
    )?.quantity ?? 0;
  }

  get ownedTradeValue(): number | null {
    const shares = this.ownedTradeShares;
    if (shares === null) return null;
    if (shares === 0) return 0;
    if (this.loadingQuote || this.quoteError || this.currentPrice === null ||
        !Number.isFinite(this.currentPrice) || this.currentPrice < 0) return null;
    return shares * this.currentPrice;
  }

  getTradeChartSymbol(): string {
    const exchange = this.selectedTradeInstrument?.exchange?.trim().toUpperCase();
    const prefix = exchange === 'XNAS' ? 'NASDAQ' : exchange === 'XNYS' ? 'NYSE' : exchange;
    return prefix === 'NASDAQ' || prefix === 'NYSE' || prefix === 'AMEX'
      ? `${prefix}:${this.selectedTicker}`
      : this.selectedTicker;
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
        this.quoteCurrency = quote.currency || 'UNKNOWN';
        this.currentChange = quote.change ?? null;
        this.currentChangePercent = quote.changePercent ?? null;
        this.loadingQuote = false;
        this.onOrderFormChange();
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
    this.selectedTradeInstrument = null;
  }

  backToPortfolio(): void {
    this.closeOrderModal();
    this.tradeSearch = '';
    this.setView('portfolio');
  }

  setView(view: 'portfolio' | 'trade' | 'history' | 'wallet' | 'graphs' | 'news' | 'help'): void {
    this.activeView = view;
    if (view === 'graphs') this.loadMarketHeatmap();
    if (view === 'history') this.loadOrderHistory();
    if (view === 'wallet') this.loadWalletData();
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
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Finnhub News Error:', error);
        console.error('Error loading Finnhub news:', error);
        this.newsError = 'Live news is temporarily unavailable. Please try again shortly.';
        this.loadingNews = false;
        this.cdr.markForCheck();
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
    if (!/^[A-Z][A-Z0-9.-]{0,9}$/.test(symbol)) {
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
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Finnhub News Error:', error);
        console.error(`Error loading Finnhub news for ${symbol}:`, error);
        this.newsError = `News for ${symbol} is temporarily unavailable.`;
        this.loadingNews = false;
        this.cdr.markForCheck();
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

  handleNewsImageError(article: FinnhubNewsArticle): void {
    this.failedNewsImages.add(article.id);
    this.cdr.detectChanges();
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
    this.disputeSummaries = null; this.disputeSummaryError = ''; this.disputeDialogOpen = false;
    this.bankAccounts = [];
    this.walletActivity = [];
    this.walletLoadError = '';
    this.closeWalletModal();
  }

  loadOrderHistory(): void {
    const accountId = this.accountId;
    this.loadingOrders = true;
    this.disputeSummaryError = '';
    forkJoin({orders: this.api.getOrders(accountId), summaries: this.api.getTradeDisputeSummary(accountId).pipe(catchError(() => { this.disputeSummaryError = 'Ticket statuses could not be loaded. Refresh to retry.'; return of(null); }))}).pipe(timeout(10000), takeUntil(this.destroy$)).subscribe({
      next: result => { if (this.accountId !== accountId) return; this.orders = result.orders; this.disputeSummaries = result.summaries; this.loadingOrders = false; this.cdr.markForCheck(); },
      error: () => { if (this.accountId !== accountId) return; this.error = 'Order history could not be loaded. Refresh to retry.'; this.loadingOrders = false; this.cdr.markForCheck(); }
    });
  }

  loadDashboardData(recordTrade = false) {
    this.loadWalletData();
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

  
  private loadWalletData(): void {
    this.walletLoadSubscription?.unsubscribe();
    this.walletLoadError = '';
    this.walletLoadSubscription = forkJoin({
      accounts: this.api.getWalletBanks(this.accountId),
      activity: this.api.getWalletActivity(this.accountId)
    }).pipe(timeout(10000), takeUntil(this.destroy$)).subscribe({
      next: ({ accounts, activity }) => {
        this.bankAccounts = accounts.map((account) => this.mapBankAccount(account));
        this.walletActivity = activity.map((item) => ({
          id: item.movementId,
          type: item.movementType === 'DEPOSIT' ? 'Deposit' : 'Withdrawal',
          amount: item.amount,
          bankNickname: this.bankAccounts.find((bank) => bank.id === item.bankId)?.nickname ?? 'Bank account',
          date: new Date(item.movementDate)
        }));
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Unable to load wallet data:', error);
        this.walletLoadError = 'Unable to load wallet data. Refresh to try again.';
        this.cdr.markForCheck();
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

  /**
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
  } */

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
    } else if (!/^[A-Z][A-Z0-9.-]{0,9}$/.test(this.orderForm.symbol.toUpperCase())) {
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
        this.orderFormErrors['submit'] = 'Order could not be confirmed. Check History before retrying; the server may have received it.';
        this.loading = false;
      }
    });
  }

  resetOrderForm(): void {
    this.orderForm = { symbol: '', side: 'BUY', quantity: 1 };
  }

  // Refresh Data
  refreshData() {
    if (this.activeView === 'history') { this.loadOrderHistory(); return; }
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
    return order.executedNotional ?? 0;
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
      ? 'var(--ui-success)'
      : 'var(--ui-danger)';
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
    return `${this.currencyLabel} ${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
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
