import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { RoleService, UserRole } from '../../../services/role.service';
import { AuthService } from '../../../services/auth.service';
import { AdminDashboardComponent } from '../admin-dashboard/admin-dashboard.component';
import { PilotDashboardComponent } from '../pilot-dashboard/pilot-dashboard.component';
import { NavigationDashboardComponent } from '../navigation-dashboard/navigation-dashboard.component';

@Component({
  selector: 'app-role-based-dashboard',
  standalone: true,
  imports: [CommonModule, AdminDashboardComponent, PilotDashboardComponent, NavigationDashboardComponent],
  templateUrl: './role-based-dashboard.component.html',
  styleUrls: ['./role-based-dashboard.component.scss']
})
export class RoleBasedDashboardComponent implements OnInit {
  
  isLoading = true;
  userRole: UserRole | null = null;
  currentUser: any;

  constructor(
    private router: Router,
    private roleService: RoleService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.initializeDashboard();
  }

  initializeDashboard(): void {
    // Check if user is authenticated
    this.currentUser = this.authService.getCurrentUser();
    
    if (!this.currentUser) {
      this.isLoading = false;
      return;
    }

    // Set current user in role service
    this.roleService.setCurrentUser(this.currentUser);

    // Get user's highest priority role
    this.userRole = this.roleService.getHighestPriorityRole();
    
    // Simulate loading time
    setTimeout(() => {
      this.isLoading = false;
    }, 500);
  }

  navigateTo(route: string): void {
    this.router.navigate([route]);
  }
}
