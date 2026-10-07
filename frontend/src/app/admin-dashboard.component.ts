import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService, User } from './auth.service';
import { ApiService, InvestorInfo, Trade, Order, PlatformStats } from './api.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
  currentUser: User | null = null;
  loading = true;
  error = '';

  // Data
  investors: InvestorInfo[] = [];
  trades: Trade[] = [];
  orders: Order[] = [];
  stats: PlatformStats | null = null;

  // UI State
  activeTab: 'overview' | 'investors' | 'trades' | 'orders' = 'overview';
  searchText = '';

  constructor(
    private auth: AuthService,
    private router: Router,
    private api: ApiService,
    private cdr: ChangeDetectorRef
  ) {
    this.currentUser = this.auth.getUser();
    if (!this.currentUser || this.currentUser.role !== 'admin') {
      this.router.navigate(['/login']);
    }
  }

  ngOnInit() {
    this.loadAdminData();
  }

  loadAdminData() {
    this.loading = true;
    this.error = '';

    // Load platform stats
    this.api.getPlatformStats().subscribe({
      next: (stats) => {
        this.stats = stats;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading stats:', err);
        this.error = 'Failed to load platform stats';
        this.cdr.detectChanges();
      }
    });

    // Load investors
    this.api.getAllInvestors().subscribe({
      next: (investors) => {
        this.investors = investors;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading investors:', err);
        this.error = 'Failed to load investors';
        this.cdr.detectChanges();
      }
    });

    // Load trades
    this.api.getAllTrades().subscribe({
      next: (trades) => {
        this.trades = trades;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading trades:', err);
        this.error = 'Failed to load trades';
        this.cdr.detectChanges();
      }
    });

    // Load orders
    this.api.getAllOrders().subscribe({
      next: (orders) => {
        this.orders = orders;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading orders:', err);
        this.error = 'Failed to load orders';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  getFilteredInvestors(): InvestorInfo[] {
    if (!this.searchText) return this.investors;
    return this.investors.filter(
      (inv) =>
        inv.email.toLowerCase().includes(this.searchText.toLowerCase()) ||
        inv.firstName.toLowerCase().includes(this.searchText.toLowerCase()) ||
        inv.lastName.toLowerCase().includes(this.searchText.toLowerCase())
    );
  }

  getFilteredTrades(): Trade[] {
    if (!this.searchText) return this.trades;
    return this.trades.filter((trade) => trade.symbol?.toLowerCase().includes(this.searchText.toLowerCase()));
  }

  getFilteredOrders(): Order[] {
    if (!this.searchText) return this.orders;
    return this.orders.filter((order) => order.symbol.toLowerCase().includes(this.searchText.toLowerCase()));
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
