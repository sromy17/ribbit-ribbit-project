import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-create-account',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './create-account.component.html',
  styleUrls: ['./create-account.component.css']
})
export class CreateAccountComponent {
  accountType = 'TRADING';
  initialCashBalance: number | null = null;
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
    if (this.initialCashBalance === null || this.initialCashBalance <= 0) {
      this.error = 'Initial balance must be greater than 0';
      return;
    }

    const user = this.auth.getUser();
    if (!user) {
      this.router.navigate(['/login']);
      return;
    }

    this.loading = true;
    this.api.createAccount({
      userId: Number(user.id),
      accountType: this.accountType,
      initialCashBalance: this.initialCashBalance
    }).subscribe({
      next: () => this.router.navigate(['/investor-dashboard']),
      error: () => {
        this.error = 'Unable to create the account';
        this.loading = false;
      }
    });
  }
}
