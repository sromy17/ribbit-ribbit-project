import { UI_COMPONENTS } from '../../../shared/ui';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-create-account',
  standalone: true,
  imports: [...UI_COMPONENTS, CommonModule, FormsModule],
  templateUrl: './create-account.component.html',
  styleUrls: ['./create-account.component.css']
})
export class CreateAccountComponent {
  accountType = 'TRADING';
  currency = 'USD';
  error = '';
  loading = false;
  readonly accountTypes = ['TRADING', 'INVESTMENT', 'DAYTRADING', 'PORTFOLIO', 'HEDGE', 'CASH', 'MARGIN'];

  constructor(
    private api: ApiService,
    private auth: AuthService,
    public router: Router
  ) {}

  createAccount(): void {
    this.error = '';
    const user = this.auth.getUser();
    if (!user) {
      this.router.navigate(['/login']);
      return;
    }

    this.loading = true;
    this.api.createAccount({
      userId: Number(user.id),
      accountType: this.accountType,
      currency: this.currency
    }).subscribe({
      next: (account) => this.router.navigate(['/investor-dashboard', account.accountId]),
      error: () => {
        this.error = 'Unable to create the account';
        this.loading = false;
      }
    });
  }
}
