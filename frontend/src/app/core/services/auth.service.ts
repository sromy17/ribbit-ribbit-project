import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject, map, tap } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { RegisterRequest } from './api.service';

export interface User {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  role: 'investor' | 'admin' | 'analyst';
}

interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  refreshToken?: string;
  userId: number;
  username: string;
  firstName: string;
  lastName: string;
  role?: 'admin' | 'analyst';
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private currentUser = new BehaviorSubject<User | null>(null);
  private sessionWarning = new BehaviorSubject<boolean>(false);
  private sessionExpired = new Subject<void>();
  private warningTimer: ReturnType<typeof setTimeout> | undefined;
  private expirationTimer: ReturnType<typeof setTimeout> | undefined;
  private employeeSession = false;

  get loginPath(): string {
    return this.employeeSession ? '/employee-login' : '/login';
  }

  public currentUser$ = this.currentUser.asObservable();
  public sessionWarning$ = this.sessionWarning.asObservable();
  public sessionExpired$ = this.sessionExpired.asObservable();

  constructor(private http: HttpClient) {
    const saved = localStorage.getItem('currentUser');
    if (saved) {
      this.currentUser.next(JSON.parse(saved));
      this.employeeSession = this.currentUser.value?.role === 'admin' || this.currentUser.value?.role === 'analyst';
      this.scheduleSessionTimers();
    }
  }

  login(username: string, password: string, employee = false): Observable<User> {
    return this.http.post<AuthResponse>(employee ? '/auth/employee/login' : '/auth/login', { username, password }).pipe(
      map(response => {
        if (employee && response.role !== 'admin' && response.role !== 'analyst') {
          throw new Error('Employee login returned an invalid role');
        }
        return response;
      }),
      tap((response) => {
        this.employeeSession = employee;
        this.storeTokens(response);
      }),
      tap((response) => {
        const user: User = {
          id: String(response.userId),
          username: response.username,
          firstName: response.firstName,
          lastName: response.lastName,
          role: employee ? (response.role === 'admin' ? 'admin' : 'analyst') : 'investor'
        };
        this.currentUser.next(user);
        localStorage.setItem('currentUser', JSON.stringify(user));
      }),
      map(() => this.currentUser.value as User)
    );
  }

  refreshSession(): Observable<AuthResponse> {
    const refreshToken = localStorage.getItem('refreshToken');
    const employee = this.currentUser.value?.role === 'admin' || this.currentUser.value?.role === 'analyst';
    return this.http.post<AuthResponse>(employee ? '/auth/employee/refresh' : '/auth/refresh', {
      refreshToken
    }).pipe(
      tap((response) => {
        if (employee && response.role !== this.currentUser.value?.role) {
          this.storeTokens(response);
          this.logout();
          throw new Error('Your employee role has changed. Sign in again.');
        }
        this.storeTokens(response);
        this.sessionWarning.next(false);
      })
    );
  }

  declineSessionExtension(): void {
    this.sessionWarning.next(false);
  }

  register(request: RegisterRequest): Observable<{
    userId: number;
    username: string;
    firstName: string;
    lastName: string;
  }> {
    return this.http.post<{
      userId: number;
      username: string;
      firstName: string;
      lastName: string;
    }>('/auth/register', request);
  }

  logout(): void {
    const user = this.currentUser.value;
    const token = localStorage.getItem('accessToken');
    if (token && (user?.role === 'admin' || user?.role === 'analyst')) {
      this.http.post('/auth/employee/logout', { accessToken: token }).subscribe({
        error: error => console.error('Employee session revocation failed', error)
      });
    }
    this.clearSessionTimers();
    this.currentUser.next(null);
    this.sessionWarning.next(false);
    localStorage.removeItem('currentUser');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('sessionExpiresAt');
  }

  isLoggedIn(): boolean {
    return this.currentUser.value !== null;
  }

  getUser(): User | null {
    return this.currentUser.value;
  }

  private storeTokens(response: AuthResponse): void {
    localStorage.setItem('accessToken', response.token);
    localStorage.setItem('refreshToken', String(response.refreshToken ?? ''));
    localStorage.setItem(
      'sessionExpiresAt',
      String(Date.now() + response.expiresInSeconds * 1000)
    );
    this.scheduleSessionTimers(response.expiresInSeconds);
  }

  private scheduleSessionTimers(expiresInSeconds?: number): void {
    this.clearSessionTimers();
    const storedExpiry = Number(localStorage.getItem('sessionExpiresAt'));
    const expiresAt = expiresInSeconds
      ? Date.now() + expiresInSeconds * 1000
      : storedExpiry;

    if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
      this.expireSession();
      return;
    }

    const timeUntilExpiry = expiresAt - Date.now();
    const warningDelay = Math.max(0, timeUntilExpiry - 2 * 60 * 1000);
    this.warningTimer = setTimeout(() => {
      this.sessionWarning.next(true);
    }, warningDelay);
    this.expirationTimer = setTimeout(() => {
      this.expireSession();
    }, timeUntilExpiry);
  }

  private expireSession(): void {
    const wasLoggedIn = this.currentUser.value !== null;
    this.logout();
    if (wasLoggedIn) {
      this.sessionExpired.next();
    }
  }

  private clearSessionTimers(): void {
    if (this.warningTimer) {
      clearTimeout(this.warningTimer);
      this.warningTimer = undefined;
    }
    if (this.expirationTimer) {
      clearTimeout(this.expirationTimer);
      this.expirationTimer = undefined;
    }
  }
}
