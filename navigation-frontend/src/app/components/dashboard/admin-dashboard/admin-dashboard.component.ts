import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { UserService } from '../../../services/user.service';
import { FlightService } from '../../../services/flight.service';
import { UserRole } from '../../../models/user.model';
import { FlightStatus } from '../../../models/flight.model';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.scss']
})
export class AdminDashboardComponent implements OnInit {

  totalUsers = 0;
  totalFlights = 0;
  totalReports = 0;
  systemStatus = '99.8%';
  isLoading = false;

  // User data for role statistics
  allUsers: any[] = [];
  roleStats = {
    ADMIN: 0,
    SUPERVISEUR: 0,
    ATC: 0,
    PISTE: 0
  };

  // Flight data for statistics
  allFlights: any[] = [];
  flightStats = {
    PROGRAMME: 0,
    EN_VOL: 0,
    ARRIVE: 0,
    RETARDE: 0
  };

  // Recent activities will be loaded from backend
  recentActivities: any[] = [];

  constructor(
    private router: Router,
    private userService: UserService,
    private flightService: FlightService
  ) {}

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.isLoading = true;

    // Load users count and role statistics
    this.userService.getAllUsers().subscribe({
      next: (users) => {
        this.allUsers = users;
        this.totalUsers = users.length;
        this.calculateRoleStats();
      },
      error: (error) => {
        console.error('Error loading users:', error);
        this.isLoading = false;
      }
    });

    // Load flights count and status statistics
    this.flightService.getAllFlights().subscribe({
      next: (flights) => {
        this.allFlights = flights;
        this.totalFlights = flights.length;
        this.calculateFlightStats();
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading flights:', error);
        this.isLoading = false;
      }
    });

    // Load reports count from backend
    this.loadReportsCount();

    // Load recent activities from backend
    this.loadRecentActivities();
  }

  private calculateRoleStats(): void {
    this.roleStats = {
      ADMIN: 0,
      SUPERVISEUR: 0,
      ATC: 0,
      PISTE: 0
    };

    this.allUsers.forEach(user => {
      if (user.roles && user.roles.length > 0) {
        const primaryRole = user.roles[0];
        if (this.roleStats.hasOwnProperty(primaryRole)) {
          this.roleStats[primaryRole as keyof typeof this.roleStats]++;
        }
      }
    });
  }

  private calculateFlightStats(): void {
    this.flightStats = {
      PROGRAMME: 0,
      EN_VOL: 0,
      ARRIVE: 0,
      RETARDE: 0
    };

    this.allFlights.forEach(flight => {
      if (flight.status && this.flightStats.hasOwnProperty(flight.status)) {
        this.flightStats[flight.status as keyof typeof this.flightStats]++;
      }
    });
  }

  private loadReportsCount(): void {
    // This should be implemented in the backend
    // For now, we'll keep the mock value but it should be replaced
    this.totalReports = 24;
  }

  private loadRecentActivities(): void {
    // This should be implemented in the backend
    // For now, we'll keep the mock data but it should be replaced
    this.recentActivities = [
      { title: 'Nouvel utilisateur inscrit', time: 'Il y a 5 minutes', type: 'success' },
      { title: 'Vol AF1234 créé', time: 'Il y a 1 heure', type: 'success' },
      { title: 'Rapport mensuel généré', time: 'Il y a 2 heures', type: 'warning' },
      { title: 'Sauvegarde système réussie', time: 'Il y a 3 heures', type: 'success' },
      { title: 'Mise à jour système terminée', time: 'Il y a 1 jour', type: 'success' }
    ];
  }

  // Get role percentage for progress bars
  getRolePercentage(role: string): number {
    if (this.totalUsers === 0) return 0;
    const count = this.roleStats[role as keyof typeof this.roleStats] || 0;
    return Math.round((count / this.totalUsers) * 100);
  }

  // Get role count
  getRoleCount(role: string): number {
    return this.roleStats[role as keyof typeof this.roleStats] || 0;
  }

  // Get flight count by status
  getFlightCountByStatus(status: string): number {
    return this.flightStats[status as keyof typeof this.flightStats] || 0;
  }

  // Refresh data
  refreshData(): void {
    this.loadDashboardData();
  }

  // Time filter change handler
  onTimeFilterChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const filter = select.value;
    console.log('Time filter changed to:', filter);
    // Implement time-based filtering logic here
  }

  // View all activities
  viewAllActivities(): void {
    this.router.navigate(['/dashboard/activities']);
  }

  // Navigation method
  navigateTo(route: string): void {
    // Routes absolues vers le dashboard
    const routeMap: Record<string, string> = {
      'users':         '/dashboard/users',
      'users/new':     '/dashboard/users',
      'flights':       '/dashboard/flights',
      'flights/new':   '/dashboard/flights',
      'reports':       '/dashboard/activities',
      'settings':      '/dashboard/settings',
      'activities':    '/dashboard/activities',
    };
    const target = routeMap[route] || `/dashboard/${route}`;
    this.router.navigate([target]);
  }
}
