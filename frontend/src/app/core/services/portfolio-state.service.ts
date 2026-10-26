import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, catchError, defer, forkJoin, map, of, shareReplay, switchMap, timeout } from 'rxjs';
import { ApiService, Order, ProcessOrderResponse } from './api.service';

export interface TradeRequest {
  ticker: string;
  side: 'BUY' | 'SELL';
  shares: number;
  price: number;
}

export interface TradeExecutionResult {
  order: ProcessOrderResponse;
  syncWarning?: string;
}

export interface PortfolioAccountState {
  accountNumber: string;
  cash: number;
  currency?: string;
  holdings: Array<{ symbol: string; shares: number; avgPrice: number; currentPrice: number }>;
  history: Array<{ timestamp: Date; totalValue: number }>;
  orders: Order[];
  valuationWarning?: string;
}

@Injectable({ providedIn: 'root' })
export class PortfolioStateService {
  private readonly accounts = new Map<string, BehaviorSubject<PortfolioAccountState | null>>();
  private readonly revisions = new Map<string, number>();
  readonly persistenceError$ = new BehaviorSubject<string>('');

  constructor(private readonly api: ApiService) {}

  watchAccount(id: string): Observable<PortfolioAccountState | null> {
    return this.account(id).asObservable();
  }

  static estimateFee(notional: number): number {
    return notional > 0 ? Math.max(notional * 0.001, 1) : 0;
  }

  executeTrade(accountNumber: string, trade: TradeRequest): Observable<TradeExecutionResult> {
    return defer(() => {
      if (!accountNumber || !/^[A-Z][A-Z0-9.-]{0,9}$/.test(trade.ticker) ||
          !['BUY', 'SELL'].includes(trade.side) || !Number.isSafeInteger(trade.shares) ||
          trade.shares <= 0 || !Number.isFinite(trade.price) || trade.price <= 0 ||
          !Number.isFinite(trade.shares * trade.price)) {
        throw new Error('A valid account, ticker, side, quantity, and price are required.');
      }
      // The displayed price is an estimate; the backend supplies the execution price and fees.
      return this.api.createOrder(accountNumber, {
        symbol: trade.ticker, side: trade.side, quantity: trade.shares, status: 'PENDING'
      }).pipe(
        timeout(30000),
        switchMap(order => this.refreshAccount(
          accountNumber, order.status === 'EXECUTED' || order.status === 'FILLED'
        ).pipe(
          map((): TradeExecutionResult => ({ order })),
          catchError(error => {
            console.error('Post-order account synchronization failed:', error);
            return of<TradeExecutionResult>({
              order,
              syncWarning: 'Account synchronization failed. Refresh Portfolio and History to verify balances; do not resubmit this order.'
            });
          })
        ))
      );
    }).pipe(
      // Complete reconciliation even if the user leaves the view after submitting.
      shareReplay({ bufferSize: 1, refCount: false })
    );
  }

