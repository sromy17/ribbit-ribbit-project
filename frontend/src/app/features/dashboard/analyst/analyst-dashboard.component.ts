import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { UiEmptyStateComponent, UiPageHeadingComponent, UiWorkspaceComponent } from '../../../shared/ui';
import { AnalyticsPeriod, AnalyticsReport, AnalyticsService } from '../../../core/services/analytics.service';
import { Subscription } from 'rxjs';
import { AnalyticsChartComponent, ChartPoint, ChartSeries } from './analytics-chart.component';

@Component({
  selector: 'app-analyst-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, AnalyticsChartComponent, UiWorkspaceComponent, UiPageHeadingComponent, UiEmptyStateComponent],
  templateUrl: './analyst-dashboard.component.html',
  styleUrls: ['../../../shared/ui/staff-dashboard.css', './analyst-dashboard.component.css']
})
export class AnalystDashboardComponent implements OnInit, OnDestroy {
  readonly currentUser;
  report: AnalyticsReport | null = null;
  loading = false;
  refreshing = false;
  error = '';
  refreshError = '';
  notice = '';
  period: AnalyticsPeriod = 'ALL';
  fundingPoints: ChartPoint[] = [];
  distributionPoints: ChartPoint[] = [];
  outcomePoints: ChartPoint[] = [];
  growthPoints: ChartPoint[] = [];
  participationPoints: ChartPoint[] = [];
  stockWeeks: { week: string; cells: { orders: number; clients: number; intensity: number }[] }[] = [];
  readonly fundingSeries: ChartSeries[] = [
    { label: 'Deposits', kind: 'bar' }, { label: 'Withdrawals', kind: 'bar' }, { label: 'Net deposits', kind: 'line' }
  ];
  readonly depositSeries: ChartSeries[] = [{ label: 'Deposits', kind: 'bar' }];
  readonly outcomeSeries: ChartSeries[] = [{ label: 'Orders', kind: 'bar' }];
  readonly growthSeries: ChartSeries[] = [
    { label: 'New accounts', kind: 'bar' }, { label: 'Trading accounts', kind: 'line' }
  ];
  readonly participationSeries: ChartSeries[] = [{ label: 'Trading account participation', kind: 'line' }];
  private readRequest?: Subscription;
  private refreshRequest?: Subscription;

  constructor(private readonly auth: AuthService, private readonly router: Router,
    private readonly analytics: AnalyticsService, private readonly cdr: ChangeDetectorRef) {
    this.currentUser = auth.getUser();
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.readRequest?.unsubscribe();
    this.loading = true;
    this.error = '';
    this.report = null;
    this.readRequest = this.analytics.overview(this.period).subscribe({
      next: report => {
        this.report = report;
        this.prepareCharts(report);
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.error = error.error?.message || 'Unable to load reporting data. Check OLAP and retry.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private prepareCharts(report: AnalyticsReport): void {
    const data = report.insights;
    this.fundingPoints = data.weeks.map(week => ({ label: week.week, values: [week.deposits, week.withdrawals, week.netDeposits] }));
    this.distributionPoints = data.depositDistribution.map(bucket => ({ label: bucket.label, values: [bucket.deposits] }));
    this.outcomePoints = data.execution.outcomes.map(outcome => ({ label: outcome.label, values: [outcome.orders] }));
    this.growthPoints = data.weeks.map(week => ({ label: week.week, values: [week.newAccounts, week.tradingAccounts] }));
    this.participationPoints = data.weeks.map(week => ({ label: week.week, values: [week.tradingAccountRate] }));
    const activity = new Map(data.stockActivity.map(row => [`${row.week}:${row.symbol}`, row]));
    const max = Math.max(1, ...data.stockActivity.map(row => row.orders));
    this.stockWeeks = data.weeks.map(week => ({
      week: week.week, cells: data.topStocks.map(stock => {
        const row = activity.get(`${week.week}:${stock.symbol}`);
        return { orders: row?.orders ?? 0, clients: row?.clients ?? 0, intensity: (row?.orders ?? 0) / max };
      })
    }));
  }

  stockWidth(shares: number): number {
    const max = this.report?.insights.topStocks[0]?.executedShares ?? 0;
    return max > 0 ? shares * 100 / max : 0;
  }

  refreshReporting(): void {
    if (this.refreshing) return;
    this.refreshing = true;
    this.refreshError = '';
    this.notice = '';
    this.refreshRequest = this.analytics.refreshReporting().subscribe({
      next: result => {
        this.refreshing = false;
        if (!result.success) {
          this.refreshError = result.message || 'Reporting refresh failed.';
        } else {
          this.notice = 'Reporting refresh committed successfully.';
          this.load();
        }
        this.cdr.markForCheck();
      },
      error: error => {
        this.refreshing = false;
        this.refreshError = error.error?.message ||
          'Refresh could not be confirmed. Check ETL logs and reload analytics before retrying.';
        this.cdr.markForCheck();
      }
    });
  }

  ngOnDestroy(): void {
    this.readRequest?.unsubscribe();
    this.refreshRequest?.unsubscribe();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/employee-login']);
  }
}
