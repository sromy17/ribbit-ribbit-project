import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from '../../../core/services/api.service';
import { TradeDisputesComponent } from './trade-disputes.component';

describe('Customer trade disputes', () => {
  let api: {getCustomerDisputes: ReturnType<typeof vi.fn>; submitTradeDispute: ReturnType<typeof vi.fn>};
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function() { this.open = true; };
    HTMLDialogElement.prototype.close = function() { this.open = false; };
    api = {getCustomerDisputes: vi.fn(() => of({items: [], total: 0})), submitTradeDispute: vi.fn()};
    TestBed.configureTestingModule({providers: [{provide: ApiService, useValue: api}]});
  });
  function create() {
    const fixture = TestBed.createComponent(TradeDisputesComponent);
    fixture.componentRef.setInput('accountId', '101');
    fixture.componentRef.setInput('order', {orderId: '2008', symbol: 'AAPL', side: 'BUY', status: 'EXECUTED', executedQuantity: 1});
    fixture.detectChanges(); return fixture;
  }
  it('submits the actual trade and allows another ticket with a new key', () => {
    api.submitTradeDispute.mockReturnValue(of({caseId: 7}));
    const c = create().componentInstance; c.open(); c.title = 'Fee question'; c.reason = 'Please review the fee'; c.feeRelated = true; c.submit();
    const first = api.submitTradeDispute.mock.calls[0][0];
    expect(first).toEqual(expect.objectContaining({transactionType: 'TRADE', transactionId: '2008', title: 'Fee question', feeRelated: true}));
    expect(c.success).toContain('#7');
    c.title = 'Another issue'; c.reason = 'Separate reason'; c.submit();
    expect(api.submitTradeDispute.mock.calls[1][0].idempotencyKey).not.toBe(first.idempotencyKey);
  });
  it('does not cancel native checkbox clicks inside the dialog', async () => {
    const fixture = create(); fixture.componentInstance.open();
    await fixture.whenStable();
    const checkbox = fixture.nativeElement.querySelector('input[type="checkbox"]') as HTMLInputElement;
    checkbox.click(); fixture.detectChanges();
    await fixture.whenStable();
    expect(checkbox.checked).toBe(true);
    expect(fixture.componentInstance.feeRelated).toBe(true);
  });
  it('prevents double submits and reuses the same key after an uncertain failure', () => {
    const response = new Subject<any>(); api.submitTradeDispute.mockReturnValue(response);
    const c = create().componentInstance; c.title = 'Question'; c.reason = 'Reason'; c.submit(); c.submit();
    expect(api.submitTradeDispute).toHaveBeenCalledTimes(1);
    const first = api.submitTradeDispute.mock.calls[0][0];
    response.error({status: 0});
    api.submitTradeDispute.mockReturnValue(of({caseId: 9})); c.submit();
    expect(api.submitTradeDispute.mock.calls[1][0]).toEqual(first);
  });
  it('shows resolved tickets and their customer-visible resolution', () => {
    api.getCustomerDisputes.mockReturnValue(of({total: 1, items: [{caseId: 1, title: 'Fee', reason: 'Question', status: 'RESOLVED', resolutionReason: 'NOT_UPHELD: Fee matches schedule'}]}));
    const fixture = create(); fixture.componentRef.setInput('summary', {total: 1, active: 0, resolved: 1, status: 'RESOLVED'});
    fixture.componentInstance.open(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Resolved');
    expect(fixture.nativeElement.textContent).toContain('Fee matches schedule');
    expect(fixture.nativeElement.textContent).toContain('Submit a new ticket');
  });
  it('does not let an unexecuted order submit a dispute', () => {
    const fixture = create(); fixture.componentRef.setInput('order', {orderId: '2009', status: 'PENDING', executedQuantity: 0}); fixture.detectChanges();
    const c = fixture.componentInstance; c.title = 'Title'; c.reason = 'Reason'; c.submit();
    expect(api.submitTradeDispute).not.toHaveBeenCalled();
  });
  it('shows a read failure instead of claiming there are no tickets', () => {
    api.getCustomerDisputes.mockReturnValue(throwError(() => ({status: 503})));
    const fixture = create(); fixture.componentInstance.open(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Tickets could not be loaded');
    expect(fixture.nativeElement.textContent).not.toContain('No tickets submitted');
  });
});
