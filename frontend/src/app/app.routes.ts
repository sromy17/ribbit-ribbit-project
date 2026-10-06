import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { DashboardComponent } from './dashboard.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'investor-dashboard', component: DashboardComponent },
  { path: 'admin-dashboard', component: DashboardComponent },
  { path: 'analyst-dashboard', component: DashboardComponent },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' }
];
