import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';

// Component -> ApiService -> HttpClient -> API
// Data Interfaces (that match the OpenAPI Schema)

/**
 * constructor(private api: ApiService) {}

ngOnInit() {
  this.api.getOrders(accountId).subscribe(orders => {
    this.orders = orders;
  });
}
 */

export interface User {
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  role: 'investor' | 'admin' | 'analyst';
}

export interface TradingAccount {
  accountId: string;
  accountType: string;
  cashBalance: number;
  creationDate?: string;
}

export interface Order {
  orderId: string;
  accountId: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  status: 'PENDING' | 'FILLED' | 'CANCELLED';
  createdAt?: string;
}

export interface Trade {
  tradeId: string;
  orderId: string;
  symbol?: string;
  executionPrice: number;
  executedQuantity: number;
  executedAt?: string;
}

export interface Holding {
  symbol: string;
  quantity: number;
  averageCost: number;
  currentPrice?: number;
}

export interface Transaction {
  transactionId: string;
  accountId: string;
  transactionType: string;
  amount: number;
  timestamp?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  sessionId: string;
  accessToken: string;
  expiresAt: string;
  user: User;
}

// API Service

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private apiUrl = 'https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1'; // updated with actual API base URL
  private useMockData = true; // Toggle this to switch between mock and real API // flipping this back to True because no login endpoint yet

  constructor(private http: HttpClient) {}

  // Authentication

  login(request: LoginRequest): Observable<LoginResponse> {
    if (this.useMockData) {
      return of(this.mockLogin(request)).pipe(delay(500));
    }
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, request);
  }

  logout(): Observable<any> {
    if (this.useMockData) {
      return of({ success: true }).pipe(delay(200));
    }
    return this.http.post(`${this.apiUrl}/logout`, {});
  }

  // Accounts

  getAccountDetails(accountId: string): Observable<TradingAccount> {
    if (this.useMockData) {
      return of(this.mockGetAccountDetails(accountId)).pipe(delay(300));
    }
    return this.http.get<TradingAccount>(`${this.apiUrl}/accounts/${accountId}`);
  }

  getAccountBalance(accountId: string): Observable<{ accountId: string; cashBalance: number }> {
    if (this.useMockData) {
      return of(this.mockGetAccountBalance(accountId)).pipe(delay(200));
    }
    return this.http.get<{ accountId: string; cashBalance: number }>(
      `${this.apiUrl}/accounts/${accountId}/balance`
    );
  }

  updateAccountBalance(accountId: string, amount: number): Observable<any> {
    if (this.useMockData) {
      return of({ success: true, newBalance: amount }).pipe(delay(200));
    }
    return this.http.put(`${this.apiUrl}/accounts/${accountId}/balance`, { amount });
  }

  // Holdings

  getHoldings(accountId: string): Observable<Holding[]> {
    if (this.useMockData) {
      return of(this.mockGetHoldings(accountId)).pipe(delay(300));
    }
    return this.http.get<Holding[]>(`${this.apiUrl}/accounts/${accountId}/holdings`);
  }

  // Orders

  getOrders(accountId: string): Observable<Order[]> {
    if (this.useMockData) {
      return of(this.mockGetOrders(accountId)).pipe(delay(300));
    }
    return this.http.get<Order[]>(`${this.apiUrl}/accounts/${accountId}/orders`);
  }

  createOrder(accountId: string, order: Omit<Order, 'orderId' | 'accountId' | 'createdAt'>): Observable<Order> {
    if (this.useMockData) {
      return of(this.mockCreateOrder(accountId, order)).pipe(delay(500));
    }
    return this.http.post<Order>(`${this.apiUrl}/accounts/${accountId}/orders`, order);
  }

  cancelOrder(accountId: string, orderId: string): Observable<any> {
    if (this.useMockData) {
      return of({ success: true, orderId }).pipe(delay(300));
    }
    return this.http.delete(`${this.apiUrl}/accounts/${accountId}/orders/${orderId}/status`);
  }

  // Trades

  getTrades(accountId: string): Observable<Trade[]> {
    if (this.useMockData) {
      return of(this.mockGetTrades(accountId)).pipe(delay(300));
    }
    return this.http.get<Trade[]>(`${this.apiUrl}/accounts/${accountId}/trades`);
  }

  // Mock Data

  private mockLogin(request: LoginRequest): LoginResponse {
    const mockUsers: Record<string, User> = {
      'investor@test.com': { 
        userId: '1', 
        email: 'investor@test.com', 
        firstName: 'John',
        lastName: 'Investor',
        role: 'investor' 
      },
      'admin@test.com': { 
        userId: '2', 
        email: 'admin@test.com', 
        firstName: 'Admin',
        lastName: 'User',
        role: 'admin' 
      },
      'analyst@test.com': { 
        userId: '3', 
        email: 'analyst@test.com', 
        firstName: 'Alice',
        lastName: 'Analyst',
        role: 'analyst' 
      }
    };

    const user = mockUsers[request.username];
    if (user && request.password === 'password') {
      return {
        sessionId: 'mock-session-' + Date.now(),
        accessToken: 'mock-token-' + Date.now(),
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        user
      };
    }
    throw new Error('Invalid credentials');
  }

  private mockGetAccountDetails(accountId: string): TradingAccount {
    return {
      accountId,
      accountType: 'BROKERAGE',
      cashBalance: 50000,
      creationDate: '2025-01-15'
    };
  }

  private mockGetAccountBalance(accountId: string): { accountId: string; cashBalance: number } {
    return {
      accountId,
      cashBalance: 50000
    };
  }

  private mockGetHoldings(accountId: string): Holding[] {
    return [
      { symbol: 'AAPL', quantity: 100, averageCost: 145.20, currentPrice: 150.25 },
      { symbol: 'MSFT', quantity: 50, averageCost: 370.00, currentPrice: 380.50 },
      { symbol: 'GOOGL', quantity: 25, averageCost: 130.00, currentPrice: 140.75 }
    ];
  }

  private mockGetOrders(accountId: string): Order[] {
    return [
      { 
        orderId: 'ORD-001', 
        accountId, 
        symbol: 'TSLA', 
        side: 'BUY', 
        quantity: 10, 
        price: 250, 
        status: 'FILLED',
        createdAt: '2025-10-01T10:30:00Z'
      },
      { 
        orderId: 'ORD-002', 
        accountId, 
        symbol: 'NVDA', 
        side: 'SELL', 
        quantity: 5, 
        price: 875, 
        status: 'PENDING',
        createdAt: '2025-10-02T14:15:00Z'
      }
    ];
  }

  private mockCreateOrder(accountId: string, order: Omit<Order, 'orderId' | 'accountId' | 'createdAt'>): Order {
    return {
      ...order,
      orderId: 'ORD-' + Math.floor(Math.random() * 10000).toString().padStart(3, '0'),
      accountId,
      status: 'PENDING',
      createdAt: new Date().toISOString()
    };
  }

  private mockGetTrades(accountId: string): Trade[] {
    return [
      {
        tradeId: 'TRD-001',
        orderId: 'ORD-001',
        symbol: 'TSLA',
        executionPrice: 248.50,
        executedQuantity: 10,
        executedAt: '2025-10-01T10:35:00Z'
      },
      {
        tradeId: 'TRD-002',
        orderId: 'ORD-003',
        symbol: 'AMD',
        executionPrice: 120.75,
        executedQuantity: 20,
        executedAt: '2025-09-28T09:12:00Z'
      }
    ];
  }

  // Helper Methods

  /**
   * Toggle between mock data and real API
   * Call this to switch: apiService.setUseMockData(false) when backend is ready
   */
  setUseMockData(useMock: boolean): void {
    this.useMockData = useMock;
    console.log('API Service: Using ' + (useMock ? 'MOCK' : 'REAL') + ' data');
  }

  /**
   * Set the API URL when backend is deployed
   */
  setApiUrl(url: string): void {
    this.apiUrl = url;
  }
}
