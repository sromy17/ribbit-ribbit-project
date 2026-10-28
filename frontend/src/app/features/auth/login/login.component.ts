import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  username = '';
  password = '';
  error = '';
  loading = false;
  readonly employeeLogin: boolean;

  constructor(
    public auth: AuthService,
    public router: Router,
    private changeDetector: ChangeDetectorRef,
    route: ActivatedRoute
  ) {
    this.employeeLogin = route.snapshot.data['employeeLogin'] === true;
  }

  login(): void {
    if (this.loading) {
      return;
    }
    this.error = '';
    const username = this.username.trim();

    if (!username || !this.password) {
      this.error = 'Username and password are required';
      return;
    }

    this.loading = true;

    this.auth.login(username, this.password, this.employeeLogin).subscribe({
      next: (user) => {
        if (user.role === 'investor') {
          this.router.navigate(['/accounts']);
        } else {
          this.router.navigate([user.role === 'admin' ? '/admin-dashboard' : '/analyst-dashboard']);
        }
      },
      error: (error) => {
        this.error = error.error?.message || error.message || 'Invalid credentials';
        this.loading = false;
        this.changeDetector.detectChanges();
      },
      complete: () => {
        this.loading = false;
        this.changeDetector.detectChanges();
      }
    });
  }
}
