import { Injectable } from '@angular/core';
import { PortfolioAccountState } from './portfolio-state.service';

export type PortfolioHistoryRange = '1D' | '1W' | '1M' | '3M' | 'YTD' | '1Y' | 'ALL';
export interface PortfolioHistoryPoint { date: Date; value: number }

@Injectable({ providedIn: 'root' })
export class PortfolioService {
  getAccountHistory(state: PortfolioAccountState, range: PortfolioHistoryRange, now = new Date()): PortfolioHistoryPoint[] {
    const observations = [...new Map(
      state.history.filter(h => h.timestamp <= now).map(h => [h.timestamp.getTime(), h])
    ).values()].sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());
    if (!observations.length) return [];
    const start = new Date(now);
    const subtractMonths = (months: number) => {
      const day = start.getDate();
      start.setDate(1);
      start.setMonth(start.getMonth() - months);
      const lastDay = new Date(start.getFullYear(), start.getMonth() + 1, 0).getDate();
      start.setDate(Math.min(day, lastDay));
    };
    switch (range) {
      case '1D': start.setDate(start.getDate() - 1); break;
      case '1W': start.setDate(start.getDate() - 7); break;
      case '1M': subtractMonths(1); break;
      case '3M': subtractMonths(3); break;
      case 'YTD': start.setMonth(0, 1); start.setHours(0, 0, 0, 0); break;
      case '1Y': subtractMonths(12); break;
      case 'ALL': start.setTime(observations[0].timestamp.getTime()); break;
    }
    // Carry forward the last observed balance, never invent pre-observation history.
    const prior = observations.filter(h => h.timestamp <= start).at(-1);
    const points = observations.filter(h => h.timestamp > start)
      .map(h => ({ date: h.timestamp, value: h.totalValue }));
    if (prior) points.unshift({ date: start, value: prior.totalValue });
    const last = points.at(-1);
    if (last && last.date < now) {
      points.push({ date: now, value: last.value });
    }
    return points;
  }
}
