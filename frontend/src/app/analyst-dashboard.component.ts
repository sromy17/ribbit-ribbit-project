import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService, User } from './auth.service';
import { ApiService, SymbolTrend, MarketTrend } from './api.service';

@Component({
  selector: 'app-analyst-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './analyst-dashboard.component.html',
  styleUrls: ['./analyst-dashboard.component.css']
})
export class AnalystDashboardComponent implements OnInit {
  currentUser: User | null = null;
  loading = true;
  error = '';

  // Data
  symbolTrends: SymbolTrend[] = [];
  marketTrends: MarketTrend[] = [];

  // UI State
  activeTab: 'heatmap' | 'trends' = 'heatmap';

  constructor(
    private auth: AuthService,
    private router: Router,
    private api: ApiService,
    private cdr: ChangeDetectorRef
  ) {
    this.currentUser = this.auth.getUser();
    if (!this.currentUser || this.currentUser.role !== 'analyst') {
      this.router.navigate(['/login']);
    }
  }

  ngOnInit() {
    this.loadAnalystData();
  }

  loadAnalystData() {
    this.loading = true;
    this.error = '';

    // Load symbol trends (for heatmap)
    this.api.getSymbolTrends().subscribe({
      next: (trends) => {
        this.symbolTrends = trends.sort((a, b) => b.popularityScore - a.popularityScore);
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading symbol trends:', err);
        this.error = 'Failed to load symbol trends';
        this.cdr.detectChanges();
      }
    });

    // Load market trends
    this.api.getMarketTrends().subscribe({
      next: (trends) => {
        this.marketTrends = trends;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading market trends:', err);
        this.error = 'Failed to load market trends';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
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
