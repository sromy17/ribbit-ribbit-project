import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, concat, of, map, shareReplay, tap, timeout } from 'rxjs';
import { delay } from 'rxjs/operators';

// Component -> ApiService -> HttpClient -> API
// Data Interfaces (that match the OpenAPI Schema)

/**
 * constructor(private api: ApiService) {}

ngOnInit() {
  this.api.getOrders(accountId).subscribe(orders => {
    this.orders = orders;
  });
}
 */

export interface User {
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  role: 'investor' | 'admin' | 'analyst';
}

export interface TradingAccount {
  accountId: string;
  type: string;
  availableFunds: number;
  creationDate?: string;
}

export interface Order {
  orderId: string;
  accountId: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price?: number;
  status: 'PENDING' | 'FILLED' | 'EXECUTED' | 'REJECTED' | 'CANCELLED';
  dateRequested?: string;
  createdAtUtc?: string;
  executedQuantity?: number;
  executedNotional?: number;
  fee?: number;
  cashEffect?: number;
}

export interface ProcessOrderResponse {
  orderId: number;
  tradeId?: number;
  status: string;
  executedQuantity?: number;
  executionPrice?: number;
  fee?: number;
}

export interface Trade {
  tradeId: string;
  orderId: string;
  symbol?: string;
  executionPrice: number;
  executedQuantity: number;
  executedAt?: string;
}

export interface Holding {
  symbol: string;
  quantity: number;
  averageCost: number;
  currentPrice?: number;
}

export interface Transaction {
  transactionId: string;
  accountId: string;
  transactionType: string;
  amount: number;
  timestamp?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface CreateAccountRequest {
  userId: number;
  accountType: string;
  currency: string;
}

export interface WalletBankAccount {
  bankId: number;
  accountId: number;
  accountName: string;
  routingNumber: string;
  accountNumber: string;
  balance: number;
}

export interface WalletActivity {
  movementId: number;
  bankId: number;
  movementType: 'DEPOSIT' | 'WITHDRAWAL';
  amount: number;
  movementDate: string;
  cashBalance?: number;
}

export interface LoginResponse {
  sessionId: string;
  accessToken: string;
  expiresAt: string;
  user: User;
}

// Admin Dashboard Interfaces
export interface InvestorInfo {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  accountId: string;
  accountBalance: number;
  portfolioValue: number;
  holdingsCount: number;
  ordersCount: number;
  joinDate: string;
}

export interface PlatformStats {
  totalInvestors: number;
  totalTrades: number;
  totalOrders: number;
  totalVolume: number;
  avgPortfolioValue: number;
  topTrader: InvestorInfo;
}

export interface AdminOrder {
  requestid: number;
  accountid: number;
  instrumentid?: number;
  side: 'BUY' | 'SELL';
  quantity_requested: number;
  bidprice?: number;
  order_status: string;
  created_at_utc: string;
  correlation_id: string;
  orderId: string;
  accountId: string;
  symbol: string;
  quantity: number;
  price: number;
  status: string;
  dateRequested: string;
}

export interface AdminOrderPage {
  items: AdminOrder[];
  page: number;
  size: number;
  total: number;
  from: string;
  to: string;
  asOf?: string;
}

export interface ReportingStatus {
  stale?: boolean;
  ageSeconds?: number;
  run_id?: string;
  status: string;
  source_watermark?: string;
  started_at?: string;
  published_at?: string;
  asOf?: string;
}

export interface DisputeCase {
  caseId: number;
  transactionType: string;
  transactionReference: string;
  title: string;
  reason: string;
  status: string;
  customerUserId: number;
  accountId: number;
  orderId?: number;
  feeRelated?: boolean;
  assignedStaffUserId?: number;
  resolutionReason?: string;
  version: number;
  createdAtUtc: string;
  updatedAtUtc: string;
}

export interface TradeDisputeSummary { total: number; active: number; resolved: number; status: 'NONE' | 'UNDER_REVIEW' | 'RESOLVED'; }
export type CustomerDispute = Pick<DisputeCase, 'caseId' | 'orderId' | 'title' | 'reason' | 'status' | 'feeRelated' | 'resolutionReason' | 'createdAtUtc' | 'updatedAtUtc'>;
export interface DisputeSubmission { transactionType: 'TRADE'; transactionId: string; title: string; reason: string; feeRelated: boolean; idempotencyKey: string; }

// Analyst Dashboard Interfaces
export interface SymbolTrend {
  symbol: string;
  tradeCount: number;
  totalVolume: number;
  avgPrice: number;
  buyCount: number;
  sellCount: number;
  popularityScore: number; // 0-100
}

export interface MarketTrend {
  date: string;
  topSymbol: string;
  tradeCount: number;
  totalVolume: number;
}

export interface MarketInstrument {
  symbol: string;
  name: string;
  type: string;
  exchange: string;
  currency: string;
}

// API Service

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  getTradeDisputeSummary(accountId: string): Observable<Record<string, TradeDisputeSummary>> {
    return this.http.get<Record<string, TradeDisputeSummary>>(`/api/accounts/${accountId}/disputes/summary`);
  }
  getCustomerDisputes(accountId: string, orderId: string, page = 0): Observable<{items: CustomerDispute[]; total: number}> {
    return this.http.get<{items: CustomerDispute[]; total: number}>(`/api/accounts/${accountId}/orders/${orderId}/disputes`, {params: {page, size: 10}});
  }
  submitTradeDispute(body: DisputeSubmission): Observable<CustomerDispute> {
    return this.http.post<CustomerDispute>('/api/disputes', body);
  }
  public static readonly REFRESH_INTERVAL_MS = 60000;
  private apiUrl = '/api';
  private useMockData = false;
  private readonly marketQuoteCache = new Map<string, { symbol: string; price: number; change?: number; changePercent?: number; currency?: string }>();
  private readonly marketQuoteRequests = new Map<string, Observable<Array<{ symbol: string; price: number; change?: number; changePercent?: number; currency?: string }>>>();
  private readonly marketQuoteRequestTimes = new Map<string, number>();
  private readonly marketQuoteStorageKey = 'leap-trader:market-quotes';
  private marketQuoteCacheRestored = false;

