import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService, User } from '../../../core/services/auth.service';
import { ApiService, TradingAccount } from '../../../core/services/api.service';

@Component({
  selector: 'app-investor-accounts',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './investor-accounts.component.html',
  styleUrls: ['./investor-accounts.component.css']
})
export class InvestorAccountsComponent implements OnInit {
  currentUser: User | null = null;
  accounts: TradingAccount[] = [];
  loading = false;
  error = '';
  private createdAccountFromState: TradingAccount | null = null;

  constructor(
    private auth: AuthService,
    private api: ApiService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    this.currentUser = this.auth.getUser();
  }

  ngOnInit(): void {
    if (!this.currentUser) {
      this.router.navigate(['/login']);
      return;
    }

    const createdAccountFromState = history.state?.['createdAccount'] as TradingAccount | undefined;
    if (createdAccountFromState?.accountId) {
      this.createdAccountFromState = createdAccountFromState;
      this.accounts = this.mergeAccounts(this.accounts, [createdAccountFromState]);
    }

    this.loadAccounts();
  }

  loadAccounts(): void {
    if (!this.currentUser) {
      return;
    }

    this.loading = true;
    this.error = '';

    this.api.getAccountsForUser(this.currentUser.id).subscribe({
      next: (accounts) => {
        const accountsFromApi = accounts ?? [];
        this.accounts = this.createdAccountFromState?.accountId
          ? this.mergeAccounts(accountsFromApi, [this.createdAccountFromState])
          : accountsFromApi;
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
    this.router.navigate(['/investor-dashboard'], {
      queryParams: { accountId: account.accountId }
    });
  }

  createAccount(): void {
    this.router.navigate(['/accounts/create']);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  formatAccountType(type: string | null | undefined): string {
    const normalizedType = (type ?? 'TRADING').toLowerCase();
    return `${normalizedType.charAt(0).toUpperCase()}${normalizedType.slice(1)} Account`;
  }

  private mergeAccounts(existing: TradingAccount[], incoming: TradingAccount[]): TradingAccount[] {
    const byId = new Map<string, TradingAccount>();

    for (const account of existing) {
      if (account.accountId) {
        byId.set(String(account.accountId), account);
      }
    }

    for (const account of incoming) {
      if (account.accountId) {
        byId.set(String(account.accountId), account);
      }
    }

    return Array.from(byId.values());
  }
}
