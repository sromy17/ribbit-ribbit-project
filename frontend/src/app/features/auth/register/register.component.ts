import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { finalize, timeout } from 'rxjs';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {
  username = '';
  password = '';
  error = '';
  loading = false;

  constructor(private auth: AuthService, public router: Router) {}

  register(): void {
    this.error = '';
    if (!this.username.trim() || !this.password) {
      this.error = 'Username and password are required';
      return;
    }

    this.loading = true;
    this.auth.register({ username: this.username.trim(), password: this.password }).pipe(
      timeout(15000),
      finalize(() => {
        this.loading = false;
      })
    ).subscribe({
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
