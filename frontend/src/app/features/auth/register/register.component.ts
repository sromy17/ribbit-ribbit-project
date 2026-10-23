import { UI_COMPONENTS } from '../../../shared/ui';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [...UI_COMPONENTS, CommonModule, FormsModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {
  username = '';
  password = '';
  firstName = '';
  lastName = '';
  error = '';
  loading = false;

  constructor(private auth: AuthService, public router: Router) {}

  register(): void {
    this.error = '';
    if (!this.firstName.trim() || !this.lastName.trim() || !this.username.trim() || !this.password) {
      this.error = 'First name, last name, username, and password are required';
      return;
    }
    if (this.password.length < 12) {
      this.error = 'Password must be at least 12 characters';
      return;
    }

    this.loading = true;
    this.auth.register({
      username: this.username.trim(),
      password: this.password,
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim()
    }).subscribe({
      next: () => this.router.navigate(['/login']),
      error: (error) => {
        this.error = error.status === 409 ? 'That username already exists' : 'Unable to create your profile';
        this.loading = false;
      }
    });
  }
}
