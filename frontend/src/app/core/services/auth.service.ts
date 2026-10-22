import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, map, tap, switchMap, of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { RegisterRequest } from './api.service';

export interface User {
  id: string;
  username: string;
  role: 'investor' | 'admin' | 'analyst';
  permissions?: string[];
}

interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  refreshToken?: number;
  userId: number;
  username: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private currentUser = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUser.asObservable();

  constructor(private http: HttpClient) {
    const saved = localStorage.getItem('currentUser');
    if (saved) {
      this.currentUser.next(JSON.parse(saved));
    }
  }

  login(username: string, password: string, staff = false): Observable<User> {
    return this.http.post<AuthResponse>(staff ? '/api/login' : '/auth/login', { username, password }).pipe(
      tap((response) => {
      localStorage.setItem('staffSession', String(staff));
      localStorage.setItem('accessToken', response.token);
      localStorage.setItem('refreshToken', String(response.refreshToken ?? ''));
      }),
      switchMap((response) => staff ? this.http.get<{permissions: string[]}>('/api/admin/session').pipe(
        map(session => ({response, permissions: session.permissions}))
      ) : of({response, permissions: [] as string[]})),
      tap(({response, permissions}) => {
        const role: User['role'] = permissions.includes('PERM_USER_LOOKUP') ? 'admin'
          : permissions.includes('PERM_REPORTING') ? 'analyst' : 'investor';
        const user: User = {id: String(response.userId), username: response.username, role, permissions};
        this.currentUser.next(user);
        localStorage.setItem('currentUser', JSON.stringify(user));
      }),
      map(() => this.currentUser.value as User)
    );
  }

  register(request: RegisterRequest): Observable<{ userId: number; username: string }> {
    return this.http.post<{ userId: number; username: string }>('/auth/register', request);
  }

  logout(): void {
    const token = localStorage.getItem('accessToken');
    if (token && localStorage.getItem('staffSession') === 'true') {
      this.http.post('/api/logout', {accessToken: token}, {headers: {Authorization: `Bearer ${token}`}}).subscribe({error: () => { /* Local logout still completes if the server is unavailable. */ }});
    }
    localStorage.removeItem('staffSession');
    this.currentUser.next(null);
    localStorage.removeItem('currentUser');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  }

  isLoggedIn(): boolean {
    return this.currentUser.value !== null;
  }

  getUser(): User | null {
    return this.currentUser.value;
  }
}
