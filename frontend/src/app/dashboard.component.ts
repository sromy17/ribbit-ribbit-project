import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService, User } from './auth.service';

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

  // Investor Dashboard Data
  portfolio = {
    cash: 50000,
    holdings: [
      { symbol: 'AAPL', shares: 100, price: 150.25, value: 15025 },
      { symbol: 'MSFT', shares: 50, price: 380.50, value: 19025 },
      { symbol: 'GOOGL', shares: 25, price: 140.75, value: 3518.75 }
    ],
    totalValue: 87568.75
  };

  orders = [
    { id: 'ORD-001', symbol: 'TSLA', side: 'BUY', qty: 10, price: 250, status: 'FILLED' },
    { id: 'ORD-002', symbol: 'NVDA', side: 'SELL', qty: 5, price: 875, status: 'PENDING' }
  ];

  orderForm = { symbol: '', side: 'BUY', quantity: 0, price: 0 };

  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.currentUser = this.auth.getUser();
    if (!this.currentUser) {
      this.router.navigate(['/login']);
    } else {
      this.currentRole = this.currentUser.role;
    }
  }

  ngOnInit() {}

  placeOrder() {
    if (!this.orderForm.symbol || !this.orderForm.quantity || !this.orderForm.price) {
      alert('Please fill all fields');
      return;
    }
    alert(`Order placed: ${this.orderForm.side} ${this.orderForm.quantity} ${this.orderForm.symbol} @ $${this.orderForm.price}`);
    this.orderForm = { symbol: '', side: 'BUY', quantity: 0, price: 0 };
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  getTotalValue(): number {
    return this.portfolio.cash + this.portfolio.holdings.reduce((sum, h) => sum + h.value, 0);
  }
}

