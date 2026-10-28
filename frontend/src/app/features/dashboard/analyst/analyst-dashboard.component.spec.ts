import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService, User } from '../../../core/services/auth.service';
import { AuthGuard } from '../../../core/guards/auth.guard';
import { AnalystDashboardComponent } from './analyst-dashboard.component';
import { AnalyticsReport } from '../../../core/services/analytics.service';

const report: AnalyticsReport = {
  retrievedAt: '2026-10-28T15:00:00Z', lastSyncedAt: '2026-10-28T14:59:00Z',
  summary: {
    orders: 8, executedOrders: 4, fills: 6, executedShares: 58, buyOrders: 6, sellOrders: 2,
    activeClients: 5, activeAccounts: 5, totalAccounts: 5, rejectedOrders: 2, cancelledOrders: 1,
    pendingOrders: 1, executionRate: 50, meanOrderSize: 13.125, medianOrderSize: 11,
    modeOrderSizes: [10, 25], modeFrequency: 2, availableCash: 975000, supportTickets: 3,
    openSupportTickets: 2, ordersWithDisputes: 3, ordersWithOpenDisputes: 3,
    firstOrderAt: '2024-09-20T10:00:00Z', lastOrderAt: '2026-10-28T10:00:00Z'
  },
  topStocks: [{ symbol: 'MSFT', name: 'Microsoft', orders: 1, executedShares: 25,
    buyExecutedShares: 25, sellExecutedShares: 0, clients: 1 }],
  dailyActivity: [{ date: '2026-10-28', orders: 1, executedShares: 0 }],
  insights: {
    period: 'ALL', fromDate: '2024-09-20', toDate: '2026-10-28',
    funding: { deposits: 75000, withdrawals: 2500, netDeposits: 72500, depositCount: 3,
      depositingClients: 3, meanDeposit: 25000, medianDeposit: 25000 },
    depositDistribution: [{ label: 'Under $100', deposits: 0 }, { label: '$10,000 to under $100,000', deposits: 3 }],
    topStocks: [{ symbol: 'MSFT', name: 'Microsoft', orders: 1, executedShares: 25,
      buyExecutedShares: 25, sellExecutedShares: 0, clients: 1 }],
    stockActivity: [{ week: '2024-09-16', symbol: 'MSFT', orders: 1, clients: 1 }],
    execution: { requestedShares: 105, executedShares: 58, shareFillRate: 55.24,
      outcomes: [{ label: 'Fully filled', orders: 4, percentage: 50 }, { label: 'Unfilled', orders: 1, percentage: 12.5 }] },
    participation: { newAccounts: 5, eligibleAccounts: 5, tradingAccounts: 5, tradingAccountRate: 100 },
    weeks: [{ week: '2024-09-16', deposits: 75000, withdrawals: 0, netDeposits: 75000, depositingClients: 3,
      meanDeposit: 25000, medianDeposit: 25000, newAccounts: 5, eligibleAccounts: 5, tradingAccounts: 4, tradingAccountRate: 80 },
    { week: '2024-09-23', deposits: 0, withdrawals: 2500, netDeposits: -2500, depositingClients: 0,
      meanDeposit: null, medianDeposit: null, newAccounts: 0, eligibleAccounts: 5, tradingAccounts: 1, tradingAccountRate: 20 }]
  }
};

