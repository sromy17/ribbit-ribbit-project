import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { FinnhubNewsService } from '../../../core/services/finnhub-news.service';
import { PortfolioService } from '../../../core/services/portfolio.service';
import { PortfolioStateService } from '../../../core/services/portfolio-state.service';
import { DashboardComponent } from './dashboard.component';

describe('Stock order position', () => {
  beforeEach(() => {
    vi.spyOn(DashboardComponent.prototype, 'ngOnInit').mockImplementation(() => {});
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { getUser: () => ({ role: 'investor' }) } },
        { provide: Router, useValue: {} },
        { provide: ActivatedRoute, useValue: {} },
        { provide: ApiService, useValue: {} },
        { provide: FinnhubNewsService, useValue: {} },
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

  it('renders existing shares and market value, independent of order quantity and side', async () => {
    const { fixture, component } = createScreen();
    component.orderForm.quantity = 10;
    fixture.detectChanges();
    await fixture.whenStable();
    const position: HTMLElement = fixture.nativeElement.querySelector('.trade-holdings');
    expect(position.textContent).toContain('Shares owned');
    expect(position.textContent).toContain('4');
    expect(position.textContent).toContain('$601.00');
    component.orderForm.side = 'SELL';
    component.currentPrice = 160;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(position.textContent).toContain('$640.00');
    expect(component.ownedTradeShares).toBe(4);
  });

  it('shows zero for a stock not owned, without reusing the previous ticker position', () => {
    const { fixture, component } = createScreen();
    component.selectedTicker = 'MSFT';
    fixture.detectChanges();
    expect(component.ownedTradeShares).toBe(0);
    expect(fixture.nativeElement.querySelector('.trade-holdings').textContent).toContain('$0.00');
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
    expect(position.textContent).not.toContain('$0.00');
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
    expect(position.textContent).not.toContain('$400.00');
  });
});
