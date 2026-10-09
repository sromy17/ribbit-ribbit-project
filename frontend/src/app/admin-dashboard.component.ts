import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService, User } from './auth.service';
import { ApiService, InvestorInfo, Trade, Order, PlatformStats, SymbolTrend } from './api.service';

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
  symbolTrends: SymbolTrend[] = [];

  // UI State
  activeTab: 'overview' | 'heatmap' | 'investors' | 'trades' | 'orders' = 'overview';
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

    // Load symbol trends (for heatmap)
    this.api.getSymbolTrends().subscribe({
      next: (trends) => {
        this.symbolTrends = trends.sort((a, b) => b.popularityScore - a.popularityScore);
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading symbol trends:', err);
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

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
