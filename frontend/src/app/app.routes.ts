import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { AuthGuard } from './core/guards/auth.guard';
import { AdminDashboardComponent } from './features/dashboard/admin/admin-dashboard.component';
import { AnalystDashboardComponent } from './features/dashboard/analyst/analyst-dashboard.component';
import { DashboardComponent } from './features/dashboard/investor/dashboard.component';
import { RegisterComponent } from './features/auth/register/register.component';
import { CreateAccountComponent } from './features/accounts/create-account/create-account.component';
import { AccountDashboardComponent } from './features/accounts/account-dashboard/account-dashboard.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'employee-login', component: LoginComponent, data: { employeeLogin: true } },
  { path: 'register', component: RegisterComponent },
  {
    path: 'accounts',
    component: AccountDashboardComponent,
    canActivate: [AuthGuard],
    data: { role: 'investor' }
  },
  {
    path: 'accounts/create',
    component: CreateAccountComponent,
    canActivate: [AuthGuard],
    data: { role: 'investor' }
  },
  { path: 'investor-dashboard', redirectTo: 'accounts', pathMatch: 'full' },
  {
    path: 'investor-dashboard/:accountId',
    component: DashboardComponent,
    canActivate: [AuthGuard],
    data: { role: 'investor' }
  },
  {
    path: 'admin-dashboard',
    component: AdminDashboardComponent,
    canActivate: [AuthGuard],
    data: { role: 'admin' }
  },
  {
    path: 'analyst-dashboard',
    component: AnalystDashboardComponent,
    canActivate: [AuthGuard],
    data: { role: 'analyst' }
  },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' }
];
