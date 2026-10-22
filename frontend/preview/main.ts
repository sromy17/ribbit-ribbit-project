/** Explicit development-only entry point. Never imported by src/main.ts. */
import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, RouterOutlet } from '@angular/router';
import { of, Subject } from 'rxjs';
import { routes } from '../src/app/app.routes';
import { AuthService } from '../src/app/core/services/auth.service';
import { ApiService } from '../src/app/core/services/api.service';
import { ThemeService } from '../src/app/core/services/theme.service';

const now = new Date().toISOString();
const role = location.pathname.includes('admin')
  ? 'admin'
  : location.pathname.includes('analyst')
    ? 'analyst'
    : 'investor';
const customer = {
  id: '1042',
  username: 'Alex Morgan',
  role,
  permissions: ['PERM_DISPUTE_WORK', 'PERM_DISPUTE_SUPERVISOR', 'PERM_REPORTING'],
};
const accounts = [
  { accountId: '20481', type: 'TRADING', availableFunds: 12480.5 },
  { accountId: '20482', type: 'INVESTMENT', availableFunds: 5200 },
];
const orderRows = [
  {
    requestid: 83204,
    accountid: 20481,
    instrumentid: 101,
    side: 'BUY',
    quantity_requested: 12,
    order_status: 'EXECUTED',
    created_at_utc: now,
  },
  {
    requestid: 83203,
    accountid: 20482,
    instrumentid: 102,
    side: 'SELL',
    quantity_requested: 5,
    order_status: 'PENDING',
    created_at_utc: now,
  },
  {
    requestid: 83202,
    accountid: 20481,
    instrumentid: 103,
    side: 'BUY',
    quantity_requested: 20,
    order_status: 'REJECTED',
    created_at_utc: now,
  },
  {
    requestid: 83201,
    accountid: 20481,
    instrumentid: 104,
    side: 'BUY',
    quantity_requested: 8,
    order_status: 'EXECUTED',
    created_at_utc: now,
  },
];
const cases = [
  {
    caseId: 241,
    title: 'Execution fee query',
    transactionType: 'TRADE',
    transactionReference: '83204',
    reason: 'Please review the fee applied to this trade.',
    status: 'OPEN',
    version: 0,
    customerUserId: 1042,
    accountId: 20481,
    orderId: 83204,
    createdAtUtc: now,
    updatedAtUtc: now,
    assignedStaffUserId: null as number | null,
    resolutionReason: '',
    feeRelated: true,
  },
  {
    caseId: 240,
    title: 'Cash movement clarification',
    transactionType: 'CASH',
    transactionReference: '72016',
    reason: 'I would like more detail about this movement.',
    status: 'IN_PROGRESS',
    version: 2,
    customerUserId: 1042,
    accountId: 20481,
    orderId: 83204,
    createdAtUtc: now,
    updatedAtUtc: now,
    assignedStaffUserId: 1042,
    resolutionReason: '',
    feeRelated: false,
  },
];
const events: Record<number, any[]> = {
  241: [
    { event_type: 'SUBMITTED', actor_user_id: 1042, occurred_at_utc: now, note: cases[0].reason },
  ],
  240: [
    { event_type: 'SUBMITTED', actor_user_id: 1042, occurred_at_utc: now, note: cases[1].reason },
    {
      event_type: 'ACCEPTED',
      actor_user_id: 1042,
      occurred_at_utc: now,
      note: 'Reviewing linked account history.',
    },
  ],
};
const history: Record<string, any[]> = {
  orders: orderRows.map((r, i) => ({
    requestid: r.requestid,
    ticker: ['AAPL', 'MSFT', 'NVDA', 'AMZN'][i],
    side: r.side,
    quantity: r.quantity_requested,
    bidprice: 189.84,
    status: r.order_status,
    created_at_utc: now,
  })),
  trades: [
    {
      executeid: 72016,
      requestid: 83204,
      ticker: 'AAPL',
      quantity_executed: 12,
      askprice: 189.84,
      fee: 2.28,
      executed_at_utc: now,
    },
  ],
  holdings: [
    {
      holdingid: 1,
      ticker: 'AAPL',
      quantity: 24,
      average_cost: 176.12,
      as_of_date: now.slice(0, 10),
    },
    {
      holdingid: 2,
      ticker: 'MSFT',
      quantity: 8,
      average_cost: 411.27,
      as_of_date: now.slice(0, 10),
    },
  ],
  cash: [
    {
      movement_id: 72016,
      order_id: 83204,
      amount: -2280.36,
      currency: 'USD',
      movement_type: 'BUY_SETTLEMENT',
      occurred_at_utc: now,
    },
  ],
};
const trends = [
  {
    symbol: 'AAPL',
    tradeCount: 124,
    totalVolume: 950,
    avgPrice: 189.84,
    buyCount: 86,
    sellCount: 38,
    popularityScore: 92,
  },
  {
    symbol: 'MSFT',
    tradeCount: 98,
    totalVolume: 740,
    avgPrice: 420.12,
    buyCount: 46,
    sellCount: 52,
    popularityScore: 81,
  },
  {
    symbol: 'NVDA',
    tradeCount: 146,
    totalVolume: 1250,
    avgPrice: 128.56,
    buyCount: 100,
    sellCount: 46,
    popularityScore: 96,
  },
  {
    symbol: 'JPM',
    tradeCount: 63,
    totalVolume: 480,
    avgPrice: 211.1,
    buyCount: 22,
    sellCount: 41,
    popularityScore: 65,
  },
];
const api = {
  getAdminOrders: (from: string, to: string, page: number, size: number, status: string) => {
    const rows = orderRows.filter((r) => !status || r.order_status === status);
    return of({ items: rows, total: rows.length, page, size, asOf: now, from, to });
  },
  getAdminReportingStatus: () =>
    of({ status: 'SUCCEEDED', published_at: now, source_watermark: now, stale: false }),
  getAdminActivity: () =>
    of({
      orders: [
        { order_status: 'EXECUTED', order_count: 1824 },
        { order_status: 'PENDING', order_count: 26 },
        { order_status: 'REJECTED', order_count: 18 },
      ],
      instruments: trends.map((t) => ({
        ticker: t.symbol,
        fill_count: t.tradeCount,
        executed_quantity: t.totalVolume,
      })),
      asOf: now,
      valuation: 'Monetary valuation is unavailable in this sample dataset.',
    }),
  getAdminCasePage: (status: string) => {
    const rows = cases.filter((c) => !status || c.status === status);
    return of({ items: rows, total: rows.length, asOf: now });
  },
  getAdminCase: (id: number) => of(cases.find((c) => c.caseId === id)),
  getAdminCaseEvents: (id: number) => of([...(events[id] || [])]),
  caseAction: (id: number, action: string, body: any) => {
    const c = cases.find((c) => c.caseId === id)!;
    c.version++;
    if (action === 'claim') {
      c.status = 'CLAIMED';
      c.assignedStaffUserId = 1042;
    }
    if (action === 'accept') c.status = 'IN_PROGRESS';
    if (action === 'resolve') {
      c.status = 'RESOLVED';
      c.resolutionReason = body.outcome + ': ' + body.reason;
    }
    if (action === 'release') {
      c.status = 'OPEN';
      c.assignedStaffUserId = null;
    }
    if (action === 'reassign') {
      c.status = 'CLAIMED';
      c.assignedStaffUserId = body.newAssigneeUserId;
    }
    events[id].push({
      event_type: action.toUpperCase(),
      actor_user_id: 1042,
      occurred_at_utc: now,
      note: body.note || body.reason,
    });
    return of({ ...c });
  },
  investigate: (id: number) =>
    of({
      order: {
        ticker: 'AAPL',
        side: 'BUY',
        status: 'EXECUTED',
        correlation_id: 'sample-order-83204',
      },
      asOf: now,
      evidenceAvailable: true,
      events: [
        {
          event_sequence: 1,
          event_type: 'ORDER_SUBMITTED',
          occurred_at_utc: now,
          reason: 'Order received.',
        },
        { event_sequence: 2, event_type: 'ORDER_ACCEPTED', occurred_at_utc: now },
        {
          event_sequence: 3,
          event_type: 'PRICING_DECISION',
          occurred_at_utc: now,
          quote_source: 'Sample quote',
          quote_price: 189.84,
          quote_at_utc: now,
          decision_price: 189.84,
          quantity: 12,
          fee: 2.28,
        },
        { event_sequence: 4, event_type: 'FILL_COMMITTED', occurred_at_utc: now },
      ],
      fills: [
        {
          executeid: 72016,
          askprice: 189.84,
          quantity_executed: 12,
          fee: 2.28,
          executed_at_utc: now,
        },
      ],
      cash: history['cash'],
      holdings: [{ movement_id: 1, occurred_at_utc: now, quantity_delta: 12, quantity_after: 24 }],
    }),
  lookupUser: () => of({ userId: 1042, username: 'Alex Morgan' }),
  lookupAccounts: () => of(accounts),
  accountHistory: (id: string, kind: string, page: number) =>
    of({
      account: { availablefunds: 12480.5 },
      items: history[kind] || [],
      total: (history[kind] || []).length,
      page,
      size: 25,
      asOf: now,
    }),
  getAccountsForUser: () => of(accounts),
  getAccountDetails: () => of(accounts[0]),
  getAccountBalance: () => of({ cashBalance: 12480.5 }),
  getHoldings: () =>
    of([
      { symbol: 'AAPL', quantity: 24, averageCost: 176.12, currentPrice: 189.84 },
      { symbol: 'MSFT', quantity: 8, averageCost: 411.27, currentPrice: 420.12 },
    ]),
  getOrders: () =>
    of(
      history['orders'].map((r) => ({
        orderId: String(r.requestid),
        symbol: r.ticker,
        side: r.side,
        quantity: r.quantity,
        price: r.bidprice,
        status: r.status,
        dateRequested: now,
      })),
    ),
  getMarketInstruments: () =>
    of({
      symbols: [
        { symbol: 'AAPL', name: 'Apple Inc.', type: 'Equity', exchange: 'NASDAQ', currency: 'USD' },
        {
          symbol: 'MSFT',
          name: 'Microsoft Corporation',
          type: 'Equity',
          exchange: 'NASDAQ',
          currency: 'USD',
        },
      ],
    }),
  getQuote: (symbol: string) => of({ symbol, price: 189.84, change: 2.47, changePercent: 1.32 }),
  getSymbolTrends: () => of(trends),
  getMarketTrends: () =>
    of([
      { date: '2026-10-21', topSymbol: 'NVDA', tradeCount: 146, totalVolume: 160700 },
      { date: '2026-10-20', topSymbol: 'AAPL', tradeCount: 124, totalVolume: 180348 },
    ]),
  createAccount: () => of(accounts[0]),
  createOrder: () => of({ orderId: 83205, status: 'PENDING' }),
};
const auth = {
  getUser: () => customer,
  login: () => of(customer),
  register: () => of({ userId: 1042 }),
  logout: () => {},
  sessionWarning$: of(false),
  sessionExpired$: new Subject<void>(),
  refreshSession: () => of({}),
  declineSessionExtension: () => {},
};
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.Default,
  template: ` <div
      class="flex flex-wrap items-center justify-center gap-x-5 gap-y-2 border-b border-amber-400/30 bg-amber-950 px-4 py-2 text-[11px] text-amber-100"
      role="note"
    >
      <strong>DESIGN PREVIEW · SAMPLE DATA · NO BACKEND</strong
      ><a class="!text-amber-100 underline" href="/login">Sign in</a
      ><a class="!text-amber-100 underline" href="/register">Register</a
      ><a class="!text-amber-100 underline" href="/accounts">Accounts</a
      ><a class="!text-amber-100 underline" href="/accounts/create">Create account</a
      ><a class="!text-amber-100 underline" href="/investor-dashboard/20481">Investor</a
      ><a class="!text-amber-100 underline" href="/admin-dashboard">Admin</a
      ><a class="!text-amber-100 underline" href="/analyst-dashboard">Analyst</a>
    </div>
    <router-outlet />`,
})
class Preview {
  constructor() {
    inject(ThemeService).initializeTheme();
  }
}
bootstrapApplication(Preview, {
  providers: [
    provideRouter(routes),
    { provide: AuthService, useValue: auth },
    { provide: ApiService, useValue: api },
  ],
}).catch(console.error);
