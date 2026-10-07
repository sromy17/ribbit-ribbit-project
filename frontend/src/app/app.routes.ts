import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { DashboardComponent } from './dashboard.component';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { AnalystDashboardComponent } from './analyst-dashboard.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'investor-dashboard', component: DashboardComponent },
  { path: 'admin-dashboard', component: AdminDashboardComponent },
  { path: 'analyst-dashboard', component: AnalystDashboardComponent },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' }
];
