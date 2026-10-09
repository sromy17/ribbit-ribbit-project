import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService, User } from './auth.service';
import { ApiService, Holding, Order, Trade, SymbolTrend } from './api.service';

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
  loadingBalance = false;
  loadingHoldings = false;
  loadingOrders = false;
  loadingTrades = false;
  error = '';

  // Investor Dashboard Data
  portfolio = {
    cash: 0,
    holdings: [] as Holding[],
    totalValue: 0
  };

  orders: Order[] = [];
  trades: Trade[] = [];
  symbolTrends: SymbolTrend[] = [];

  orderForm = { symbol: '', side: 'BUY', quantity: 0, price: 0 };
  orderFormErrors: { [key: string]: string } = {};
  cancelingOrderId: string | null = null;

  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private api: ApiService,
    private cdr: ChangeDetectorRef
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
    this.loadingBalance = true;
    this.loadingHoldings = true;
    this.loadingOrders = true;
    this.loadingTrades = true;
    this.error = '';

    // Load balance
    this.api.getAccountBalance(this.accountId).subscribe({
      next: (balance) => {
        this.portfolio.cash = balance.cashBalance;
        this.updateTotalValue();
        this.loadingBalance = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading balance:', err);
        this.error = 'Failed to load account balance';
        this.loadingBalance = false;
        this.cdr.detectChanges();
      }
    });

    // Load holdings
    this.api.getHoldings(this.accountId).subscribe({
      next: (holdings) => {
        this.portfolio.holdings = holdings;
        this.updateTotalValue();
        this.loadingHoldings = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading holdings:', err);
        this.error = 'Failed to load holdings';
        this.loadingHoldings = false;
        this.cdr.detectChanges();
      }
    });

    // Load orders
    this.api.getOrders(this.accountId).subscribe({
      next: (orders) => {
        this.orders = orders;
        this.loadingOrders = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading orders:', err);
        this.error = 'Failed to load orders';
        this.loadingOrders = false;
        this.cdr.detectChanges();
      }
    });

    // Load trades
    this.api.getTrades(this.accountId).subscribe({
      next: (trades) => {
        this.trades = trades;
        this.loadingTrades = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading trades:', err);
        this.loadingTrades = false;
        this.cdr.detectChanges();
      }
    });

    // Load symbol trends (for heatmap)
    this.api.getSymbolTrends().subscribe({
      next: (trends) => {
        this.symbolTrends = trends.sort((a, b) => b.popularityScore - a.popularityScore);
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading symbol trends:', err);
        // Non-critical error, continue without heatmap data
      }
    });
  }

  // Form Validation Methods
  validateOrderForm(): boolean {
    this.orderFormErrors = {};

    if (!this.orderForm.symbol || this.orderForm.symbol.trim() === '') {
      this.orderFormErrors['symbol'] = 'Symbol is required';
    } else if (!/^[A-Z]{1,5}$/.test(this.orderForm.symbol.toUpperCase())) {
      this.orderFormErrors['symbol'] = 'Invalid symbol format (e.g., AAPL, MSFT)';
    }

    if (!this.orderForm.quantity || this.orderForm.quantity <= 0) {
      this.orderFormErrors['quantity'] = 'Quantity must be greater than 0';
    } else if (!Number.isInteger(this.orderForm.quantity)) {
      this.orderFormErrors['quantity'] = 'Quantity must be a whole number';
    }

    if (!this.orderForm.price || this.orderForm.price <= 0) {
      this.orderFormErrors['price'] = 'Price must be greater than 0';
    }

    return Object.keys(this.orderFormErrors).length === 0;
  }

  isFormValid(): boolean {
    return this.validateOrderForm();
  }

  placeOrder() {
    if (!this.validateOrderForm()) {
      return;
    }

    this.loading = true;
    this.api.createOrder(this.accountId, {
      symbol: this.orderForm.symbol.toUpperCase(),
      side: this.orderForm.side as 'BUY' | 'SELL',
      quantity: this.orderForm.quantity,
      price: this.orderForm.price,
      status: 'PENDING'
    }).subscribe({
      next: (order) => {
        this.loading = false;
        this.orderFormErrors = {};
        this.orderForm = { symbol: '', side: 'BUY', quantity: 0, price: 0 };
        this.loadDashboardData(); // Refresh orders list
      },
      error: (err) => {
        console.error('Error placing order:', err);
        this.orderFormErrors['submit'] = 'Failed to place order. Please try again.';
        this.loading = false;
      }
    });
  }

  // Cancel Order
  cancelOrder(orderId: string) {
    if (!confirm('Are you sure you want to cancel this order?')) {
      return;
    }

    this.cancelingOrderId = orderId;
    this.api.cancelOrder(this.accountId, orderId).subscribe({
      next: () => {
        this.cancelingOrderId = null;
        this.loadDashboardData(); // Refresh orders list
      },
      error: (err) => {
        console.error('Error canceling order:', err);
        alert('Failed to cancel order');
        this.cancelingOrderId = null;
      }
    });
  }

  // Refresh Data
  refreshData() {
    this.loadDashboardData();
  }

  // Helper Methods
  getOrderStatusClass(status: string): string {
    switch (status) {
      case 'FILLED':
        return 'status-filled';
      case 'PENDING':
        return 'status-pending';
      case 'CANCELLED':
        return 'status-cancelled';
      default:
        return '';
    }
  }

  canCancelOrder(order: Order): boolean {
    return order.status === 'PENDING';
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

  // Get sector for symbol
  getSymbolSector(symbol: string): string {
    const techSymbols = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'META', 'NFLX', 'TSLA', 'NVIDIA', 'AMD', 'INTEL'];
    const financeSymbols = ['JPM', 'BAC', 'GS', 'MS', 'AXP', 'PYPL', 'SQ', 'CIB', 'USB', 'WFC'];
    const healthSymbols = ['JNJ', 'PFE', 'MRK', 'ABBV', 'LLY', 'AbbVie', 'UNH', 'CVS', 'MCK', 'TMDX'];
    const energySymbols = ['XOM', 'CVX', 'MPC', 'PSX', 'COP', 'SLB', 'EOG', 'MUR', 'FANG', 'DVN'];
    
    if (techSymbols.includes(symbol)) return 'Technology';
    if (financeSymbols.includes(symbol)) return 'Finance';
    if (healthSymbols.includes(symbol)) return 'Healthcare';
    if (energySymbols.includes(symbol)) return 'Energy';
    return 'Other';
  }

  // Fire gradient heatmap color scheme (hot activity indicator)
  getPopularityColor(score: number): string {
    if (score >= 85) return '#dc2626'; // Bright red
    if (score >= 70) return '#ea580c'; // Red-orange
    if (score >= 55) return '#f59e0b'; // Orange
    if (score >= 40) return '#fbbf24'; // Amber
    if (score >= 25) return '#fce7b8'; // Light amber
    return '#fffbeb'; // Very light cream
  }

  // Calculate size multiplier for treemap effect (1x to 3x)
  getSizeMultiplier(score: number): number {
    return 1 + (score / 100) * 2;
  }

  getPopularityIntensity(score: number): number {
    return 0.8 + (score / 100) * 0.2;
  }
}

