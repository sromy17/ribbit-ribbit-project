import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';

describe('Admin investigation workspace', () => {
  let api: any;
  beforeEach(() => {
    api = {
      getAdminOrders: vi.fn(() => of({items: [], total: 0})),
      getAdminCasePage: vi.fn(() => of({items: [], total: 0, asOf: '2026-10-21T12:00:00Z'})),
      getAdminReportingStatus: vi.fn(() => of({status: 'UNAVAILABLE', stale: true})),
      getAdminActivity: vi.fn(() => of({instruments: [], orders: []})),
      getAdminCase: vi.fn(), getAdminCaseEvents: vi.fn(() => of([])), caseAction: vi.fn(),
      lookupUser: vi.fn(() => of({userId: 1, username: 'client'})),
      lookupAccounts: vi.fn(() => of([{accountId: '2', type: 'TRADING'}])),
      accountHistory: vi.fn(() => of({items: [], total: 0, asOf: '2026-10-21T12:00:00Z', account: {availablefunds: 25}}))
    };
    TestBed.configureTestingModule({imports: [AdminDashboardComponent], providers: [
      {provide: ApiService, useValue: api}, {provide: AuthService, useValue: {getUser: () => ({id: '9', role: 'admin', permissions: ['PERM_DISPUTE_WORK', 'PERM_DISPUTE_SUPERVISOR', 'PERM_REPORTING']}), logout: vi.fn()}},
      {provide: Router, useValue: {navigate: vi.fn()}}
    ]});
  });
  it('sends versioned resolution and reloads the case', async () => {
    const fixture = TestBed.createComponent(AdminDashboardComponent); fixture.detectChanges();
    const c = fixture.componentInstance;
    const item: any = {caseId: 1, version: 4, status: 'IN_PROGRESS', assignedStaffUserId: 9, title: 'Fee'};
    c.selectedCase = item; c.reason = 'Checked evidence';
    api.caseAction.mockReturnValue(of({...item, version: 5, status: 'RESOLVED'}));
    api.getAdminCase.mockReturnValue(of({...item, version: 5, status: 'RESOLVED'}));
    c.act('resolve');
    expect(api.caseAction).toHaveBeenCalledWith(1, 'resolve', expect.objectContaining({expectedVersion: 4, reason: 'Checked evidence', outcome: 'UPHELD'}));
    expect(c.selectedCase?.status).toBe('RESOLVED');
    fixture.nativeElement.querySelectorAll('nav button')[1].click();
    await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('cannot be reopened');
  });
  it('reports a conflicting edit instead of claiming success', () => {
    const fixture = TestBed.createComponent(AdminDashboardComponent); fixture.detectChanges();
    const c = fixture.componentInstance;
    c.selectedCase = {caseId: 1, version: 1, status: 'OPEN'} as any;
    api.caseAction.mockReturnValue(throwError(() => ({status: 409})));
    c.act('claim'); expect(c.error).toContain('Reload'); expect(c.busy).toBe(false);
  });
  it('uses server pagination and labels the operational snapshot', () => {
    const fixture = TestBed.createComponent(AdminDashboardComponent); fixture.detectChanges();
    const c = fixture.componentInstance; c.activeTab = 'users'; c.lookupUser(1); c.loadHistory(2);
    expect(api.accountHistory).toHaveBeenLastCalledWith('2', 'orders', 2);
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Operational snapshot as of');
  });
  it('ignores a superseded lookup response', () => {
    const fixture = TestBed.createComponent(AdminDashboardComponent); fixture.detectChanges();
    const old = new Subject<any>(); api.lookupUser.mockReturnValueOnce(old);
    const c = fixture.componentInstance; c.lookupUser(1); c.lookupUser(2);
    old.next({userId: 1, username: 'obsolete'});
    expect(c.user?.username).toBe('client');
  });
});
