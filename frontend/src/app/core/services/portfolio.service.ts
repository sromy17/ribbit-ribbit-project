import { Injectable } from '@angular/core';
import { PortfolioAccountState } from './portfolio-state.service';

export type PortfolioHistoryRange = '1D' | '1W' | '1M' | '3M' | 'YTD' | '1Y' | 'ALL';
export interface PortfolioHistoryPoint { date: Date; value: number }

@Injectable({ providedIn: 'root' })
export class PortfolioService {
  getAccountHistory(state: PortfolioAccountState, range: PortfolioHistoryRange, now = new Date()): PortfolioHistoryPoint[] {
    if (!state.history.length) return [];
    const start = new Date(now);
    switch (range) {
      case '1D': start.setDate(start.getDate() - 1); break;
      case '1W': start.setDate(start.getDate() - 7); break;
      case '1M': start.setMonth(start.getMonth() - 1); break;
      case '3M': start.setMonth(start.getMonth() - 3); break;
      case 'YTD': start.setMonth(0, 1); start.setHours(0, 0, 0, 0); break;
      case '1Y': start.setFullYear(start.getFullYear() - 1); break;
      case 'ALL': start.setTime(state.history[0].timestamp.getTime()); break;
    }
    // Carry forward the last observed balance, never invent pre-observation history.
    const prior = state.history.filter(h => h.timestamp <= start).at(-1);
    const points = state.history.filter(h => h.timestamp > start && h.timestamp <= now)
      .map(h => ({ date: h.timestamp, value: h.totalValue }));
    if (prior) points.unshift({ date: start, value: prior.totalValue });
    const last = points.at(-1);
    if (last && (last.date < now || points.length === 1)) {
      points.push({ date: now, value: last.value });
    }
    return points;
  }
}