  refreshAccount(id: string, recordTrade = false): Observable<PortfolioAccountState> {
    return defer(() => {
      const key = this.cacheKey(id);
      const subject = this.account(id);
      const revision = (this.revisions.get(key) ?? 0) + 1;
      this.revisions.set(key, revision);
      return forkJoin({
        balance: this.api.getAccountBalance(id),
        holdings: this.api.getHoldings(id),
        orders: this.api.getOrders(id)
      }).pipe(
        timeout(10000),
        switchMap(data => {
          const held = data.holdings.filter(h => h.quantity > 0);
          return (held.length ? forkJoin(held.map(h => {
            if (h.currentPrice !== undefined && Number.isFinite(h.currentPrice)) {
              return of({ holding: h, estimated: false });
            }
            return this.api.getQuote(h.symbol).pipe(
              timeout(4000),
              map(quote => {
                if (!Number.isFinite(quote.price) || quote.price < 0) throw new Error('Invalid quote price.');
                return { holding: { ...h, currentPrice: quote.price }, estimated: false };
              }),
              catchError(error => {
                console.warn(`Live valuation unavailable for ${h.symbol}; using cost basis.`, error);
                return of({ holding: { ...h, currentPrice: h.averageCost }, estimated: true });
              })
            );
          })) : of([])).pipe(map(priced => ({
            ...data, holdings: priced.map(p => p.holding),
            valuationWarning: priced.some(p => p.estimated)
              ? 'Some live quotes are unavailable. Portfolio value uses cost basis for those positions.'
              : ''
          })));
        }),
        map(({ balance, holdings, orders, valuationWarning }) => {
        const previous = subject.value;
        if (this.revisions.get(key) !== revision && previous) return previous;
        const positions = holdings.filter(h => h.quantity > 0).map(h => ({
          symbol: h.symbol, shares: h.quantity, avgPrice: h.averageCost,
          currentPrice: h.currentPrice ?? h.averageCost
        }));
        const totalValue = balance.cashBalance + positions.reduce((sum, h) => sum + h.shares * h.currentPrice, 0);
        if (!Number.isFinite(totalValue)) throw new Error('Invalid account valuation received from server.');
        const history = [...(previous?.history ?? [])];
        const now = new Date();
        const last = history.at(-1);
        if (recordTrade || !last || last.totalValue !== totalValue ||
            last.timestamp.toDateString() !== now.toDateString()) {
          history.push({ timestamp: now, totalValue });
        }
        const state: PortfolioAccountState = {
          accountNumber: id, currency: balance.currency, cash: balance.cashBalance, holdings: positions, orders, history, valuationWarning
        };
        this.persist(state, key);
        subject.next(state);
        return state;
      }));
    });
  }

  private account(id: string): BehaviorSubject<PortfolioAccountState | null> {
    const key = this.cacheKey(id);
    let subject = this.accounts.get(key);
    if (!subject) {
      subject = new BehaviorSubject<PortfolioAccountState | null>(this.restore(id));
      this.accounts.set(key, subject);
    }
    return subject;
  }

  private cacheKey(id: string): string {
    let user = 'anonymous';
    try { user = String(JSON.parse(localStorage.getItem('currentUser') || '{}').id || 'anonymous'); } catch { /* Cache only; backend remains authoritative. */ }
    return `leap_account_${user}_${id}`;
  }

  private restore(id: string): PortfolioAccountState | null {
    if (typeof localStorage === 'undefined') return null;
    try {
      const raw = localStorage.getItem(this.cacheKey(id));
      if (!raw) return null;
      const value = JSON.parse(raw) as PortfolioAccountState;
      if (value.accountNumber !== id || !Number.isFinite(value.cash) ||
          !Array.isArray(value.holdings) || !Array.isArray(value.history) || !Array.isArray(value.orders) ||
          !value.holdings.every(h => typeof h.symbol === 'string' && [h.shares, h.avgPrice, h.currentPrice].every(Number.isFinite)) ||
          !value.history.every(h => Number.isFinite(h.totalValue) && Number.isFinite(new Date(h.timestamp).getTime()))) {
        throw new Error('Invalid persisted account state.');
      }
      return { ...value, history: value.history.map(h => ({ ...h, timestamp: new Date(h.timestamp) })) };
    } catch (error) {
      console.error('Unable to restore account cache:', error);
      this.persistenceError$.next('Saved account data could not be read. Reloading from the server.');
      return null;
    }
  }

  private persist(state: PortfolioAccountState, key: string): void {
    if (typeof localStorage === 'undefined') return;
    try {
      localStorage.setItem(key, JSON.stringify(state));
      this.persistenceError$.next('');
    } catch (error) {
      console.error('Unable to persist account state:', error);
      this.persistenceError$.next('Account updated, but browser storage failed. History may not survive a refresh.');
    }
  }
}
