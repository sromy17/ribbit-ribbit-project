import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface User {
  id: string;
  username: string;
  role: 'investor' | 'admin' | 'analyst';
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private currentUser = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUser.asObservable();

  constructor() {
    // Load from localStorage on app init
    const saved = localStorage.getItem('currentUser');
    if (saved) {
      this.currentUser.next(JSON.parse(saved));
    }
  }

  login(username: string, password: string): boolean {
    // Mock users - replace with real API call later
    const mockUsers: Record<string, User> = {
      'investor@test.com': { id: '1', username: 'investor@test.com', role: 'investor' },
      'admin@test.com': { id: '2', username: 'admin@test.com', role: 'admin' },
      'analyst@test.com': { id: '3', username: 'analyst@test.com', role: 'analyst' }
    };

    const user = mockUsers[username];
    if (user && password === 'password') {
      this.currentUser.next(user);
      localStorage.setItem('currentUser', JSON.stringify(user));
      return true;
    }
    return false;
  }

  logout() {
    this.currentUser.next(null);
    localStorage.removeItem('currentUser');
  }

  isLoggedIn(): boolean {
    return this.currentUser.value !== null;
  }

  getUser(): User | null {
    return this.currentUser.value;
  }
}
