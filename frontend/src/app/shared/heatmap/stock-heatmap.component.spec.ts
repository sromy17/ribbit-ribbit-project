import { Component, Input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FinnhubNewsArticle, FinnhubNewsService } from '../../core/services/finnhub-news.service';
import { TradingviewWidgetComponent } from '../tradingview/tradingview-widget.component';
import { StockHeatmapComponent } from './stock-heatmap.component';
import { PortfolioStateService, TradeExecutionResult } from '../../core/services/portfolio-state.service';

@Component({ selector: 'app-tradingview-widget', standalone: true, template: '' })
class ChartStub { @Input() symbol = ''; }

describe('Heatmap stock selection', () => {
  const executeTrade = vi.fn();
  beforeEach(() => {
    executeTrade.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: PortfolioStateService, useValue: { executeTrade } }]
    });
    // jsdom has no native dialog implementation.
    HTMLDialogElement.prototype.showModal = function () { this.open = true; };
    HTMLDialogElement.prototype.close = function () { this.open = false; };
  });
  it('shows a ticker, price and uncapped percentage for every stock', () => {
    TestBed.configureTestingModule({ providers: [{ provide: FinnhubNewsService, useValue: { getCompanyNews: () => of([]) } }] });
    const fixture = TestBed.createComponent(StockHeatmapComponent);
    fixture.componentRef.setInput('stocks', Array.from({ length: 50 }, (_, index) => ({
      ticker: `S${index}`, companyName: `Company ${index}`, currentPrice: 100 + index, dailyChange: 12.75
    })));
    fixture.detectChanges();
    const tiles = fixture.nativeElement.querySelectorAll('.tile');
    expect(tiles).toHaveLength(50);
    for (const tile of tiles) {
      expect(tile.querySelector('.ticker')?.textContent).toBeTruthy();
      expect(tile.querySelector('.price')?.textContent).toContain('$');
      expect(tile.querySelector('.change')?.textContent).toContain('+12.75%');
    }
  });
  it('gives stronger gains and losses more area without rewarding price or market cap', () => {
    TestBed.configureTestingModule({ providers: [{ provide: FinnhubNewsService, useValue: { getCompanyNews: () => of([]) } }] });
    const fixture = TestBed.createComponent(StockHeatmapComponent);
    fixture.componentRef.setInput('stocks', [0, .2, 4, -8, 8].map((dailyChange, index) => ({
      ticker: `S${index}`, companyName: `Company ${index}`, currentPrice: 500 - index * 90, dailyChange
    })));
    fixture.detectChanges();
    const area = (ticker: string) => {
      const tile = fixture.componentInstance.tiles.find(tile => tile.ticker === ticker)!;
      return tile.width * tile.height;
    };
    expect(area('S2')).toBeGreaterThan(area('S1'));
    expect(area('S3')).toBeGreaterThan(area('S2'));
    expect(area('S4')).toBeGreaterThan(area('S2'));
    expect(area('S3') / area('S4')).toBeGreaterThan(.9);
    expect(area('S3') / area('S4')).toBeLessThan(1.1);
    expect(area('S0')).toBeGreaterThan(0);
  });
  it('keeps all fifty tiles inside the available viewport after resizing', () => {
    TestBed.configureTestingModule({ providers: [{ provide: FinnhubNewsService, useValue: { getCompanyNews: () => of([]) } }] });
    const fixture = TestBed.createComponent(StockHeatmapComponent);
    fixture.componentRef.setInput('stocks', Array.from({ length: 50 }, (_, index) => ({
      ticker: `S${index}`, companyName: `Company ${index}`, currentPrice: 100, dailyChange: index % 7 - 3
    })));
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const measure = vi.spyOn(component.mapViewport.nativeElement, 'getBoundingClientRect');
    for (const width of [1000, 320]) {
      measure.mockReturnValue(new DOMRect(0, 250, width, 0));
      window.dispatchEvent(new Event('resize'));
      expect(component.layoutWidth).toBe(width);
      expect(component.layoutHeight).toBe(Math.max(180, window.innerHeight - 250 - 24));
      expect(component.tiles).toHaveLength(50);
      for (const tile of component.tiles) {
        expect(tile.left).toBeGreaterThanOrEqual(0);
        expect(tile.top).toBeGreaterThanOrEqual(0);
        expect(tile.left + tile.width).toBeLessThanOrEqual(component.layoutWidth);
        expect(tile.top + tile.height).toBeLessThanOrEqual(component.layoutHeight);
      }
    }
  });
  it('selects another tile after company news fails', () => {
    const log = vi.spyOn(console, 'error').mockImplementation(() => {});
    TestBed.configureTestingModule({
      providers: [{ provide: FinnhubNewsService, useValue: {
        getCompanyNews: vi.fn()
          .mockReturnValueOnce(throwError(() => new Error('Finnhub rejected the API key.')))
          .mockReturnValueOnce(of([]))
      } }]
    }).overrideComponent(StockHeatmapComponent, {
      remove: { imports: [TradingviewWidgetComponent] }, add: { imports: [ChartStub] }
    });
    const fixture = TestBed.createComponent(StockHeatmapComponent);
    fixture.componentRef.setInput('stocks', ['ALLE', 'AAPL'].map(ticker => ({
      ticker, companyName: ticker, currentPrice: 100, dailyChange: 1
    })));
    fixture.detectChanges();
    const nodes: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.tile');
    nodes[0].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.companyNewsError).toContain('API key');
    fixture.componentInstance.closeDetails();
    nodes[1].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedStock?.ticker).toBe('AAPL');
    expect(fixture.componentInstance.companyNewsError).toBe('');
    expect(nodes[1].getAttribute('aria-pressed')).toBe('true');
    fixture.destroy();
    log.mockRestore();
  });
  it('allows repeated selection without rebuilding tiles and cancels stale news', () => {
    const a = new Subject<FinnhubNewsArticle[]>();
    const b = new Subject<FinnhubNewsArticle[]>();
    TestBed.configureTestingModule({
      providers: [{ provide: FinnhubNewsService, useValue: {
        getCompanyNews: vi.fn().mockReturnValueOnce(a).mockReturnValueOnce(b)
      } }]
    }).overrideComponent(StockHeatmapComponent, {
      remove: { imports: [TradingviewWidgetComponent] }, add: { imports: [ChartStub] }
    });
    const fixture = TestBed.createComponent(StockHeatmapComponent);
    fixture.componentRef.setInput('stocks', ['AAPL', 'MSFT'].map(ticker => ({
      ticker, companyName: ticker, currentPrice: 100, dailyChange: 1
    })));
    fixture.detectChanges();
    const tiles = fixture.componentInstance.tiles;
    const nodes: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.tile');
    nodes[0].click();
    fixture.detectChanges();
    expect(a.observed).toBe(true);
    fixture.componentInstance.closeDetails();
    nodes[1].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedStock?.ticker).toBe('MSFT');
    expect(fixture.componentInstance.tiles).toBe(tiles);
    expect(a.observed).toBe(false);
    expect(b.observed).toBe(true);
    fixture.componentInstance.tradeSelectedStock();
    expect(fixture.componentInstance.selectedStock?.ticker).toBe('MSFT');
    expect(fixture.componentInstance.showTrade).toBe(true);
    expect(fixture.componentInstance.detailsDialog.nativeElement.open).toBe(true);
    expect(fixture.componentInstance.tradePrice).toBe(100);
    expect(fixture.componentInstance.tradeSide).toBe('BUY');
    expect(fixture.componentInstance.tradeQuantity).toBe(1);
    const pane: HTMLElement = fixture.nativeElement.querySelector('.heatmap-pane');
    expect(pane.contains(fixture.nativeElement.querySelector('.details-drawer'))).toBe(false);
    fixture.componentInstance.closeDetails();
    expect(b.observed).toBe(false);
    fixture.destroy();
  });

  it('opens and closes the side ticket without replacing or covering the chart with another dialog', async () => {
    TestBed.configureTestingModule({
      providers: [{ provide: FinnhubNewsService, useValue: { getCompanyNews: () => of([]) } }]
    }).overrideComponent(StockHeatmapComponent, {
      remove: { imports: [TradingviewWidgetComponent] }, add: { imports: [ChartStub] }
    });
    const fixture = TestBed.createComponent(StockHeatmapComponent);
    fixture.componentRef.setInput('accountNumber', '301089');
    fixture.componentRef.setInput('stocks', [{
      ticker: 'AMD', companyName: 'AMD', currentPrice: 150, dailyChange: 1
    }]);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.tile').click();
    fixture.detectChanges();
    await fixture.whenStable();
    const component = fixture.componentInstance;
    const chart = fixture.nativeElement.querySelector('app-tradingview-widget');
    fixture.nativeElement.querySelector('.trade-stock-button').click();
    fixture.detectChanges();
    await fixture.whenStable();
    const content: HTMLElement = fixture.nativeElement.querySelector('.stock-details-content');
    expect(content.classList.contains('with-trade')).toBe(true);
    expect(content.querySelector('.quick-trade-panel form')).not.toBeNull();
    expect(content.querySelector('app-tradingview-widget')).toBe(chart);
    expect(fixture.nativeElement.querySelectorAll('dialog[open]')).toHaveLength(1);
    component.tradeQuantity = 3;
    component.tradeSelectedStock();
    expect(component.tradeQuantity).toBe(3);
    fixture.nativeElement.querySelector('.ticket-close').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(content.querySelector('.quick-trade-panel')).toBeNull();
    expect(content.querySelector('app-tradingview-widget')).toBe(chart);
    expect(component.detailsDialog.nativeElement.open).toBe(true);
    fixture.destroy();
  });

  it('executes an inline ticket once and keeps the stock dialog and tiles intact', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: FinnhubNewsService, useValue: { getCompanyNews: () => of([]) } }]
    }).overrideComponent(StockHeatmapComponent, {
      remove: { imports: [TradingviewWidgetComponent] }, add: { imports: [ChartStub] }
    });
    const fixture = TestBed.createComponent(StockHeatmapComponent);
    fixture.componentRef.setInput('accountNumber', '301089');
    fixture.componentRef.setInput('stocks', [{ ticker: 'MMM', companyName: '3M', currentPrice: 175.21, dailyChange: 1 }]);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.tile').click();
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const tiles = component.tiles;
    component.tradeSelectedStock();
    component.tradeQuantity = 2;
    expect(component.tradeSubtotal).toBe(350.42);
    expect(component.tradeFee).toBe(1);
    expect(component.tradeTotal).toBe(351.42);
    component.tradeSide = 'SELL';
    expect(component.tradeTotal).toBe(349.42);
    component.tradeSide = 'BUY';
    component.tradeQuantity = 0;
    component.placeQuickOrder();
    expect(executeTrade).not.toHaveBeenCalled();
    component.tradeQuantity = 2;
    const result = new Subject<TradeExecutionResult>();
    executeTrade.mockReturnValue(result);
    component.placeQuickOrder();
    component.placeQuickOrder();
    expect(executeTrade).toHaveBeenCalledExactlyOnceWith('301089', {
      ticker: 'MMM', side: 'BUY', shares: 2, price: 175.21
    });
    component.closeTrade();
    expect(component.showTrade).toBe(true);
    result.next({ order: { orderId: 1, status: 'EXECUTED', executedQuantity: 2, executionPrice: 176 } });
    fixture.detectChanges();
    expect(component.showTrade).toBe(false);
    expect(component.detailsDialog.nativeElement.open).toBe(true);
    expect(component.selectedStock?.ticker).toBe('MMM');
    expect(component.tiles).toBe(tiles);
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Bought 2 shares of MMM @ $176.00');
    fixture.destroy();
  });

  it('shows rejection and uncertain order errors without reporting success', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: FinnhubNewsService, useValue: { getCompanyNews: () => of([]) } }]
    }).overrideComponent(StockHeatmapComponent, {
      remove: { imports: [TradingviewWidgetComponent] }, add: { imports: [ChartStub] }
    });
    const fixture = TestBed.createComponent(StockHeatmapComponent);
    fixture.componentRef.setInput('accountNumber', '301089');
    fixture.componentRef.setInput('stocks', [{ ticker: 'MMM', companyName: '3M', currentPrice: 175.21, dailyChange: 1 }]);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.tile').click();
    const component = fixture.componentInstance;
    component.tradeSelectedStock();
    executeTrade.mockReturnValue(of({ order: { orderId: 1, status: 'REJECTED' } }));
    component.placeQuickOrder();
    expect(component.tradeError).toContain('rejected');
    expect(component.tradeNotice).toBe('');
    expect(component.showTrade).toBe(true);
    const log = vi.spyOn(console, 'error').mockImplementation(() => {});
    executeTrade.mockReturnValue(throwError(() => new Error('offline')));
    component.placeQuickOrder();
    expect(component.tradeError).toContain('Check History');
    expect(component.tradeLocked).toBe(true);
    expect(component.tradeNotice).toBe('');
    log.mockRestore();
    fixture.destroy();
  });

  it('recreates working tile selections after navigating away and back', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: FinnhubNewsService, useValue: { getCompanyNews: () => of([]) } }]
    }).overrideComponent(StockHeatmapComponent, {
      remove: { imports: [TradingviewWidgetComponent] }, add: { imports: [ChartStub] }
    });
    for (const ticker of ['AAPL', 'MSFT']) {
      const fixture = TestBed.createComponent(StockHeatmapComponent);
      fixture.componentRef.setInput('stocks', [{ ticker, companyName: ticker, currentPrice: 100, dailyChange: 1 }]);
      fixture.detectChanges();
      fixture.nativeElement.querySelector('.tile').click();
      fixture.detectChanges();
      expect(fixture.componentInstance.selectedStock?.ticker).toBe(ticker);
      expect(fixture.nativeElement.querySelector('dialog').open).toBe(true);
      fixture.nativeElement.querySelector('dialog').dispatchEvent(new Event('cancel'));
      fixture.detectChanges();
      expect(fixture.componentInstance.selectedStock).toBeNull();
      expect(fixture.nativeElement.querySelector('dialog').open).toBe(false);
      fixture.destroy();
    }
  });
});
