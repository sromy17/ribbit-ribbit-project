import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject, Observable, map, tap, switchMap, of, catchError, throwError } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { RegisterRequest } from './api.service';

export interface User {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  role: 'investor' | 'admin' | 'analyst';
  permissions?: string[];
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
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private currentUser = new BehaviorSubject<User | null>(null);
  private sessionWarning = new BehaviorSubject<boolean>(false);
  private sessionExpired = new Subject<void>();
  private warningTimer: ReturnType<typeof setTimeout> | undefined;
  private expirationTimer: ReturnType<typeof setTimeout> | undefined;

  public currentUser$ = this.currentUser.asObservable();
  public sessionWarning$ = this.sessionWarning.asObservable();
  public sessionExpired$ = this.sessionExpired.asObservable();

  constructor(private http: HttpClient) {
    const saved = localStorage.getItem('currentUser');
    if (saved) {
      try {
        this.currentUser.next(JSON.parse(saved));
        this.scheduleSessionTimers();
      } catch { this.logout(); }
    }
  }

  login(username: string, password: string, staff = false): Observable<User> {
    return this.http.post<AuthResponse>(staff ? '/api/login' : '/auth/login', { username, password }).pipe(
      switchMap(response => {
        localStorage.setItem('staffSession', String(staff));
        this.storeTokens(response);
        return (staff ? this.http.get<{permissions: string[]}>('/api/admin/session') : of({permissions: [] as string[]})).pipe(
          map(session => {
            const permissions = session.permissions;
            const admin = permissions.some(p => ['PERM_ORDER_INVESTIGATION', 'PERM_ACCOUNT_READ', 'PERM_DISPUTE_MANAGE', 'PERM_STAFF_ADMIN'].includes(p));
            if (staff && !admin && !permissions.includes('PERM_REPORTING')) throw new Error('This account has no staff workspace permission.');
            const user: User = {id: String(response.userId), username: response.username,
              firstName: response.firstName ?? '', lastName: response.lastName ?? '',
              role: staff ? (admin ? 'admin' : 'analyst') : 'investor', permissions};
            this.currentUser.next(user);
            localStorage.setItem('currentUser', JSON.stringify(user));
            return user;
          })
        );
      }),
      catchError(error => { this.logout(); return throwError(() => error); })
    );
  }

  refreshSession(): Observable<AuthResponse> {
    const refreshToken = localStorage.getItem('refreshToken');
    return this.http.post<AuthResponse>(localStorage.getItem('staffSession') === 'true' ? '/api/refresh' : '/auth/refresh', {
      refreshToken
    }).pipe(
      tap((response) => {
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
    const token = localStorage.getItem('accessToken');
    this.clearSessionTimers();
    if (token) {
      const url = localStorage.getItem('staffSession') === 'true' ? '/api/logout' : '/auth/logout';
      this.http.post(url, {accessToken: token}).subscribe({error: () => { /* Local logout completes when offline. */ }});
    }
    localStorage.removeItem('staffSession');
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
