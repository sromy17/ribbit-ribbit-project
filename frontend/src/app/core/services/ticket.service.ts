import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';

export type TicketKind = 'dispute' | 'support';
export type DisputeCategory = 'Fraud' | 'Accident' | 'Incorrect transfer';
export interface Ticket {
  kind: TicketKind;
  ticketId: number;
  accountId: number;
  userId: number;
  username: string;
  firstName: string;
  lastName: string;
  accountType: string;
  availableFunds: number;
  orderId: number | null;
  symbol: string | null;
  side: string | null;
  quantity: number | null;
  orderStatus: string | null;
  category: DisputeCategory | null;
  reason: string;
  status: string;
  employeeId: number | null;
  employeeUsername: string | null;
  createdAt: string | null;
  closedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class TicketService {
  constructor(private readonly http: HttpClient) {}

  createDispute(accountId: string, orderId: string, category: DisputeCategory, reason: string) {
    return this.http.post<{ ticketId: number }>(
      `/api/accounts/${accountId}/orders/${orderId}/disputes`, { category, reason });
  }

  createSupport(accountId: string, reason: string) {
    return this.http.post<{ ticketId: number }>(`/api/accounts/${accountId}/support-tickets`, { reason });
  }

  list(kind: TicketKind, accountId?: string) {
    const url = accountId ? `/api/accounts/${accountId}/tickets/${kind}` : `/api/admin/tickets/${kind}`;
    return this.http.get<Ticket[]>(url).pipe(timeout(15000));
  }

  update(ticket: Ticket, status: string) {
    return this.http.patch<void>(`/api/admin/tickets/${ticket.kind}/${ticket.ticketId}`,
      { status, expectedStatus: ticket.status });
  }
}
