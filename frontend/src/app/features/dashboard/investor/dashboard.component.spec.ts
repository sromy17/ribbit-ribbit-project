import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { FinnhubNewsService } from '../../../core/services/finnhub-news.service';
import { PortfolioService } from '../../../core/services/portfolio.service';
import { PortfolioStateService } from '../../../core/services/portfolio-state.service';
import { DashboardComponent } from './dashboard.component';

describe('Wallet loading after a refresh', () => {
      const bank = {
        bankId: 7, accountId: 1, accountName: 'Existing bank',
        routingNumber: '123456789', accountNumber: '12345678', balance: 0
      };
      const activity = {
        movementId: 3, bankId: 7, movementType: 'DEPOSIT' as const,
        amount: 50, movementDate: '2026-10-27T12:00:00Z'
      };

      function createDashboard() {
        const params = new BehaviorSubject(convertToParamMap({ accountId: '1' }));
        const api = {
          getWalletBanks: vi.fn(() => of([bank])),
          getWalletActivity: vi.fn(() => of([activity]))
        };
        TestBed.configureTestingModule({
          providers: [
            { provide: AuthService, useValue: { getUser: () => ({ role: 'investor' }) } },
            { provide: Router, useValue: {} },
            { provide: ActivatedRoute, useValue: { paramMap: params } },
            { provide: ApiService, useValue: api },
            { provide: FinnhubNewsService, useValue: { availability$: of('available') } },
            { provide: PortfolioService, useValue: {} },
            { provide: PortfolioStateService, useValue: {
              persistenceError$: of(''), watchAccount: () => of(null),
              refreshAccount: vi.fn(() => of(null))
            } }
          ]
        });
        const fixture = TestBed.createComponent(DashboardComponent);
        const component = fixture.componentInstance;
        vi.spyOn(component, 'loadInstrumentCatalog').mockImplementation(() => {});
        vi.spyOn(component, 'loadAccountDetails').mockImplementation(() => {});
        vi.spyOn(component, 'loadAccounts').mockImplementation(() => {});
        vi.spyOn(component, 'loadMarketHeatmap').mockImplementation(() => {});
        return { fixture, component, api, params };
      }

      it('browses currencies and sorts company names without requesting prices', () => {
        const { component } = createDashboard();
        component.marketInstruments = [
          { symbol: 'Z.L', name: 'Alpha UK', currency: 'GBP', exchange: 'LSE', type: 'Equity' },
          { symbol: 'AAPL', name: 'Apple', currency: 'USD', exchange: 'NASDAQ', type: 'Equity' },
          { symbol: 'INFY', name: 'Infosys', currency: 'INR', exchange: 'NSE', type: 'Equity' }
        ];
        const prices = vi.spyOn(component, 'loadQuote');
        expect(component.browsableTickers.length).toBe(3);
        component.setTickerCurrency('GBP');
        expect(component.browsableTickers.map(item => item.symbol)).toEqual(['Z.L']);
        component.tradeSearch = 'Infosys';
        expect(component.browsableTickers).toEqual([]);
        component.setTickerCurrency('INR');
        expect(component.browsableTickers[0].symbol).toBe('INFY');
        component.tradeSearch = '';
        component.setTickerCurrency('ALL');
        component.tickerSort = 'name';
        expect(component.browsableTickers.map(item => item.name)).toEqual(['Alpha UK', 'Apple', 'Infosys']);
        expect(prices).not.toHaveBeenCalled();
      });

      it('remembers opened tickers, deduplicates them and clears saved history', () => {
        const { component } = createDashboard();
        component.clearRecentTickers();
        vi.spyOn(component, 'loadQuote').mockImplementation(() => {});
        component.selectTicker('AAPL');
        component.selectTicker('MSFT');
        component.selectTicker('AAPL');
        expect(component.recentTickers).toEqual(['AAPL', 'MSFT']);
        expect(JSON.parse(localStorage.getItem('leap-trader:recent-tickers')!)).toEqual(['AAPL', 'MSFT']);
        component.clearRecentTickers();
        expect(component.recentTickers).toEqual([]);
        expect(localStorage.getItem('leap-trader:recent-tickers')).toBeNull();
      });

      afterEach(() => {
        TestBed.resetTestingModule();
        vi.restoreAllMocks();
      });

      it('fetches existing banks and activity on initialization without adding a bank', () => {
        const { component, api } = createDashboard();
        component.ngOnInit();
        expect(api.getWalletBanks).toHaveBeenCalledWith('1');
        expect(api.getWalletActivity).toHaveBeenCalledWith('1');
        expect(component.bankAccounts).toEqual([{
          id: 7, nickname: 'Existing bank', routingNumber: '123456789', accountNumber: '12345678'
        }]);
        expect(component.walletActivity[0].bankNickname).toBe('Existing bank');
        component.refreshData();
        expect(api.getWalletBanks).toHaveBeenCalledTimes(2);
      });

      it('ignores the previous account response after switching accounts', () => {
        const { component, api, params } = createDashboard();
        const previousBanks = new Subject<typeof bank[]>();
        api.getWalletBanks.mockReturnValueOnce(previousBanks);
        component.ngOnInit();
        api.getWalletBanks.mockReturnValue(of([]));
        api.getWalletActivity.mockReturnValue(of([]));
        params.next(convertToParamMap({ accountId: '2' }));
        previousBanks.next([bank]);
        previousBanks.complete();
        expect(api.getWalletBanks).toHaveBeenLastCalledWith('2');
        expect(component.bankAccounts).toEqual([]);
        expect(component.walletActivity).toEqual([]);
      });

      it('reports load failures and clears the error after a successful retry', () => {
        const { component, api } = createDashboard();
        vi.spyOn(console, 'error').mockImplementation(() => {});
        api.getWalletBanks.mockReturnValueOnce(throwError(() => new Error('Network failure')));
        component.ngOnInit();
        expect(component.walletLoadError).toContain('Unable to load wallet data');
        component.refreshData();
        expect(component.walletLoadError).toBe('');
        expect(component.bankAccounts[0].nickname).toBe('Existing bank');
      });

      it('does not apply pending wallet responses after the dashboard is destroyed', () => {
        const { component, api } = createDashboard();
        const pendingBanks = new Subject<typeof bank[]>();
        api.getWalletBanks.mockReturnValueOnce(pendingBanks);
        component.ngOnInit();
        component.ngOnDestroy();
        pendingBanks.next([bank]);
        pendingBanks.complete();
        expect(component.bankAccounts).toEqual([]);
      });
    });
