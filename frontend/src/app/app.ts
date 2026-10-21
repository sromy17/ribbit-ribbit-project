import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { ThemeService } from './core/services/theme.service';
import { AuthService } from './core/services/auth.service';

@Component({
  imports: [CommonModule, RouterOutlet],
  selector: 'app-root',
  template: `
    <router-outlet></router-outlet>
    <div *ngIf="auth.sessionWarning$ | async" class="session-warning-backdrop">
      <section class="session-warning" role="alertdialog" aria-modal="true"
        aria-labelledby="session-warning-title">
        <h2 id="session-warning-title">Session expiring</h2>
        <p>Your session is about to expire. Do you want to extend your session?</p>
        <div class="session-warning-actions">
          <button type="button" class="session-warning-secondary" (click)="declineSession()">
            No
          </button>
          <button type="button" class="session-warning-primary" (click)="extendSession()">
            Yes
          </button>
        </div>
      </section>
    </div>
    <div *ngIf="sessionExpired" class="session-expired-backdrop">
      <section class="session-expired" role="alertdialog" aria-modal="true"
        aria-labelledby="session-expired-title">
        <h2 id="session-expired-title">Session Expired</h2>
        <p>Your session has expired.</p>
        <button type="button" class="session-warning-primary" (click)="returnToSignIn()">
          Click here to return to Sign In
        </button>
      </section>
    </div>
  `,
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {
  private sessionSubscription?: Subscription;
  sessionExpired = false;

  constructor(
    private themeService: ThemeService,
    public auth: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.themeService.initializeTheme();
    this.sessionSubscription = this.auth.sessionExpired$.subscribe(() => {
      this.sessionExpired = true;
    });
  }

  extendSession(): void {
    this.auth.refreshSession().subscribe({
      error: () => {
        this.auth.logout();
        this.router.navigate(['/login']);
      }
    });
  }

  declineSession(): void {
    this.auth.declineSessionExtension();
  }

  returnToSignIn(): void {
    this.sessionExpired = false;
    this.router.navigate(['/login']);
  }

  ngOnDestroy(): void {
    this.sessionSubscription?.unsubscribe();
  }
}
