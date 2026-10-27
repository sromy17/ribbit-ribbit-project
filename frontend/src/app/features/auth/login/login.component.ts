import { UI_COMPONENTS } from '../../../shared/ui';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [...UI_COMPONENTS, CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  private route = inject(ActivatedRoute, { optional: true });
  ngOnInit(): void {
    const type = this.route?.snapshot.queryParamMap.get('type');
    if (type === 'user' || type === 'staff') this.chooseSignIn(type);
  }
  username = '';
  signInType: 'user' | 'staff' | null = null;
  get staffLogin(): boolean { return this.signInType === 'staff'; }

  chooseSignIn(type: 'user' | 'staff' | null): void {
    this.signInType = type;
    this.username = '';
    this.password = '';
    this.error = '';
  }
  password = '';
  error = '';
  loading = false;

  constructor(
    public auth: AuthService,
    public router: Router,
    private changeDetector: ChangeDetectorRef
  ) {}

  login(): void {
    if (!this.signInType || this.loading) return;
    this.error = '';
    const username = this.username.trim();

    if (!username || !this.password) {
      this.error = 'Username and password are required';
      return;
    }

    this.loading = true;

    this.auth.login(username, this.password, this.staffLogin).subscribe({
      next: (user) => {
        const destination = user.role === 'admin' ? '/admin-dashboard' : user.role === 'analyst' ? '/analyst-dashboard' : '/accounts';
        this.router.navigate([destination]);
      },
      error: (error) => {
        this.error = error.error?.message || 'Invalid credentials';
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