describe('OLAP analyst dashboard', () => {
  let user: User | null;
  const router = { navigate: vi.fn(), createUrlTree: vi.fn(() => 'login-redirect') };
  const logout = vi.fn();
  let http: HttpTestingController;

  beforeEach(() => {
    user = { id: '1', username: 'analyst@example.com', firstName: 'Test', lastName: 'User', role: 'analyst' };
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { getUser: () => user, logout } },
      { provide: Router, useValue: router }
    ] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    TestBed.resetTestingModule();
  });

  it('renders real report metrics, rankings, all tied modes and their definitions', async () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading analytics');
    expect(fixture.nativeElement.textContent).not.toContain('Submitted orders');
    http.expectOne('/api/analyst/overview').flush(report);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Commercial analytics');
    expect(fixture.nativeElement.textContent).toContain('Activity overview');
    expect(fixture.nativeElement.textContent).toContain('10, 25');
    expect(fixture.nativeElement.textContent).toContain('13.13');
    expect(fixture.nativeElement.textContent).toContain('50%');
    expect(fixture.nativeElement.textContent).toContain('MSFT');
    expect(fixture.nativeElement.textContent).toContain('not fill day');
    expect(fixture.nativeElement.textContent).toContain('not all historical claims');
    expect(fixture.nativeElement.querySelectorAll('app-analytics-chart svg')).toHaveLength(5);
    expect(fixture.nativeElement.textContent).toContain('$72,500.00');
    expect(fixture.nativeElement.textContent).toContain('55.24%');
    expect(fixture.nativeElement.textContent).toContain('orders / clients');
    expect(fixture.componentInstance.fundingPoints[1].values).toEqual([0, 2500, -2500]);
    expect(fixture.componentInstance.stockWeeks[1].cells[0]).toEqual({ orders: 0, clients: 0, intensity: 0 });
  });

  it('refreshes OLTP to OLAP only on request, prevents duplicates and reloads after confirmed success', () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    http.expectOne('/api/analyst/overview').flush(report);
    const component = fixture.componentInstance;
    component.refreshReporting();
    component.refreshReporting();
    const refresh = http.expectOne('/api/etl/run');
    expect(refresh.request.method).toBe('POST');
    expect(component.refreshing).toBe(true);
    http.expectNone('/api/analyst/overview');
    refresh.flush({ success: true, message: 'Completed' });
    http.expectOne('/api/analyst/overview').flush(report);
    expect(component.refreshing).toBe(false);
    expect(component.notice).toContain('committed successfully');
  });

  it('does not claim success or replace the previous report after refresh failure', () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    http.expectOne('/api/analyst/overview').flush(report);
    const component = fixture.componentInstance;
    component.refreshReporting();
    http.expectOne('/api/etl/run').flush({ success: false, message: 'Missing OLAP tables' },
      { status: 503, statusText: 'Unavailable' });
    expect(component.refreshError).toContain('Missing OLAP');
    expect(component.notice).toBe('');
    expect(component.report).toEqual(report);
    http.expectNone('/api/analyst/overview');
    component.refreshReporting();
    http.expectOne('/api/etl/run').flush({ success: false, message: 'Refresh rolled back' });
    expect(component.refreshError).toBe('Refresh rolled back');
    http.expectNone('/api/analyst/overview');
  });

  it('clears stale reports on read failures and supports retry', () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    http.expectOne('/api/analyst/overview').flush({}, { status: 503, statusText: 'Unavailable' });
    const component = fixture.componentInstance;
    expect(component.error).toContain('Unable to load');
    expect(component.report).toBeNull();
    component.load();
    http.expectOne('/api/analyst/overview').flush(report);
    expect(component.error).toBe('');
    expect(component.report?.summary.orders).toBe(8);
  });

  it('treats a lost refresh response as unconfirmed and never retries the mutation', () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    http.expectOne('/api/analyst/overview').flush(report);
    fixture.componentInstance.refreshReporting();
    http.expectOne('/api/etl/run').error(new ProgressEvent('error'));
    expect(fixture.componentInstance.refreshError).toContain('could not be confirmed');
    expect(fixture.componentInstance.refreshing).toBe(false);
    expect(fixture.componentInstance.notice).toBe('');
    http.expectNone('/api/etl/run');
    http.expectNone('/api/analyst/overview');
  });

  it('shows empty statistics as N/A and does not invent rankings or a refresh timestamp', async () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    http.expectOne('/api/analyst/overview').flush({ ...report, lastSyncedAt: null, topStocks: [], dailyActivity: [],
      insights: { ...report.insights, topStocks: [], stockActivity: [],
        funding: { deposits: 0, withdrawals: 0, netDeposits: 0, depositCount: 0, depositingClients: 0, meanDeposit: null, medianDeposit: null },
        execution: { requestedShares: 0, executedShares: 0, shareFillRate: null, outcomes: [] } },
      summary: { ...report.summary, orders: 0, meanOrderSize: null, medianOrderSize: null,
        executionRate: null, modeOrderSizes: [], modeFrequency: 0 } });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('N/A');
    expect(fixture.nativeElement.textContent).toContain('No repeated size');
    expect(fixture.nativeElement.textContent).toContain('No executed stock activity');
    expect(fixture.nativeElement.textContent).toContain('does not prove a successful refresh');
    expect(fixture.nativeElement.textContent).not.toContain('MSFT');
  });

  it('loads the selected period, cancels stale reads and keeps it after a successful refresh', () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    const old = http.expectOne('/api/analyst/overview');
    const component = fixture.componentInstance;
    component.period = 'LAST_30_DAYS';
    component.load();
    expect(old.cancelled).toBe(true);
    http.expectOne('/api/analyst/overview?period=LAST_30_DAYS').flush({
      ...report, insights: { ...report.insights, period: 'LAST_30_DAYS' }
    });
    component.refreshReporting();
    http.expectOne('/api/etl/run').flush({ success: true, message: 'Completed' });
    http.expectOne('/api/analyst/overview?period=LAST_30_DAYS').flush(report);
    expect(component.period).toBe('LAST_30_DAYS');
  });

  it('cancels component requests on destruction without retrying ETL', () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    const read = http.expectOne('/api/analyst/overview');
    fixture.componentInstance.refreshReporting();
    const write = http.expectOne('/api/etl/run');
    fixture.destroy();
    expect(read.cancelled).toBe(true);
    expect(write.cancelled).toBe(true);
  });

  it('preserves sign-out behavior', () => {
    const fixture = TestBed.createComponent(AnalystDashboardComponent);
    fixture.detectChanges();
    http.expectOne('/api/analyst/overview').flush(report);
    fixture.nativeElement.querySelector('header .actions > button').click();
    expect(logout).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/employee-login']);
  });

  it.each(['admin', 'analyst'] as const)('keeps the existing %s role gate without granting investor access', role => {
    const route = new ActivatedRouteSnapshot();
    route.data = { role };
    const guard = AuthGuard as CanActivateFn;
    const state = { url: `/${role}-dashboard` } as RouterStateSnapshot;
    user = { ...user!, role };
    expect(TestBed.runInInjectionContext(() => guard(route, state))).toBe(true);
    user = { ...user!, role: 'investor' };
    expect(TestBed.runInInjectionContext(() => guard(route, state))).toBe('login-redirect');
    user = null;
    expect(TestBed.runInInjectionContext(() => guard(route, state))).toBe('login-redirect');
    expect(router.createUrlTree).toHaveBeenCalledWith(['/employee-login']);
  });
});
