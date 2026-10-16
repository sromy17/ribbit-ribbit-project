import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, map, tap } from 'rxjs';
import { HttpClient } from '@angular/common/http';

export interface User {
  id: string;
  username: string;
  role: 'investor' | 'admin' | 'analyst';
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

  login(username: string, password: string): Observable<User> {
    return this.http.post<AuthResponse>('/auth/login', { username, password }).pipe(
      tap((response) => {
      localStorage.setItem('accessToken', response.token);
      localStorage.setItem('refreshToken', String(response.refreshToken ?? ''));
      }),
      // The current auth stub supplies a mission-operator role. Until backend
      // role claims are available, authenticated users use the investor flow.
      tap((response) => {
      const user = {
        id: String(response.userId),
        username: response.username,
        role: 'investor' as const
      };
      this.currentUser.next(user);
      localStorage.setItem('currentUser', JSON.stringify(user));
      }),
      map(() => this.currentUser.value as User)
    );
  }

  logout(): void {
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
