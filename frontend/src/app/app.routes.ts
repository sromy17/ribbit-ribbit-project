import { Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'accounts',
    loadComponent: () =>
      import('./features/accounts/account-dashboard/account-dashboard.component').then(
        (m) => m.AccountDashboardComponent,
      ),
    canActivate: [AuthGuard],
    data: { role: 'investor' },
  },
  {
    path: 'accounts/create',
    loadComponent: () =>
      import('./features/accounts/create-account/create-account.component').then(
        (m) => m.CreateAccountComponent,
      ),
    canActivate: [AuthGuard],
    data: { role: 'investor' },
  },
  { path: 'investor-dashboard', redirectTo: 'accounts', pathMatch: 'full' },
  {
    path: 'investor-dashboard/:accountId',
    loadComponent: () =>
      import('./features/dashboard/investor/dashboard.component').then((m) => m.DashboardComponent),
    canActivate: [AuthGuard],
    data: { role: 'investor' },
  },
  {
    path: 'admin-dashboard',
    loadComponent: () =>
      import('./features/dashboard/admin/admin-dashboard.component').then(
        (m) => m.AdminDashboardComponent,
      ),
    canActivate: [AuthGuard],
    data: { role: 'admin' },
  },
  {
    path: 'analyst-dashboard',
    loadComponent: () =>
      import('./features/dashboard/analyst/analyst-dashboard.component').then(
        (m) => m.AnalystDashboardComponent,
      ),
    canActivate: [AuthGuard],
    data: { role: 'analyst' },
  },
  { path: '', loadComponent: () => import('./features/landing/landing.component').then(m => m.LandingComponent), pathMatch: 'full' },
  { path: '**', redirectTo: '/login' },
];
