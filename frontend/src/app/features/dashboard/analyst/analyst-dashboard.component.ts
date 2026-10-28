import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { UiEmptyStateComponent, UiPageHeadingComponent, UiWorkspaceComponent } from '../../../shared/ui';

@Component({
  selector: 'app-analyst-dashboard',
  standalone: true,
  imports: [CommonModule, UiWorkspaceComponent, UiPageHeadingComponent, UiEmptyStateComponent],
  templateUrl: './analyst-dashboard.component.html',
  styleUrls: ['../../../shared/ui/staff-dashboard.css', './analyst-dashboard.component.css']
})
export class AnalystDashboardComponent {
  readonly currentUser;
  readonly unavailableMetrics = ['Submitted orders', 'Executed orders', 'Recorded fills', 'Active clients'];

  constructor(private readonly auth: AuthService, private readonly router: Router) {
    this.currentUser = auth.getUser();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/employee-login']);
  }
}
