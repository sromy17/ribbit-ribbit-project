import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

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

  constructor(private auth: AuthService, private router: Router) {}

  login() {
    this.loading = true;
    this.error = '';

    // Simulate API delay
    setTimeout(() => {
      if (this.auth.login(this.username, this.password)) {
        const user = this.auth.getUser();
        if (user?.role === 'investor') {
          this.router.navigate(['/investor-dashboard']);
        } else if (user?.role === 'admin') {
          this.router.navigate(['/admin-dashboard']);
        } else if (user?.role === 'analyst') {
          this.router.navigate(['/analyst-dashboard']);
        }
      } else {
        this.error = 'Invalid credentials';
      }
      this.loading = false;
    }, 500);
  }
}
