import { describe, expect, it } from 'vitest';
import { PortfolioHistoryRange, PortfolioService } from './portfolio.service';
import { PortfolioAccountState } from './portfolio-state.service';

describe('Portfolio snapshot history ranges', () => {
  const service = new PortfolioService();
  const state = (history: PortfolioAccountState['history']): PortfolioAccountState => ({
    accountNumber: '1', cash: 0, holdings: [], orders: [], history
  });

  it.each([
    ['1D', new Date(2026, 2, 30, 12)],
    ['1W', new Date(2026, 2, 24, 12)],
    ['1M', new Date(2026, 1, 28, 12)],
    ['3M', new Date(2025, 11, 31, 12)],
    ['YTD', new Date(2026, 0, 1)],
    ['1Y', new Date(2025, 2, 31, 12)],
    ['ALL', new Date(2024, 0, 1)]
  ] satisfies Array<[PortfolioHistoryRange, Date]>)('uses the correct %s boundary, including month-end clamping', (range, expected) => {
    const now = new Date(2026, 2, 31, 12);
    const points = service.getAccountHistory(state([
      { timestamp: new Date(2024, 0, 1), totalValue: 100 }
    ]), range, now);
    expect(points).toEqual([{ date: expected, value: 100 }, { date: now, value: 100 }]);
  });

  it('clamps a leap-day year range to February 28 rather than March 1', () => {
    const points = service.getAccountHistory(state([
      { timestamp: new Date(2020, 0, 1), totalValue: 100 }
    ]), '1Y', new Date(2024, 1, 29, 12));
    expect(points[0].date).toEqual(new Date(2023, 1, 28, 12));
  });

  it('sorts snapshots, uses the last observation at a duplicate time and excludes future data', () => {
    const points = service.getAccountHistory(state([
      { timestamp: new Date(2026, 9, 3), totalValue: 90 },
      { timestamp: new Date(2026, 9, 1), totalValue: 100 },
      { timestamp: new Date(2026, 9, 3), totalValue: 110 },
      { timestamp: new Date(2026, 9, 9), totalValue: 999 }
    ]), 'ALL', new Date(2026, 9, 4));
    expect(points.map(p => p.value)).toEqual([100, 110, 110]);
    expect(points.map(p => p.date.getDate())).toEqual([1, 3, 4]);
  });

  it('never invents pre-observation values or duplicates a snapshot at now', () => {
    const now = new Date(2026, 9, 28, 12);
    expect(service.getAccountHistory(state([{ timestamp: now, totalValue: 0 }]), '1M', now))
      .toEqual([{ date: now, value: 0 }]);
    expect(service.getAccountHistory(state([]), 'ALL', now)).toEqual([]);
    expect(service.getAccountHistory(state([
      { timestamp: new Date(2027, 0, 1), totalValue: 10 }
    ]), '1D', now)).toEqual([]);
  });
});
