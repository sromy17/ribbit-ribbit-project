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

  // Heatmap color based on popularity
  getPopularityColor(score: number): string {
    if (score >= 80) return '#2e7d32'; // Dark green
    if (score >= 60) return '#66bb6a'; // Green
    if (score >= 40) return '#fff176'; // Yellow
    if (score >= 20) return '#ffb74d'; // Orange
    return '#ef5350'; // Red
  }

  getPopularityIntensity(score: number): number {
    return 0.3 + (score / 100) * 0.7;
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
