import { TestBed } from '@angular/core/testing';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { firstValueFrom, of, Subject, throwError } from 'rxjs';
import { ApiService, Holding, Order, ProcessOrderResponse } from './api.service';
import { PortfolioAccountState, PortfolioStateService } from './portfolio-state.service';
import { PortfolioService } from './portfolio.service';

describe('Persistent portfolio state', () => {
  const id = 'portfolio-test';
  const api = {
    getAccountBalance: vi.fn(),
    getHoldings: vi.fn(),
    getQuote: vi.fn(),
    getOrders: vi.fn(),
    createOrder: vi.fn()
  };
  let service: PortfolioStateService;
  function backend(cash: number, holdings: Holding[] = [], orders: Order[] = []) {
    api.getAccountBalance.mockReturnValue(of({ cashBalance: cash }));
    api.getHoldings.mockReturnValue(of(holdings));
    api.getOrders.mockReturnValue(of(orders));
  }
  beforeEach(() => {
    api.createOrder.mockReset();
    localStorage.removeItem(`leap_account_${id}`);
    localStorage.removeItem('leap_account_other-test');
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    service = TestBed.inject(PortfolioStateService);
    backend(350000);
  });
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem(`leap_account_${id}`);
    localStorage.removeItem('leap_account_other-test');
  });

  it('publishes confirmed buy/sell state, including fees, without locally deducting twice', async () => {
    await firstValueFrom(service.refreshAccount(id));
    const order: Order = { orderId: '1', accountId: id, symbol: 'AAPL', side: 'BUY',
      quantity: 10, price: 100, status: 'EXECUTED' };
    backend(348999, [{ symbol: 'AAPL', quantity: 10, averageCost: 100, currentPrice: 100 }], [order]);
    const buy = await firstValueFrom(service.refreshAccount(id, true));
    expect(buy.cash).toBe(348999);
    expect(buy.holdings[0].shares).toBe(10);
    expect(buy.orders).toEqual([order]);
    expect(buy.history.at(-1)?.totalValue).toBe(349999);
    backend(350098, [], [{ ...order, orderId: '2', side: 'SELL', price: 110 }]);
    const sell = await firstValueFrom(service.refreshAccount(id, true));
    expect(sell.holdings).toEqual([]);
    expect(sell.history).toHaveLength(3);
    expect(sell.history.at(-1)?.totalValue).toBe(350098);
  });

  it('executes buy and sell orders through the backend and persists reconciled snapshots', async () => {
    await firstValueFrom(service.refreshAccount(id));
    const order: Order = { orderId: '1', accountId: id, symbol: 'MMM', side: 'BUY',
      quantity: 2, price: 175, status: 'EXECUTED' };
    backend(349649, [{ symbol: 'MMM', quantity: 2, averageCost: 175, currentPrice: 175 }], [order]);
    api.createOrder.mockReturnValue(of({ orderId: 1, status: 'EXECUTED', executedQuantity: 2, executionPrice: 175, fee: 1 }));
    const result = await firstValueFrom(service.executeTrade(id, { ticker: 'MMM', side: 'BUY', shares: 2, price: 174 }));
    expect(api.createOrder).toHaveBeenCalledExactlyOnceWith(id, {
      symbol: 'MMM', side: 'BUY', quantity: 2, status: 'PENDING'
    });
    expect(result.order.executionPrice).toBe(175);
    const buy = await firstValueFrom(service.watchAccount(id));
    expect(buy?.cash).toBe(349649);
    expect(buy?.holdings[0].shares).toBe(2);
    expect(buy?.orders).toEqual([order]);
    expect(buy?.history.at(-1)?.totalValue).toBe(349999);
    expect(JSON.parse(localStorage.getItem(`leap_account_${id}`)!).cash).toBe(349649);

    backend(350008, [], [{ ...order, side: 'SELL', price: 180 }]);
    api.createOrder.mockReturnValue(of({ orderId: 2, status: 'EXECUTED', executedQuantity: 2, executionPrice: 180 }));
    await firstValueFrom(service.executeTrade(id, { ticker: 'MMM', side: 'SELL', shares: 2, price: 180 }));
    const sell = await firstValueFrom(service.watchAccount(id));
    expect(sell?.holdings).toEqual([]);
    expect(sell?.cash).toBe(350008);
    expect(sell?.history).toHaveLength(3);
  });

  it('distinguishes a confirmed execution from a failed account refresh', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    api.createOrder.mockReturnValue(of({ orderId: 1, status: 'EXECUTED' }));
    api.getAccountBalance.mockReturnValue(throwError(() => new Error('offline')));
    const result = await firstValueFrom(service.executeTrade(id, { ticker: 'MMM', side: 'BUY', shares: 1, price: 175 }));
    expect(result.order.status).toBe('EXECUTED');
    expect(result.syncWarning).toContain('do not resubmit');
    expect(api.createOrder).toHaveBeenCalledTimes(1);
  });

  it('finishes reconciliation for the original account after the view unsubscribes', async () => {
    const pending = new Subject<ProcessOrderResponse>();
    api.createOrder.mockReturnValue(pending);
    const subscription = service.executeTrade(id, { ticker: 'MMM', side: 'BUY', shares: 1, price: 175 }).subscribe();
    subscription.unsubscribe();
    backend(349824, [{ symbol: 'MMM', quantity: 1, averageCost: 175, currentPrice: 175 }]);
    pending.next({ orderId: 1, status: 'EXECUTED' });
    pending.complete();
    expect((await firstValueFrom(service.watchAccount(id)))?.cash).toBe(349824);
    expect(await firstValueFrom(service.watchAccount('other-test'))).toBeNull();
  });

  it('does not fabricate cash changes on rejection or retry an uncertain submission', async () => {
    const original = await firstValueFrom(service.refreshAccount(id));
    api.createOrder.mockReturnValue(of({ orderId: 1, status: 'REJECTED' }));
    const result = await firstValueFrom(service.executeTrade(id, { ticker: 'MMM', side: 'BUY', shares: 1, price: 175 }));
    expect(result.order.status).toBe('REJECTED');
    expect(await firstValueFrom(service.watchAccount(id))).toEqual(original);
    api.createOrder.mockReturnValue(throwError(() => new Error('offline')));
    await expect(firstValueFrom(service.executeTrade(id, { ticker: 'MMM', side: 'BUY', shares: 1, price: 175 })))
      .rejects.toThrow('offline');
    expect(api.createOrder).toHaveBeenCalledTimes(2);
    expect(await firstValueFrom(service.watchAccount(id))).toEqual(original);
  });

  it('rejects invalid order input without calling the backend and estimates the configured fees', async () => {
    for (const shares of [0, -1, 0.5, NaN, Infinity]) {
      await expect(firstValueFrom(service.executeTrade(id, { ticker: 'MMM', side: 'BUY', shares, price: 175 })))
        .rejects.toThrow('valid account');
    }
    expect(api.createOrder).not.toHaveBeenCalled();
    expect(PortfolioStateService.estimateFee(175)).toBe(1);
    expect(PortfolioStateService.estimateFee(3500)).toBe(3.5);
  });

  it('persists and restores dates and isolates account histories', async () => {
    await firstValueFrom(service.refreshAccount(id));
    backend(100);
    await firstValueFrom(service.refreshAccount('other-test'));
    const restored = await firstValueFrom(new PortfolioStateService(TestBed.inject(ApiService)).watchAccount(id));
    expect(restored?.cash).toBe(350000);
    expect(restored?.history[0].timestamp).toBeInstanceOf(Date);
    expect((await firstValueFrom(service.watchAccount('other-test')))?.cash).toBe(100);
  });

  it('values backend holdings using current quotes instead of purchase cost', async () => {
    backend(100, [{ symbol: 'AAPL', quantity: 2, averageCost: 50 }]);
    api.getQuote.mockReturnValue(of({ symbol: 'AAPL', price: 75 }));
    const state = await firstValueFrom(service.refreshAccount(id));
    expect(state.holdings[0].currentPrice).toBe(75);
    expect(state.history[0].totalValue).toBe(250);
    expect(state.valuationWarning).toBe('');
  });

  it('explicitly warns when live quotes fail and preserves confirmed positions', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    backend(100, [{ symbol: 'AAPL', quantity: 2, averageCost: 50 }]);
    api.getQuote.mockReturnValue(throwError(() => new Error('offline')));
    const state = await firstValueFrom(service.refreshAccount(id));
    expect(state.history[0].totalValue).toBe(200);
    expect(state.valuationWarning).toContain('cost basis');
  });

  it('reports corrupt storage and restores from authoritative backend data', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    localStorage.setItem(`leap_account_${id}`, '{"cash":"bad"}');
    expect(await firstValueFrom(service.watchAccount(id))).toBeNull();
    expect(service.persistenceError$.value).not.toBe('');
    expect((await firstValueFrom(service.refreshAccount(id))).cash).toBe(350000);
  });

  it('does not publish partial account values if one endpoint fails', async () => {
    const original = await firstValueFrom(service.refreshAccount(id));
    backend(1);
    api.getHoldings.mockReturnValue(throwError(() => new Error('offline')));
    await expect(firstValueFrom(service.refreshAccount(id))).rejects.toThrow('offline');
    expect(await firstValueFrom(service.watchAccount(id))).toEqual(original);
  });

  it('ignores a late older refresh after a newer trade refresh', async () => {
    await firstValueFrom(service.refreshAccount(id));
    const oldBalance = new Subject<{ cashBalance: number }>();
    api.getAccountBalance.mockReturnValue(oldBalance);
    const old = firstValueFrom(service.refreshAccount(id));
    backend(349999);
    await firstValueFrom(service.refreshAccount(id, true));
    oldBalance.next({ cashBalance: 350000 });
    oldBalance.complete();
    await old;
    expect((await firstValueFrom(service.watchAccount(id)))?.cash).toBe(349999);
  });

  it('records daily observations but not duplicate refreshes on the same day', async () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date('2026-10-22T12:00:00Z'));
      await firstValueFrom(service.refreshAccount(id));
      expect((await firstValueFrom(service.refreshAccount(id))).history).toHaveLength(1);
      vi.setSystemTime(new Date('2026-10-23T12:00:00Z'));
      expect((await firstValueFrom(service.refreshAccount(id))).history).toHaveLength(2);
    } finally { vi.useRealTimers(); }
  });

  it('plots observed balances without synthetic returns, including zero and range boundaries', () => {
    const chart = new PortfolioService();
    const state: PortfolioAccountState = {
      accountNumber: id, cash: 0, holdings: [], orders: [],
      history: [{ timestamp: new Date('2026-10-01T12:00:00Z'), totalValue: 0 }]
    };
    const points = chart.getAccountHistory(state, '1D', new Date('2026-10-22T12:00:00Z'));
    expect(points.map(p => p.value)).toEqual([0, 0]);
    expect(points[0].date.toISOString()).toBe('2026-10-21T12:00:00.000Z');
    expect(chart.getAccountHistory(state, 'ALL', new Date('2026-10-22T12:00:00Z'))[0].date)
      .toEqual(state.history[0].timestamp);
  });
});
