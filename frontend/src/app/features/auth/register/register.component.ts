import { UI_COMPONENTS } from '../../../shared/ui';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { finalize, timeout } from 'rxjs';

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
    if (Validators.email(new FormControl(this.username.trim()))) {
      this.error = 'Username must be a valid email address';
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
        this.error = error.status === 409
          ? 'That username already exists'
          : error.name === 'TimeoutError'
            ? 'The authentication service did not respond. Check that the auth stub is running.'
            : 'Unable to create your profile';
      }
    });
  }
}
