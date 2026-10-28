import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Ticket } from '../../core/services/ticket.service';
import { TicketComposerComponent } from './ticket-composer.component';
import { TicketListComponent } from './ticket-list.component';

const ticket: Ticket = {
  kind: 'dispute', ticketId: 7, accountId: 101, userId: 1, username: 'john_trader',
  firstName: 'John', lastName: 'Trader', accountType: 'TRADING', availableFunds: 500,
  orderId: 2001, symbol: 'AMD', side: 'BUY', quantity: 2, orderStatus: 'EXECUTED',
  category: 'Accident', reason: 'Wrong quantity', status: 'DISPUTED',
  employeeId: null, employeeUsername: null, createdAt: null, closedAt: null
};

describe('Ticket submission and review', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    TestBed.resetTestingModule();
  });

  function composer(order = '2001') {
    const fixture = TestBed.createComponent(TicketComposerComponent);
    fixture.componentRef.setInput('accountId', '101');
    fixture.componentRef.setInput('orderId', order);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance };
  }

  function list(admin = false, kind: 'dispute' | 'support' = 'dispute') {
    const fixture = TestBed.createComponent(TicketListComponent);
    fixture.componentRef.setInput('admin', admin);
    fixture.componentRef.setInput('kind', kind);
    if (!admin) fixture.componentRef.setInput('accountId', '101');
    fixture.detectChanges();
    const url = admin ? `/api/admin/tickets/${kind}` : `/api/accounts/101/tickets/${kind}`;
    return { fixture, component: fixture.componentInstance, url };
  }

  it('submits the selected order, trims input, prevents double submission and emits success', () => {
    const { component } = composer();
    const submitted = vi.spyOn(component.submitted, 'emit');
    component.reason = '  Wrong quantity  ';
    component.submit();
    component.submit();
    const request = http.expectOne('/api/accounts/101/orders/2001/disputes');
    expect(request.request.body).toEqual({ category: 'Accident', reason: 'Wrong quantity' });
    expect(component.submitting).toBe(true);
    const escape = new Event('cancel', { cancelable: true });
    component.cancel(escape);
    expect(escape.defaultPrevented).toBe(true);
    request.flush({ ticketId: 7 });
    expect(component.ticketId).toBe(7);
    expect(submitted).toHaveBeenCalledOnce();
    expect(component.submitting).toBe(false);
    component.submit();
    http.expectNone(() => true);
  });

  it('submits account-wide support without an order or category', () => {
    const { component, fixture } = composer('');
    expect(fixture.nativeElement.textContent).toContain('Contact support');
    expect(fixture.nativeElement.querySelector('select')).toBeNull();
    component.reason = 'Help with my account';
    component.submit();
    const request = http.expectOne('/api/accounts/101/support-tickets');
    expect(request.request.body).toEqual({ reason: 'Help with my account' });
    request.flush({ ticketId: 8 });
  });

  it('validates reason length and preserves the form after a duplicate or failed submission', () => {
    const { component } = composer();
    for (const reason of [' ', 'x'.repeat(76)]) {
      component.reason = reason;
      component.submit();
      expect(component.error).toContain('75');
      http.expectNone(() => true);
    }
    component.reason = 'Please investigate';
    component.submit();
    http.expectOne('/api/accounts/101/orders/2001/disputes').flush(
      { message: 'This order already has an open dispute' }, { status: 409, statusText: 'Conflict' });
    expect(component.error).toContain('open dispute');
    expect(component.reason).toBe('Please investigate');
    expect(component.ticketId).toBeNull();
    expect(component.submitting).toBe(false);
  });

  it('cancels the previous account request and never shows its response for a new account', () => {
    const { component, fixture } = composer();
    component.reason = 'Account one';
    component.submit();
    const old = http.expectOne('/api/accounts/101/orders/2001/disputes');
    fixture.componentRef.setInput('accountId', '102');
    fixture.detectChanges();
    expect(old.cancelled).toBe(true);
    expect(component.submitting).toBe(false);
    expect(component.ticketId).toBeNull();
  });

  it('loads investor tickets only for the selected account and offers no admin actions', async () => {
    const { fixture, component, url } = list();
    http.expectOne(url).flush([ticket]);
    component.selected = ticket;
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Wrong quantity');
    expect(fixture.nativeElement.textContent).not.toContain('Resolve ticket');
    expect(fixture.nativeElement.textContent).toContain('timestamps are not stored');
    component.update('COMPLETED');
    http.expectNone(() => true);
  });

  it('lets admins inspect the exact account and resolve using an optimistic status check', () => {
    const { component, fixture, url } = list(true);
    http.expectOne(url).flush([ticket]);
    component.selected = ticket;
    const account = vi.spyOn(component.accountRequested, 'emit');
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button');
    const button = [...buttons].find(element => element.textContent?.includes('View account history'));
    expect(button).toBeDefined();
    button?.click();
    expect(account).toHaveBeenCalledWith(ticket);
    component.update('COMPLETED');
    component.update('COMPLETED');
    const update = http.expectOne('/api/admin/tickets/dispute/7');
    expect(update.request.method).toBe('PATCH');
    expect(update.request.body).toEqual({ status: 'COMPLETED', expectedStatus: 'DISPUTED' });
    update.flush(null);
    http.expectOne(url).flush([{ ...ticket, status: 'COMPLETED', employeeUsername: 'admin_user' }]);
    expect(component.notice).toContain('COMPLETED');
    expect(component.tickets[0].status).toBe('COMPLETED');
  });

  it('surfaces conflicting admin updates without reporting success', () => {
    const { component, url } = list(true);
    http.expectOne(url).flush([ticket]);
    component.selected = ticket;
    component.update('UNDER-REVIEW');
    http.expectOne('/api/admin/tickets/dispute/7').flush(
      { message: 'Ticket changed. Refresh and retry.' }, { status: 409, statusText: 'Conflict' });
    expect(component.error).toContain('Refresh');
    expect(component.notice).toBe('');
    expect(component.saving).toBe(false);
    expect(component.selected?.status).toBe('DISPUTED');
  });

  it('switches admin ticket types, filters statuses, and retries load failures', () => {
    const { component, url } = list(true);
    http.expectOne(url).flush([], { status: 503, statusText: 'Unavailable' });
    expect(component.error).toContain('Unable to load');
    component.kind = 'support';
    component.refresh();
    http.expectOne('/api/admin/tickets/support').flush([
      { ...ticket, kind: 'support', status: 'OPEN', orderId: null },
      { ...ticket, ticketId: 8, kind: 'support', status: 'RESOLVED', orderId: null }
    ]);
    component.statusFilter = 'OPEN';
    expect(component.filtered).toHaveLength(1);
    expect(component.error).toBe('');
    expect(component.closedStatus).toBe('RESOLVED');
  });

  it('cancels pending list requests on account switch and destruction', () => {
    const { fixture, url } = list();
    const old = http.expectOne(url);
    fixture.componentRef.setInput('accountId', '102');
    fixture.detectChanges();
    expect(old.cancelled).toBe(true);
    const current = http.expectOne('/api/accounts/102/tickets/dispute');
    fixture.destroy();
    expect(current.cancelled).toBe(true);
  });
});
