import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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

  constructor(public auth: AuthService, public router: Router) {}

  login() {
    this.loading = true;
    this.error = '';

    this.auth.login(this.username, this.password).subscribe({
      next: (user) => {
        if (user.role === 'investor') {
          this.router.navigate(['/accounts']);
        }
      },
      error: () => {
        this.error = 'Invalid credentials';
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }
}
