import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService, User } from './auth.service';
import { ApiService, Holding, Order } from './api.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  currentUser: User | null = null;
  currentRole: string = '';
  accountId: string = ''; // Store account ID for API calls
  loading = false;
  error = '';

  // Investor Dashboard Data
  portfolio = {
    cash: 0,
    holdings: [] as Holding[],
    totalValue: 0
  };

  orders: Order[] = [];

  orderForm = { symbol: '', side: 'BUY', quantity: 0, price: 0 };

  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private api: ApiService
  ) {
    this.currentUser = this.auth.getUser();
    if (!this.currentUser) {
      this.router.navigate(['/login']);
    } else {
      this.currentRole = this.currentUser.role;
      // For now, use user ID as account ID (update this when backend provides proper account lookup)
      this.accountId = this.currentUser.id;
    }
  }

  ngOnInit() {
    if (this.accountId) {
      this.loadDashboardData();
    }
  }

  loadDashboardData() {
    this.loading = true;
    this.error = '';

    // Load balance
    this.api.getAccountBalance(this.accountId).subscribe({
      next: (balance) => {
        this.portfolio.cash = balance.cashBalance;
        this.updateTotalValue();
      },
      error: (err) => {
        console.error('Error loading balance:', err);
        this.error = 'Failed to load account balance';
        this.loading = false;
      }
    });

    // Load holdings
    this.api.getHoldings(this.accountId).subscribe({
      next: (holdings) => {
        this.portfolio.holdings = holdings;
        this.updateTotalValue();
      },
      error: (err) => {
        console.error('Error loading holdings:', err);
        this.error = 'Failed to load holdings';
        this.loading = false;
      }
    });

    // Load orders
    this.api.getOrders(this.accountId).subscribe({
      next: (orders) => {
        this.orders = orders;
        this.loading = false;
      },
      error: (err) => {
        console.error('Error loading orders:', err);
        this.error = 'Failed to load orders';
        this.loading = false;
      }
    });
  }

  placeOrder() {
    if (!this.orderForm.symbol || !this.orderForm.quantity || !this.orderForm.price) {
      alert('Please fill all fields');
      return;
    }

    this.loading = true;
    this.api.createOrder(this.accountId, {
      symbol: this.orderForm.symbol,
      side: this.orderForm.side as 'BUY' | 'SELL',
      quantity: this.orderForm.quantity,
      price: this.orderForm.price,
      status: 'PENDING'
    }).subscribe({
      next: (order) => {
        alert(`Order placed: ${order.side} ${order.quantity} ${order.symbol} @ $${order.price}`);
        this.orderForm = { symbol: '', side: 'BUY', quantity: 0, price: 0 };
        this.loadDashboardData(); // Refresh orders list
        this.loading = false;
      },
      error: (err) => {
        console.error('Error placing order:', err);
        alert('Failed to place order');
        this.loading = false;
      }
    });
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  updateTotalValue(): void {
    const holdingsValue = this.portfolio.holdings.reduce((sum, h) => {
      const price = h.currentPrice || h.averageCost;
      return sum + (h.quantity * price);
    }, 0);
    this.portfolio.totalValue = this.portfolio.cash + holdingsValue;
  }

  getTotalValue(): number {
    return this.portfolio.totalValue;
  }
}

