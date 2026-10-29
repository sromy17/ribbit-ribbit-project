import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from '../../../../core/services/api.service';
import { HelpComponent } from './help.component';

const ticket = { ticketId: 81, accountId: 101, category: 'PLATFORM_QUESTION', subject: 'Using the platform', message: 'How do I change theme?', status: 'OPEN', createdAtUtc: '2026-10-29T12:00:00Z', closedAtUtc: null, resolution: null, version: 0 };
describe('Help requests and knowledge base', () => {
  let api: any;
  beforeEach(() => {
    api = { getSupportTickets: vi.fn(() => of({items: [], total: 0, page: 0, size: 20})), submitSupportTicket: vi.fn(() => of(ticket)) };
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
  });
  function create() {
    const fixture = TestBed.createComponent(HelpComponent);
    fixture.componentRef.setInput('accountId', '101'); fixture.detectChanges(); return fixture;
  }
  it('keeps four collapsed FAQ categories alongside a general assistance form', () => {
    const fixture = create();
    expect(fixture.nativeElement.querySelectorAll('details')).toHaveLength(4);
    expect(fixture.nativeElement.querySelectorAll('details[open]')).toHaveLength(0);
    expect(fixture.nativeElement.querySelectorAll('form')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Contact support');
    expect(fixture.nativeElement.textContent).toContain('Your help requests');
  });
  it('rejects blank requests and submits a trimmed platform question without a trade id', () => {
    const fixture = create(), c = fixture.componentInstance;
    c.subject = '   '; c.message = 'Question'; c.submit();
    expect(api.submitSupportTicket).not.toHaveBeenCalled();
    c.subject = ' Using the platform '; c.message = ' How do I change theme? '; c.submit();
    expect(api.submitSupportTicket).toHaveBeenCalledWith('101', {
      subject: ticket.subject, message: ticket.message, category: 'PLATFORM_QUESTION', requestId: expect.any(String)
    });
    expect(c.success).toContain('#81'); expect(c.pending).toBeNull();
    expect(api.getSupportTickets).toHaveBeenCalledTimes(2);
  });
  it('reuses the original submission reference after an unconfirmed response', () => {
    api.submitSupportTicket.mockReturnValueOnce(throwError(() => ({status: 0}))).mockReturnValueOnce(of(ticket));
    const fixture = create(), c = fixture.componentInstance;
    c.subject = ticket.subject; c.message = ticket.message;
    c.submit(); const first = api.submitSupportTicket.mock.calls[0][1];
    expect(c.error).toContain('could not confirm'); expect(c.pending).toBe(first);
    c.submit(); expect(api.submitSupportTicket.mock.calls[1][1]).toBe(first);
    expect(c.success).toContain('#81');
  });
  it('renders saved requests and staff responses after returning to Help', () => {
    api.getSupportTickets.mockReturnValue(of({items: [{...ticket, status: 'RESOLVED', resolution: 'Use the theme button in the header.'}], total: 1, page: 0, size: 20}));
    const fixture = create(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Use the theme button');
    expect(fixture.nativeElement.textContent).toContain('RESOLVED');
  });
  it('lets readers expand and collapse an FAQ category', () => {
    const fixture = create(); const category: HTMLDetailsElement = fixture.nativeElement.querySelector('details');
    category.querySelector('summary')!.click(); expect(category.open).toBe(true);
    category.querySelector('summary')!.click(); expect(category.open).toBe(false);
  });
});
