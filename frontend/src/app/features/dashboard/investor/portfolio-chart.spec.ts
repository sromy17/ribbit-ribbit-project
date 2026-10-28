import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { FinnhubNewsService } from '../../../core/services/finnhub-news.service';
import { PortfolioService } from '../../../core/services/portfolio.service';
import { PortfolioStateService } from '../../../core/services/portfolio-state.service';
import { DashboardComponent } from './dashboard.component';

describe('Portfolio balance chart', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-28T12:00:00Z'));
    vi.spyOn(DashboardComponent.prototype, 'ngOnInit').mockImplementation(() => {});
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { getUser: () => ({ role: 'investor' }) } },
        { provide: Router, useValue: {} },
        { provide: ActivatedRoute, useValue: {} },
        { provide: ApiService, useValue: {} },
        { provide: FinnhubNewsService, useValue: {} },
        PortfolioService,
        { provide: PortfolioStateService, useValue: {} }
      ]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  function chart(values: number[]) {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    component.accountState = {
      accountNumber: '1', cash: values.at(-1) ?? 0, holdings: [], orders: [],
      history: values.map((totalValue, index) => ({
        timestamp: new Date(Date.UTC(2026, 9, 25 + index, 12)), totalValue
      }))
    };
    component.setPortfolioRange('ALL');
    return { fixture, component };
  }

  it.each([[100, 125], [100, 75], [0, 0], [-100, -90]])('keeps values within finite chart bounds: %s to %s', (first, last) => {
    const { component } = chart([first, last]);
    expect(component.historyPriceTicks).toHaveLength(5);
    for (const point of component.portfolioHistory) {
      expect(component.getChartY(point.value)).toBeGreaterThanOrEqual(0);
      expect(component.getChartY(point.value)).toBeLessThanOrEqual(360);
    }
    expect(component.getPortfolioLinePath()).not.toMatch(/NaN|Infinity/);
    expect(component.getChartX(0)).toBe(0);
    expect(component.getChartX(component.portfolioHistory.length - 1)).toBe(1000);
  });

  it('uses the same theme color for rising/falling lines and gradient fills', async () => {
    const { fixture, component } = chart([100, 125]);
    fixture.detectChanges();
    await fixture.whenStable();
    const stop: SVGStopElement = fixture.nativeElement.querySelector('linearGradient stop');
    const line: SVGPathElement = fixture.nativeElement.querySelector('.portfolio-line');
    expect(stop.getAttribute('stop-color')).toBe('var(--ui-portfolio-up)');
    expect(line.style.stroke).toBe('var(--ui-portfolio-up)');
    expect(fixture.nativeElement.querySelector('.portfolio-area').getAttribute('fill')).toBe('url(#portfolio-history-fill)');
    component.accountState!.history[1].totalValue = 75;
    const rangeButtons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('.portfolio-range-tabs button');
    Array.from(rangeButtons).find(button => button.textContent?.trim() === 'ALL')!.click();
    await fixture.whenStable();
    expect(component.getHistoryChange()).toBe(-25);
    expect(component.getHistoryColor()).toBe('var(--ui-portfolio-down)');
    expect(fixture.nativeElement.querySelector('.portfolio-line').style.stroke).toBe('var(--ui-portfolio-down)');
    expect(fixture.nativeElement.querySelector('linearGradient stop').getAttribute('stop-color')).toBe('var(--ui-portfolio-down)');
  });

  it('shows selected-point changes, not final-period changes, on keyboard focus', () => {
    const { fixture, component } = chart([100, 110, 130]);
    fixture.detectChanges();
    const point: SVGCircleElement = fixture.nativeElement.querySelectorAll('.portfolio-hover-point')[1];
    point.dispatchEvent(new FocusEvent('focus'));
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    const tooltip: HTMLElement = fixture.nativeElement.querySelector('.portfolio-chart-tooltip');
    expect(tooltip.textContent).toContain('$110.00');
    expect(tooltip.textContent).toContain('+$10.00');
    expect(tooltip.textContent).toContain('+10.00%');
    expect(component.getHistoryChange()).toBe(30);
    expect(point.getAttribute('tabindex')).toBe('0');
    expect(point.getAttribute('aria-label')).toContain('$110.00');
  });

  it('does not present a zero-start deposit as a zero-percent return', () => {
    const { component } = chart([0, 1000]);
    expect(component.getHistoryChange()).toBe(1000);
    expect(component.getHistoryChangePercent()).toBeNull();
    expect(component.formatHistoryChangePercent()).toBe('N/A');
  });

  it('respects a genuine zero quote when recalculating account value', () => {
    const { component } = chart([]);
    component.portfolio.cash = 50;
    component.portfolio.holdings = [{ symbol: 'AMD', quantity: 2, averageCost: 100, currentPrice: 0 }];
    component.updateTotalValue();
    expect(component.getTotalValue()).toBe(50);
  });

  it('provides an explicit empty history state', () => {
    const { fixture } = chart([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.portfolio-chart-canvas')).toBeNull();
    expect(fixture.nativeElement.querySelector('.portfolio-chart').textContent).toContain('No recorded balance snapshots');
  });

  it('uses time-of-day ticks for an exact one-day range and timestamps in point labels', () => {
    const { fixture, component } = chart([100, 110]);
    component.setPortfolioRange('1D');
    const now = new Date();
    const start = new Date(now);
    start.setDate(start.getDate() - 1);
    expect(component.historyDateTicks).toEqual([0, 0.5, 1].map(fraction =>
      new Date(start.getTime() + fraction * (now.getTime() - start.getTime()))
        .toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })));
    expect(component.formatHistoryDate(now)).toBe(now.toLocaleString(undefined, {
      month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit'
    }));
    fixture.detectChanges();
    const selected: HTMLButtonElement = fixture.nativeElement.querySelector('.portfolio-range-tabs [aria-pressed="true"]');
    expect(selected.textContent?.trim()).toBe('1D');
  });
});
