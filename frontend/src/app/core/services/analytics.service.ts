import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';

export interface AnalyticsSummary {
  orders: number; executedOrders: number; fills: number; executedShares: number;
  buyOrders: number; sellOrders: number; activeClients: number; activeAccounts: number;
  totalAccounts: number; rejectedOrders: number; cancelledOrders: number; pendingOrders: number;
  executionRate: number | null; meanOrderSize: number | null; medianOrderSize: number | null;
  modeOrderSizes: number[]; modeFrequency: number; availableCash: number;
  supportTickets: number; openSupportTickets: number; ordersWithDisputes: number;
  ordersWithOpenDisputes: number; firstOrderAt: string | null; lastOrderAt: string | null;
}
export interface AnalyticsReport {
  retrievedAt: string; lastSyncedAt: string | null; summary: AnalyticsSummary;
  topStocks: { symbol: string; name: string; orders: number; executedShares: number;
    buyExecutedShares: number; sellExecutedShares: number; clients: number }[];
  dailyActivity: { date: string; orders: number; executedShares: number }[];
  insights: AnalyticsInsights;
}

export type AnalyticsPeriod = 'ALL' | 'LAST_30_DAYS' | 'LAST_90_DAYS';
export interface AnalyticsInsights {
  period: AnalyticsPeriod; fromDate: string; toDate: string;
  funding: { deposits: number; withdrawals: number; netDeposits: number; depositCount: number;
    depositingClients: number; meanDeposit: number | null; medianDeposit: number | null };
  depositDistribution: { label: string; deposits: number }[];
  topStocks: AnalyticsReport['topStocks'];
  stockActivity: { week: string; symbol: string; orders: number; clients: number }[];
  execution: { requestedShares: number; executedShares: number; shareFillRate: number | null;
    outcomes: { label: string; orders: number; percentage: number | null }[] };
  participation: { newAccounts: number; eligibleAccounts: number; tradingAccounts: number; tradingAccountRate: number | null };
  weeks: { week: string; deposits: number; withdrawals: number; netDeposits: number; depositingClients: number;
    meanDeposit: number | null; medianDeposit: number | null; newAccounts: number; eligibleAccounts: number;
    tradingAccounts: number; tradingAccountRate: number | null }[];
}

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  constructor(private readonly http: HttpClient) {}

  overview(period: AnalyticsPeriod = 'ALL') {
    return this.http.get<AnalyticsReport>('/api/analyst/overview',
      { params: period === 'ALL' ? {} : { period } }).pipe(timeout(30000));
  }

  refreshReporting() {
    // Do not time out/retry a mutation: its server-side outcome may still be pending.
    return this.http.post<{ success: boolean; message: string }>('/api/etl/run', {});
  }
}