describe('Stock order position rendering', () => {
  beforeEach(() => {
    vi.spyOn(DashboardComponent.prototype, 'ngOnInit').mockImplementation(() => {});
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { getUser: () => ({ role: 'investor' }) } },
        { provide: Router, useValue: {} },
        { provide: ActivatedRoute, useValue: {} },
        { provide: ApiService, useValue: {} },
        { provide: FinnhubNewsService, useValue: { availability$: of('available') } },
        { provide: PortfolioService, useValue: {} },
        { provide: PortfolioStateService, useValue: {} }
      ]
    });
    Object.defineProperty(window, 'TradingView', {
      configurable: true,
      value: { widget: vi.fn(function () { return { remove: vi.fn() }; }) }
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    Reflect.deleteProperty(window, 'TradingView');
  });

  function createScreen() {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    component.activeView = 'trade';
    component.showOrderModal = true;
    component.selectedTicker = 'AMD';
    component.orderForm = { symbol: 'AMD', side: 'BUY', quantity: 1 };
    component.currentPrice = 150.25;
    component.quoteCurrency = 'USD';
    component.accountState = {
      accountNumber: '1', cash: 10000, orders: [], history: [],
      holdings: [{ symbol: 'amd', shares: 4, avgPrice: 100, currentPrice: 140 }]
    };
    component.portfolio = {
      cash: 10000, totalValue: 10560,
      holdings: [{ symbol: 'amd', quantity: 4, averageCost: 100, currentPrice: 140 }]
    };
    return { fixture, component };
  }

  it('centers the confirmation without changing the order entry layout', async () => {
    const { fixture, component } = createScreen();
    fixture.detectChanges();
    await fixture.whenStable();
    const dialog: HTMLElement = fixture.nativeElement.querySelector('.modal-box');
    expect(dialog.querySelector('.order-placed')).toBeNull();
    expect(dialog.querySelector('app-tradingview-widget')).not.toBeNull();
    expect(dialog.querySelector('.trade-entry-ticket')).not.toBeNull();
    component.orderPlaced = true;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(dialog.querySelector('.trade-holdings')).toBeNull();
    expect(dialog.querySelector('.order-placed')?.textContent).toContain('Order placed');
    expect(dialog.querySelector('.quote-card')).toBeNull();
  });

  it('keeps the chart and side ticket visible when the quote provider fails, with retry and submission disabled', () => {
    const { fixture, component } = createScreen();
    component.quoteError = 'Market data provider is temporarily unavailable';
    component.currentPrice = null;
    fixture.detectChanges();
    const dialog: HTMLElement = fixture.nativeElement.querySelector('.modal-box');
    expect(dialog.querySelector('app-tradingview-widget')).not.toBeNull();
    expect(dialog.querySelector('.trade-entry-ticket')).not.toBeNull();
    const submit = Array.from(dialog.querySelectorAll('button')).find(button => button.textContent?.trim() === 'Place Order');
    expect(submit?.disabled).toBe(true);
    expect(dialog.textContent).toContain('Retry price');
  });

  it('keeps bank inputs digits-only for typing, paste, and input events while preserving zeros', async () => {
    const { fixture, component } = createScreen();
    component.showOrderModal = false;
    component.showBankAccountModal = true;
    fixture.detectChanges();
    await fixture.whenStable();
    for (const field of ['routingNumber', 'accountNumber'] as const) {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(`[name="${field}"]`);
      const key = new KeyboardEvent('keydown', { key: 'e', bubbles: true, cancelable: true });
      input.dispatchEvent(key);
      expect(key.defaultPrevented).toBe(true);
      const paste = new Event('paste', { bubbles: true, cancelable: true });
      Object.defineProperty(paste, 'clipboardData', {
        value: { getData: () => '00ab12-34.56' }
      });
      input.dispatchEvent(paste);
      await fixture.whenStable();
      expect(input.value).toBe('00123456');
      expect(component.bankAccountForm[field]).toBe('00123456');
      input.value = '00x12.34';
      input.dispatchEvent(new Event('input', { bubbles: true }));
      await fixture.whenStable();
      expect(input.value).toBe('001234');
      expect(component.bankAccountForm[field]).toBe('001234');
    }
  });

  it('rejects invalid bank numbers before submission', () => {
    const { component } = createScreen();
    component.bankAccountForm = {
      nickname: 'Checking', routingNumber: '12345678x', accountNumber: '1234'
    };
    component.addBankAccount();
    expect(component.walletFormError).toContain('9-digit routing number');
    expect(component.submittingBankAccount).toBe(false);
  });

  it('renders existing shares and market value, independent of order quantity and side', async () => {
    const { fixture, component } = createScreen();
    component.orderForm.quantity = 10;
    fixture.detectChanges();
    await fixture.whenStable();
    const position: HTMLElement = fixture.nativeElement.querySelector('.trade-holdings');
    expect(position.textContent).toContain('Shares owned');
    expect(position.textContent).toContain('4');
    expect(position.textContent).toContain('USD 601.00');
    component.orderForm.side = 'SELL';
    component.currentPrice = 160;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(position.textContent).toContain('USD 640.00');
    expect(component.ownedTradeShares).toBe(4);
  });

  it('shows zero for a stock not owned, without reusing the previous ticker position', () => {
    const { fixture, component } = createScreen();
    component.selectedTicker = 'MSFT';
    fixture.detectChanges();
    expect(component.ownedTradeShares).toBe(0);
    expect(fixture.nativeElement.querySelector('.trade-holdings').textContent).toContain('USD 0.00');
  });

  it('updates when account holdings change or the account is switched', () => {
    const { component } = createScreen();
    component.portfolio.holdings[0].quantity = 6;
    expect(component.ownedTradeValue).toBe(901.5);
    component.accountState = { ...component.accountState!, accountNumber: '2', holdings: [] };
    component.portfolio.holdings = [];
    expect(component.ownedTradeShares).toBe(0);
    expect(component.ownedTradeValue).toBe(0);
  });

  it('labels loading and failed holdings instead of claiming no shares are owned', async () => {
    const { fixture, component } = createScreen();
    component.accountState = null;
    component.loadingHoldings = true;
    fixture.detectChanges();
    await fixture.whenStable();
    const position: HTMLElement = fixture.nativeElement.querySelector('.trade-holdings');
    expect(position.textContent).toContain('Loading holdings...');
    component.loadingHoldings = false;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(position.textContent).toContain('Holdings unavailable');
    expect(position.textContent).not.toContain('USD 0.00');
  });

  it('keeps shares visible while pricing is loading or unavailable, without using cost basis', async () => {
    const { fixture, component } = createScreen();
    component.loadingQuote = true;
    fixture.detectChanges();
    await fixture.whenStable();
    const position: HTMLElement = fixture.nativeElement.querySelector('.trade-holdings');
    expect(position.textContent).toContain('Shares owned');
    expect(position.textContent).toContain('Loading price...');
    expect(component.ownedTradeValue).toBeNull();
    component.loadingQuote = false;
    component.currentPrice = null;
    component.quoteError = 'Unable to load the market price';
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(position.textContent).toContain('Price unavailable');
    expect(position.textContent).not.toContain('USD 400.00');
  });
});
