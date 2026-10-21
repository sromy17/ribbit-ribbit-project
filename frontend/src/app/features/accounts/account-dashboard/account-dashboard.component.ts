import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService, User } from '../../../core/services/auth.service';
import { ApiService, TradingAccount } from '../../../core/services/api.service';

@Component({
  selector: 'app-account-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './account-dashboard.component.html',
  styleUrls: ['./account-dashboard.component.css']
})
export class AccountDashboardComponent implements OnInit {
  currentUser: User | null = null;
  accounts: TradingAccount[] = [];
  loading = false;
  error = '';

  constructor(
    private auth: AuthService,
    private api: ApiService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    this.currentUser = this.auth.getUser();
  }

  ngOnInit(): void {
    this.loadAccounts();
  }

  loadAccounts(): void {
    if (!this.currentUser) {
      this.router.navigate(['/login']);
      return;
    }

    this.loading = true;
    this.error = '';
    this.api.getAccountsForUser(this.currentUser.id).subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'Failed to load your accounts';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  openAccount(account: TradingAccount): void {
    this.router.navigate(['/investor-dashboard', account.accountId]);
  }

  createAccount(): void {
    this.router.navigate(['/accounts/create']);
  }

  formatAccountType(type: string): string {
    const normalizedType = type.toLowerCase();
    return `${normalizedType.charAt(0).toUpperCase()}${normalizedType.slice(1)} Account`;
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