  constructor(private http: HttpClient) {}

  // Authentication

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`/auth/login`, request);
  }

  logout(): Observable<any> {
    return this.http.post(`/auth/logout`, {});
  }

  register(request: RegisterRequest): Observable<{ userId: number; username: string }> {
    return this.http.post<{ userId: number; username: string }>(`/auth/register`, request);
  }

  // Accounts

  getAccountDetails(accountId: string): Observable<TradingAccount> {
    return this.http.get<TradingAccount>(`${this.apiUrl}/accounts/${accountId}`);
  }

  getAccountsForUser(userId: string): Observable<TradingAccount[]> {
    return this.http.get<TradingAccount | TradingAccount[]>(`${this.apiUrl}/accounts/user/${userId}`).pipe(
      map((response) => (Array.isArray(response) ? response : [response]).map(account => ({...account, accountId: String(account.accountId)})))
    );
  }

  createAccount(request: CreateAccountRequest): Observable<TradingAccount> {
    return this.http.post<TradingAccount>(`${this.apiUrl}/accounts`, request);
  }

  getAccountBalance(accountId: string): Observable<{ accountId: string; cashBalance: number; currency?: string }> {
    return this.http.get<{ accountId: string; cashBalance: number; currency?: string }>(
      `${this.apiUrl}/accounts/${accountId}/balance`
    );
  }

  updateAccountBalance(accountId: string, amount: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/accounts/${accountId}/balance`, { cashBalance: amount });
  }

  getWalletBanks(accountId: string): Observable<WalletBankAccount[]> {
    return this.http.get<WalletBankAccount[]>(`${this.apiUrl}/accounts/${accountId}/wallet/banks`);
  }

  createWalletBank(accountId: string, request: {
    nickname: string;
    routingNumber: string;
    accountNumber: string;
  }): Observable<WalletBankAccount> {
    return this.http.post<WalletBankAccount>(
      `${this.apiUrl}/accounts/${accountId}/wallet/banks`,
      request
    );
  }

  getWalletActivity(accountId: string): Observable<WalletActivity[]> {
    return this.http.get<WalletActivity[]>(`${this.apiUrl}/accounts/${accountId}/wallet/activity`);
  }

  depositToWallet(accountId: string, bankId: number, amount: number): Observable<WalletActivity> {
    return this.http.post<WalletActivity>(
      `${this.apiUrl}/accounts/${accountId}/wallet/deposit`,
      { bankId, amount }
    );
  }

  withdrawFromWallet(accountId: string, bankId: number, amount: number): Observable<WalletActivity> {
    return this.http.post<WalletActivity>(
      `${this.apiUrl}/accounts/${accountId}/wallet/withdraw`,
      { bankId, amount }
    );
  }

  // Holdings

  getHoldings(accountId: string): Observable<Holding[]> {
    return this.http.get<Holding[]>(`${this.apiUrl}/accounts/${accountId}/holdings`);
  }

  // Orders

  getOrders(accountId: string): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.apiUrl}/accounts/${accountId}/orders`);
  }

  createOrder(accountId: string, order: Omit<Order, 'orderId' | 'accountId' | 'dateRequested'>): Observable<ProcessOrderResponse> {
    return this.http.post<ProcessOrderResponse>(`${this.apiUrl}/accounts/${accountId}/orders`, {
      ticker: order.symbol,
      side: order.side,
      quantity: order.quantity
    });
  }

  cancelOrder(accountId: string, orderId: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/accounts/${accountId}/orders/${orderId}/status`);
  }

  // Market data

  getQuote(symbol: string): Observable<{ symbol: string; price: number; change?: number; changePercent?: number; currency?: string }> {
    return this.http.get<{ symbol: string; price: number; change?: number; changePercent?: number; currency?: string }>(`/api/market/quotes/${encodeURIComponent(symbol)}`).pipe(timeout(10000));
  }

  getMarketQuotes(symbols: string[]): Observable<Array<{ symbol: string; price: number; change?: number; changePercent?: number; currency?: string }>> {
    const normalizedSymbols = [...new Set(symbols.map((symbol) => symbol.toUpperCase()))];
    const cacheKey = normalizedSymbols.slice().sort().join(',');
    const existingRequest = this.marketQuoteRequests.get(cacheKey);
    const requestTime = this.marketQuoteRequestTimes.get(cacheKey) ?? 0;
    if (existingRequest && Date.now() - requestTime < ApiService.REFRESH_INTERVAL_MS) {
      return existingRequest;
    }
    this.marketQuoteRequests.delete(cacheKey);
    this.restoreMarketQuoteCache();
    const cachedQuotes = normalizedSymbols
      .map((symbol) => this.marketQuoteCache.get(symbol))
      .filter((quote): quote is { symbol: string; price: number; change?: number; changePercent?: number; currency?: string } => !!quote);
    const networkRequest = this.http.get<Array<{ symbol: string; price: number; change?: number; changePercent?: number; currency?: string }>>(
      `${this.apiUrl}/market/quotes`,
      { params: { symbols: normalizedSymbols.join(',') } }
    ).pipe(
      timeout(10000),
      tap((quotes) => {
        quotes.forEach((quote) => this.marketQuoteCache.set(quote.symbol.toUpperCase(), quote));
        this.persistMarketQuoteCache();
      })
    );
    const request = concat(
      cachedQuotes.length > 0 ? of(cachedQuotes) : of([]),
      networkRequest
    ).pipe(shareReplay({ bufferSize: 1, refCount: true }));
    this.marketQuoteRequests.set(cacheKey, request);
    this.marketQuoteRequestTimes.set(cacheKey, Date.now());
    return request;
  }

  private restoreMarketQuoteCache(): void {
    if (this.marketQuoteCacheRestored) return;
    this.marketQuoteCacheRestored = true;
    try {
      const raw = localStorage.getItem(this.marketQuoteStorageKey);
      if (!raw) return;
      const parsed = JSON.parse(raw) as Array<{ symbol: string; price: number; change?: number; changePercent?: number; currency?: string }>;
      if (Array.isArray(parsed)) {
        parsed.forEach((quote) => {
          if (quote?.symbol && Number.isFinite(quote.price)) {
            this.marketQuoteCache.set(quote.symbol.toUpperCase(), quote);
          }
        });
      }
    } catch {
      // Local storage is an optional cache; the API remains the source of truth.
    }
  }

  private persistMarketQuoteCache(): void {
    try {
      localStorage.setItem(this.marketQuoteStorageKey, JSON.stringify([...this.marketQuoteCache.values()]));
    } catch {
      // Local storage is an optional cache; the API remains the source of truth.
    }
  }

  getMarketInstruments(): Observable<{ symbols: MarketInstrument[] }> {
    return this.http.get<{ symbols: MarketInstrument[] }>('/data/us-v1.json');
  }

  // Trades

  getTrades(accountId: string): Observable<Trade[]> {
    return this.http.get<Trade[]>(`${this.apiUrl}/accounts/${accountId}/trades`);
  }

  // Admin Dashboard APIs

  getAllInvestors(): Observable<InvestorInfo[]> {
    return this.http.get<InvestorInfo[]>(`${this.apiUrl}/admin/investors`);
  }

  getAllTrades(): Observable<Trade[]> {
    if (this.useMockData) {
      return of(this.mockGetAllTrades()).pipe(delay(500));
    }
    return this.http.get<Trade[]>(`${this.apiUrl}/admin/trades`);
  }

  getAllOrders(): Observable<Order[]> {
    if (this.useMockData) {
      return of(this.mockGetAllOrders()).pipe(delay(500));
    }
    return this.http.get<Order[]>(`${this.apiUrl}/admin/orders`);
  }

  getAdminOrders(from: string, to: string, page = 0, size = 25, status = '', symbol = ''): Observable<AdminOrderPage> {
    const params: Record<string, string | number> = { from, to, page, size };
    if (status) params['status'] = status;
    if (symbol) params['symbol'] = symbol;
    return this.http.get<AdminOrderPage>(`${this.apiUrl}/admin/orders`, { params: params as any });
  }

  getAdminReportingStatus(): Observable<ReportingStatus> {
    return this.http.get<ReportingStatus>(`${this.apiUrl}/admin/reporting/status`);
  }

  getAdminActivity(from: string, to: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.apiUrl}/admin/reports/trading-activity`, { params: { from, to } });
  }

  getAdminDisputes(status = ''): Observable<DisputeCase[]> {
    const params: Record<string, string> = {};
    if (status) params['status'] = status;
    return this.http.get<DisputeCase[]>(`${this.apiUrl}/admin/disputes`, { params });
  }

  claimDispute(caseId: number, version: number): Observable<DisputeCase> {
    return this.http.post<DisputeCase>(`${this.apiUrl}/admin/disputes/${caseId}/claim`, { expectedVersion: version });
  }

  getAdminCasePage(status: string, page: number): Observable<{items: DisputeCase[]; total: number; asOf: string}> {
    return this.http.get<{items: DisputeCase[]; total: number; asOf: string}>(`${this.apiUrl}/admin/disputes/page`, {params: {status, page, size: 25}});
  }
  getAdminCase(id: number): Observable<DisputeCase> {
    return this.http.get<DisputeCase>(`${this.apiUrl}/admin/disputes/${id}`);
  }
  getAdminCaseEvents(id: number): Observable<Record<string, any>[]> {
    return this.http.get<Record<string, any>[]>(`${this.apiUrl}/admin/disputes/${id}/events`);
  }
  caseAction(id: number, action: string, body: Record<string, unknown>): Observable<DisputeCase> {
    return this.http.post<DisputeCase>(`${this.apiUrl}/admin/disputes/${id}/${action}`, body);
  }
  investigate(id: number): Observable<Record<string, any>> {
    return this.http.get<Record<string, any>>(`${this.apiUrl}/admin/investigations/${id}`);
  }
  lookupUser(id: number): Observable<{userId: number; username: string}> {
    return this.http.get<{userId: number; username: string}>(`${this.apiUrl}/admin/users/${id}`);
  }
  lookupAccounts(id: number): Observable<TradingAccount[]> {
    return this.http.get<TradingAccount[]>(`${this.apiUrl}/admin/users/${id}/accounts`);
  }
  accountHistory(id: string, kind: string, page: number): Observable<Record<string, any>> {
    return this.http.get<Record<string, any>>(`${this.apiUrl}/admin/accounts/${id}/history`, {params: {kind, page, size: 25}});
  }

  getPlatformStats(): Observable<PlatformStats> {
    if (this.useMockData) {
      return of(this.mockGetPlatformStats()).pipe(delay(300));
    }
    return this.http.get<PlatformStats>(`${this.apiUrl}/admin/stats`);
  }

  // Analyst Dashboard APIs

  getSymbolTrends(): Observable<SymbolTrend[]> {
    if (this.useMockData) {
      return of(this.mockGetSymbolTrends()).pipe(delay(500));
    }
    return this.http.get<SymbolTrend[]>(`${this.apiUrl}/analyst/trends/symbols`);
  }

  getMarketTrends(): Observable<MarketTrend[]> {
    if (this.useMockData) {
      return of(this.mockGetMarketTrends()).pipe(delay(500));
    }
    return this.http.get<MarketTrend[]>(`${this.apiUrl}/analyst/trends/market`);
  }

  // Mock Data

  private mockLogin(request: LoginRequest): LoginResponse {
    const mockUsers: Record<string, User> = {
      'investor@test.com': { 
        userId: '1', 
        email: 'investor@test.com', 
        firstName: 'John',
        lastName: 'Investor',
        role: 'investor' 
      },
      'admin@test.com': { 
        userId: '2', 
        email: 'admin@test.com', 
        firstName: 'Admin',
        lastName: 'User',
        role: 'admin' 
      },
      'analyst@test.com': { 
        userId: '3', 
        email: 'analyst@test.com', 
        firstName: 'Alice',
        lastName: 'Analyst',
        role: 'analyst' 
      }
    };

    const user = mockUsers[request.username];
    if (user && request.password === 'password') {
      return {
        sessionId: 'mock-session-' + Date.now(),
        accessToken: 'mock-token-' + Date.now(),
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        user
      };
    }
    throw new Error('Invalid credentials');
  }

  private mockGetAccountDetails(accountId: string): TradingAccount {
    return {
      accountId,
      type: 'TRADING',
      availableFunds: 50000,
      creationDate: '2025-01-15'
    };
  }

  private mockGetAccountBalance(accountId: string): { accountId: string; cashBalance: number; currency?: string } {
    return {
      accountId,
      cashBalance: 50000
    };
  }

  private mockGetHoldings(accountId: string): Holding[] {
    return [
      { symbol: 'AAPL', quantity: 100, averageCost: 145.20, currentPrice: 150.25 },
      { symbol: 'MSFT', quantity: 50, averageCost: 370.00, currentPrice: 380.50 },
      { symbol: 'GOOGL', quantity: 25, averageCost: 130.00, currentPrice: 140.75 }
    ];
  }

  private mockGetOrders(accountId: string): Order[] {
    return [
      { 
        orderId: 'ORD-001', 
        accountId, 
        symbol: 'TSLA', 
        side: 'BUY', 
        quantity: 10, 
        price: 250, 
        status: 'FILLED',
        dateRequested: '2025-10-01'
      },
      { 
        orderId: 'ORD-002', 
        accountId, 
        symbol: 'NVDA', 
        side: 'SELL', 
        quantity: 5, 
        price: 875, 
        status: 'PENDING',
        dateRequested: '2025-10-02'
      }
    ];
  }

  private mockCreateOrder(accountId: string, order: Omit<Order, 'orderId' | 'accountId' | 'dateRequested'>): Order {
    return {
      ...order,
      orderId: 'ORD-' + Math.floor(Math.random() * 10000).toString().padStart(3, '0'),
      accountId,
      status: 'PENDING',
      dateRequested: new Date().toISOString().slice(0, 10)
    };
  }

  private mockGetTrades(accountId: string): Trade[] {
    return [
      {
        tradeId: 'TRD-001',
        orderId: 'ORD-001',
        symbol: 'TSLA',
        executionPrice: 248.50,
        executedQuantity: 10,
        executedAt: '2025-10-01T10:35:00Z'
      },
      {
        tradeId: 'TRD-002',
        orderId: 'ORD-003',
        symbol: 'AMD',
        executionPrice: 120.75,
        executedQuantity: 20,
        executedAt: '2025-09-28T09:12:00Z'
      }
    ];
  }

  // Admin Dashboard Mock Methods

  private mockGetAllInvestors(): InvestorInfo[] {
    return [
      {
        userId: '1',
        email: 'investor@test.com',
        firstName: 'John',
        lastName: 'Investor',
        accountId: 'ACC-001',
        accountBalance: 50000,
        portfolioValue: 125750,
        holdingsCount: 3,
        ordersCount: 12,
        joinDate: '2025-01-15'
      },
      {
        userId: '4',
        email: 'sarah@test.com',
        firstName: 'Sarah',
        lastName: 'Trader',
        accountId: 'ACC-004',
        accountBalance: 75000,
        portfolioValue: 185300,
        holdingsCount: 5,
        ordersCount: 28,
        joinDate: '2024-12-01'
      },
      {
        userId: '5',
        email: 'mike@test.com',
        firstName: 'Mike',
        lastName: 'Johnson',
        accountId: 'ACC-005',
        accountBalance: 25000,
        portfolioValue: 62500,
        holdingsCount: 2,
        ordersCount: 8,
        joinDate: '2025-03-10'
      }
    ];
  }

  private mockGetAllTrades(): Trade[] {
    return [
      {
        tradeId: 'TRD-001',
        orderId: 'ORD-001',
        symbol: 'TSLA',
        executionPrice: 248.50,
        executedQuantity: 10,
        executedAt: '2025-10-01T10:35:00Z'
      },
      {
        tradeId: 'TRD-002',
        orderId: 'ORD-003',
        symbol: 'AMD',
        executionPrice: 120.75,
        executedQuantity: 20,
        executedAt: '2025-09-28T09:12:00Z'
      },
      {
        tradeId: 'TRD-003',
        orderId: 'ORD-005',
        symbol: 'NVDA',
        executionPrice: 875.00,
        executedQuantity: 5,
        executedAt: '2025-10-02T14:20:00Z'
      },
      {
        tradeId: 'TRD-004',
        orderId: 'ORD-007',
        symbol: 'AAPL',
        executionPrice: 150.25,
        executedQuantity: 50,
        executedAt: '2025-10-03T09:45:00Z'
      },
      {
        tradeId: 'TRD-005',
        orderId: 'ORD-009',
        symbol: 'MSFT',
        executionPrice: 380.50,
        executedQuantity: 15,
        executedAt: '2025-10-03T11:30:00Z'
      }
    ];
  }

  private mockGetAllOrders(): Order[] {
    return [
      {
        orderId: 'ORD-001',
        accountId: 'ACC-001',
        symbol: 'TSLA',
        side: 'BUY',
        quantity: 10,
        price: 250,
        status: 'FILLED',
        dateRequested: '2025-10-01'
      },
      {
        orderId: 'ORD-002',
        accountId: 'ACC-001',
        symbol: 'NVDA',
        side: 'SELL',
        quantity: 5,
        price: 875,
        status: 'PENDING',
        dateRequested: '2025-10-02'
      },
      {
        orderId: 'ORD-003',
        accountId: 'ACC-004',
        symbol: 'AMD',
        side: 'BUY',
        quantity: 20,
        price: 120,
        status: 'FILLED',
        dateRequested: '2025-09-28'
      },
      {
        orderId: 'ORD-005',
        accountId: 'ACC-004',
        symbol: 'NVDA',
        side: 'BUY',
        quantity: 5,
        price: 875,
        status: 'FILLED',
        dateRequested: '2025-10-02'
      },
      {
        orderId: 'ORD-007',
        accountId: 'ACC-005',
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 50,
        price: 150,
        status: 'FILLED',
        dateRequested: '2025-10-03'
      }
    ];
  }

  private mockGetPlatformStats(): PlatformStats {
    const allInvestors = this.mockGetAllInvestors();
    const topTrader = allInvestors[1]; // Sarah (highest portfolio value)
    const allTrades = this.mockGetAllTrades();

    return {
      totalInvestors: 3,
      totalTrades: allTrades.length,
      totalOrders: 10,
      totalVolume: 1500000,
      avgPortfolioValue: (allInvestors.reduce((sum, inv) => sum + inv.portfolioValue, 0) / allInvestors.length),
      topTrader
    };
  }

  // Analyst Dashboard Mock Methods

  private mockGetSymbolTrends(): SymbolTrend[] {
    return [
      {
        symbol: 'AAPL',
        tradeCount: 18,
        totalVolume: 980,
        avgPrice: 150.25,
        buyCount: 12,
        sellCount: 6,
        popularityScore: 95
      },
      {
        symbol: 'NVDA',
        tradeCount: 16,
        totalVolume: 820,
        avgPrice: 875.00,
        buyCount: 7,
        sellCount: 9,
        popularityScore: 91
      },
      {
        symbol: 'MSFT',
        tradeCount: 13,
        totalVolume: 620,
        avgPrice: 380.50,
        buyCount: 9,
        sellCount: 4,
        popularityScore: 84
      },
      {
        symbol: 'TSLA',
        tradeCount: 14,
        totalVolume: 880,
        avgPrice: 248.50,
        buyCount: 5,
        sellCount: 9,
        popularityScore: 87
      },
      {
        symbol: 'AMD',
        tradeCount: 11,
        totalVolume: 560,
        avgPrice: 120.75,
        buyCount: 8,
        sellCount: 3,
        popularityScore: 76
      },
      {
        symbol: 'GOOGL',
        tradeCount: 9,
        totalVolume: 470,
        avgPrice: 140.75,
        buyCount: 4,
        sellCount: 5,
        popularityScore: 68
      },
      {
        symbol: 'JPM',
        tradeCount: 10,
        totalVolume: 410,
        avgPrice: 194.10,
        buyCount: 3,
        sellCount: 7,
        popularityScore: 63
      },
      {
        symbol: 'PFE',
        tradeCount: 7,
        totalVolume: 290,
        avgPrice: 30.85,
        buyCount: 5,
        sellCount: 2,
        popularityScore: 52
      },
      {
        symbol: 'XOM',
        tradeCount: 12,
        totalVolume: 700,
        avgPrice: 119.35,
        buyCount: 4,
        sellCount: 8,
        popularityScore: 74
      },
      {
        symbol: 'BAC',
        tradeCount: 8,
        totalVolume: 260,
        avgPrice: 41.80,
        buyCount: 6,
        sellCount: 2,
        popularityScore: 49
      }
    ];
  }

  private mockGetMarketTrends(): MarketTrend[] {
    return [
      {
        date: '2025-10-01',
        topSymbol: 'TSLA',
        tradeCount: 8,
        totalVolume: 400000
      },
      {
        date: '2025-10-02',
        topSymbol: 'NVDA',
        tradeCount: 12,
        totalVolume: 500000
      },
      {
        date: '2025-10-03',
        topSymbol: 'AAPL',
        tradeCount: 15,
        totalVolume: 600000
      },
      {
        date: '2025-10-04',
        topSymbol: 'MSFT',
        tradeCount: 10,
        totalVolume: 350000
      },
      {
        date: '2025-10-05',
        topSymbol: 'AAPL',
        tradeCount: 18,
        totalVolume: 700000
      }
    ];
  }

  // Helper Methods

  /**
   * Toggle between mock data and real API
   * Call this to switch: apiService.setUseMockData(false) when backend is ready
   */
  setUseMockData(useMock: boolean): void {
    this.useMockData = useMock;
    console.log('API Service: Using ' + (useMock ? 'MOCK' : 'REAL') + ' data');
  }

  /**
   * Set the API URL when backend is deployed
   */
  setApiUrl(url: string): void {
    this.apiUrl = url;
  }
}
