import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { FinnhubNewsService } from '../../../core/services/finnhub-news.service';
import { PortfolioService } from '../../../core/services/portfolio.service';
import { PortfolioStateService } from '../../../core/services/portfolio-state.service';
import { DashboardComponent } from './dashboard.component';

describe('Investor ticket entry points', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    vi.spyOn(DashboardComponent.prototype, 'ngOnInit').mockImplementation(() => {});
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { getUser: () => ({ id: '1', role: 'investor' }) } },
      { provide: Router, useValue: {} }, { provide: ActivatedRoute, useValue: {} },
      { provide: ApiService, useValue: {} }, { provide: FinnhubNewsService, useValue: {} },
      { provide: PortfolioService, useValue: {} }, { provide: PortfolioStateService, useValue: {} }
    ] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('places a dispute action beside each History order and refreshes persisted disputes after submission', async () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    component.accountId = '101';
    component.activeView = 'history';
    component.orders = [{
      orderId: '2006', accountId: '101', symbol: 'META', side: 'BUY', quantity: 2, price: 100, status: 'EXECUTED'
    }];
    fixture.detectChanges();
    http.expectOne('/api/accounts/101/tickets/dispute').flush([]);
    await fixture.whenStable();
    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector('tbody app-ticket-composer button');
    expect(trigger.textContent).toContain('Dispute');
    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('app-ticket-composer dialog');
    dialog.showModal = vi.fn();
    trigger.click();
    expect(dialog.showModal).toHaveBeenCalledOnce();
    await fixture.whenStable();
    const reason: HTMLTextAreaElement = dialog.querySelector('textarea')!;
    reason.value = 'Wrong quantity';
    reason.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    dialog.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const request = http.expectOne('/api/accounts/101/orders/2006/disputes');
    expect(request.request.body.reason).toBe('Wrong quantity');
    request.flush({ ticketId: 7 });
    http.expectOne('/api/accounts/101/tickets/dispute').flush([]);
  });

  it('offers general support on Help while leaving the FAQs visible', async () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.componentInstance.accountId = '101';
    fixture.componentInstance.activeView = 'help';
    fixture.detectChanges();
    http.expectOne('/api/accounts/101/tickets/support').flush([]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('app-help')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Contact support');
    expect(fixture.nativeElement.textContent).toContain('Your support tickets');
    expect(fixture.nativeElement.querySelector('app-ticket-composer select')).toBeNull();
  });
});
