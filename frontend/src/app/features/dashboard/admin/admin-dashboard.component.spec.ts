import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { AdminDashboardComponent } from './admin-dashboard.component';

describe('Frontend-only admin integration', () => {
  const auth = { getUser: () => ({ id: '1', username: 'admin@example.com', role: 'admin' }), logout: vi.fn() };
  const router = { navigate: vi.fn() };
  let http: HttpTestingController;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: auth }, { provide: Router, useValue: router }
    ] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function createScreen() {
    const fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance };
  }

  function lookup(component: AdminDashboardComponent) {
    component.userId = 1;
    component.lookupUser();
    http.expectOne('/api/accounts/user/1').flush([
      { accountId: 101, type: 'TRADING', availableFunds: 500 },
      { accountId: 102, type: 'INVESTMENT', availableFunds: 200 }
    ]);
  }

  it('renders the source workspace structure without requesting unsupported global APIs', () => {
    const { fixture } = createScreen();
    expect(fixture.nativeElement.textContent).toContain('Administration');
    expect(fixture.nativeElement.querySelector('nav').textContent).toContain('User lookup');
    http.expectNone(() => true);
    expect(fixture.nativeElement.querySelector('nav').textContent).not.toContain('Disputes');
    expect(fixture.nativeElement.querySelector('nav').textContent).not.toContain('Reporting');
  });

  it('looks up accounts and shows recorded orders using only existing endpoints', async () => {
    const { fixture, component } = createScreen();
    lookup(component);
    http.expectOne('/api/accounts/101/orders').flush([{
      orderId: 7, symbol: 'AMD', side: 'BUY', quantity: 3, price: 150,
      status: 'EXECUTED', dateRequested: '2026-10-28T11:00:00Z'
    }]);
    fixture.detectChanges();
    await fixture.whenStable();
    const table: HTMLTableElement = fixture.nativeElement.querySelector('table');
    expect(table.textContent).toContain('AMD');
    expect(table.textContent).toContain('101');
    table.querySelector('button')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Order #7');
    expect(fixture.nativeElement.textContent).not.toContain('Recorded order fields only');
  });

  it('filters by UTC dates, status and symbol and resets pagination', () => {
    const { component } = createScreen();
    lookup(component);
    http.expectOne('/api/accounts/101/orders').flush(Array.from({ length: 30 }, (_, index) => ({
      orderId: index, symbol: index === 29 ? 'MSFT' : 'AMD', side: 'BUY', quantity: 1,
      status: index === 29 ? 'REJECTED' : 'EXECUTED', dateRequested: '2026-10-28T00:30:00+02:00'
    })));
    expect(component.visibleOrders).toHaveLength(25);
    component.page = 1;
    expect(component.visibleOrders).toHaveLength(5);
    component.symbol = ' msft ';
    component.status = 'REJECTED';
    component.from = component.to = '2026-10-27';
    component.applyFilters();
    expect(component.page).toBe(0);
    expect(component.filteredOrders).toHaveLength(1);
    component.from = '2026-10-29';
    component.applyFilters();
    expect(component.error).toContain('start date');
    expect(component.filteredOrders).toEqual([]);
  });

  it('loads all supported history types from existing account endpoints', () => {
    const { component } = createScreen();
    lookup(component);
    http.expectOne('/api/accounts/101/orders').flush([]);
    component.setTab('users');
    http.expectOne('/api/accounts/101/orders').flush([{
      orderId: 1, symbol: 'AMD', quantity: 2, side: 'BUY', status: 'PENDING'
    }]);
    expect(component.historyRows[0]['symbol']).toBe('AMD');
    component.historyKind = 'trades';
    component.loadHistory();
    http.expectOne('/api/accounts/101/trades').flush([{
      tradeId: 2, executionPrice: 150, executedQuantity: 2, executedAt: '2026-10-28T11:00:00Z'
    }]);
    expect(component.historyRows[0]['price']).toBe(150);
    expect(component.historyColumns.some(column => column.key === 'order')).toBe(false);
    component.historyKind = 'holdings';
    component.loadHistory();
    http.expectOne('/api/accounts/101/holdings').flush([{ symbol: 'AMD', quantity: 2, averageCost: 150 }]);
    expect(component.historyRows[0]['cost']).toBe(150);
    component.historyKind = 'cash';
    component.loadHistory();
    http.expectOne('/api/accounts/101/wallet/activity').flush([{
      movementId: 3, bankId: 8, movementType: 'DEPOSIT', amount: 500, movementDate: '2026-10-28T10:00:00Z'
    }]);
    expect(component.historyRows[0]['type']).toBe('DEPOSIT');
    expect(component.historyAsOf).not.toBe('');
  });

  it('cancels stale orders and history on account switches and destruction', () => {
    const { component, fixture } = createScreen();
    lookup(component);
    const previousOrders = http.expectOne('/api/accounts/101/orders');
    component.setTab('users');
    const previousHistory = http.expectOne('/api/accounts/101/orders');
    component.accountId = '102';
    component.loadAccount();
    expect(previousOrders.cancelled).toBe(true);
    expect(previousHistory.cancelled).toBe(true);
    const requests = http.match('/api/accounts/102/orders');
    expect(requests).toHaveLength(2);
    fixture.destroy();
    expect(requests.every(request => request.cancelled)).toBe(true);
  });

  it('does not let a successful history request conceal a failed order request', () => {
    const { component } = createScreen();
    vi.spyOn(console, 'error').mockImplementation(() => {});
    lookup(component);
    http.expectOne('/api/accounts/101/orders').flush('Unavailable', { status: 503, statusText: 'Unavailable' });
    component.setTab('users');
    http.expectOne('/api/accounts/101/orders').flush([]);
    expect(component.error).toContain('Unable to load account orders');
    component.loadOrders();
    http.expectOne('/api/accounts/101/orders').flush([]);
    expect(component.error).toBe('');
  });

  it('handles invalid IDs and empty account results without fabricating a user profile', () => {
    const { component } = createScreen();
    component.userId = -1;
    component.lookupUser();
    expect(component.error).toContain('positive whole-number');
    http.expectNone(() => true);
    component.userId = 99;
    component.lookupUser();
    http.expectOne('/api/accounts/user/99').flush([]);
    expect(component.lookupComplete).toBe(true);
    expect(component.accountId).toBe('');
    expect(component.orders).toEqual([]);
  });

  it('signs out through the existing auth service', () => {
    const { fixture } = createScreen();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('app-workspace header .actions > button');
    button.click();
    expect(auth.logout).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/employee-login']);
  });

  it('loads the tickets page without an account lookup and opens the ticket account rather than the first account', async () => {
    const { fixture, component } = createScreen();
    const navigation: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('nav button');
    const ticketsButton = [...navigation].find(button => button.textContent?.includes('Tickets'));
    expect(ticketsButton).toBeDefined();
    ticketsButton?.click();
    await fixture.whenStable();
    expect(component.activeTab).toBe('tickets');
    expect(fixture.nativeElement.querySelector('app-ticket-list')).not.toBeNull();
    http.expectOne('/api/admin/tickets/dispute').flush([{
      kind: 'dispute', ticketId: 7, accountId: 102, userId: 1, username: 'test',
      firstName: 'Test', lastName: 'User', reason: 'Wrong quantity', status: 'DISPUTED',
      accountType: 'TRADING', availableFunds: 500, orderId: 9, symbol: 'AMD'
    }]);
    await fixture.whenStable();
    const view: HTMLButtonElement = fixture.nativeElement.querySelector('app-ticket-list tbody button');
    view.click();
    await fixture.whenStable();
    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('app-ticket-list button');
    const accountButton = [...buttons].find(button => button.textContent?.includes('View account history'));
    expect(accountButton).toBeDefined();
    accountButton?.click();
    http.expectOne('/api/accounts/user/1').flush([
      { accountId: 101, type: 'TRADING', availableFunds: 50 },
      { accountId: 102, type: 'TRADING', availableFunds: 500 }
    ]);
    const orders = http.match('/api/accounts/102/orders');
    expect(orders).toHaveLength(2);
    orders.forEach(request => request.flush([]));
    expect(component.activeTab).toBe('users');
    expect(component.accountId).toBe('102');
  });
});
