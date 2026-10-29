import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from '../../../core/services/api.service';
import { SupportQueueComponent } from './support-queue.component';
const ticket: any = {ticketId: 81, accountId: 101, subject: 'Help', category: 'PLATFORM_QUESTION', message: 'Question', status: 'OPEN', createdAtUtc: '2026-10-29T12:00:00Z', version: 2};
describe('General support queue', () => {
  let api: any;
  beforeEach(() => {
    api = {getAdminSupportTickets: vi.fn(() => of({items: [ticket], total: 1, page: 0, size: 20})), updateSupportTicket: vi.fn(() => of({...ticket, status: 'RESOLVED', version: 3}))};
    TestBed.configureTestingModule({providers: [{provide: ApiService, useValue: api}]});
  });
  it('requires a customer-visible response to resolve a request', () => {
    const fixture = TestBed.createComponent(SupportQueueComponent); fixture.componentRef.setInput('canWork', true); fixture.detectChanges();
    const c = fixture.componentInstance; c.select(ticket); c.nextStatus = 'RESOLVED'; c.save(); expect(api.updateSupportTicket).not.toHaveBeenCalled();
    c.response = ' Use the theme button. '; c.save(); expect(api.updateSupportTicket).toHaveBeenCalledWith(ticket, 'RESOLVED', 'Use the theme button.');
  });
  it('keeps read-only staff from responding and reports conflicting edits', () => {
    const fixture = TestBed.createComponent(SupportQueueComponent); fixture.detectChanges(); const c = fixture.componentInstance;
    c.select(ticket); c.save(); expect(api.updateSupportTicket).not.toHaveBeenCalled();
    c.canWork = true; api.updateSupportTicket.mockReturnValue(throwError(() => ({status: 409}))); c.save();
    expect(c.error).toContain('Refresh'); expect(c.selected).toBeNull();
  });
});
