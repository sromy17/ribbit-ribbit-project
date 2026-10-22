import { UI_COMPONENTS } from '../../../shared/ui';
import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [...UI_COMPONENTS, CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  username = '';
  staffLogin = false;
  password = '';
  error = '';
  loading = false;

  constructor(
    public auth: AuthService,
    public router: Router,
    private changeDetector: ChangeDetectorRef
  ) {}

  login(): void {
    this.error = '';
    const username = this.username.trim();

    if (!username || !this.password) {
      this.error = 'Username and password are required';
      return;
    }

    this.loading = true;

    this.auth.login(username, this.password).subscribe({
      next: (user) => {
        const destination = user.role === 'admin' ? '/admin-dashboard' : user.role === 'analyst' ? '/analyst-dashboard' : '/accounts';
        this.router.navigate([destination]);
      },
      error: () => {
        this.error = 'Invalid credentials';
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
