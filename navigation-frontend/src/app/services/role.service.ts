import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { User } from '../models/user.model';

export type UserRole = 'ADMIN' | 'SUPERVISEUR' | 'ATC' | 'PISTE' | 'AIS' | 'RH_FORMATION' | 'QUALITE' | 'CONSULTATION';

export interface RolePermissions {
  canManageUsers: boolean;
  canManageFlights: boolean;
  canManageAircraft: boolean;
  canViewReports: boolean;
  canAccessChat: boolean;
  canManageSystem: boolean;
  canViewDashboard: boolean;
  canManageCrew: boolean;
  canViewFlightPlans: boolean;
  canAccessNavigation: boolean;
}

@Injectable({ providedIn: 'root' })
export class RoleService {
  
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  private rolePermissions: Record<UserRole, RolePermissions> = {
    ADMIN: {
      canManageUsers: true,
      canManageFlights: true,
      canManageAircraft: true,
      canViewReports: true,
      canAccessChat: true,
      canManageSystem: true,
      canViewDashboard: true,
      canManageCrew: true,
      canViewFlightPlans: true,
      canAccessNavigation: true
    },
    SUPERVISEUR: {
      canManageUsers: true,
      canManageFlights: true,
      canManageAircraft: false,
      canViewReports: true,
      canAccessChat: true,
      canManageSystem: false,
      canViewDashboard: true,
      canManageCrew: true,
      canViewFlightPlans: true,
      canAccessNavigation: true
    },
    ATC: {
      canManageUsers: false,
      canManageFlights: true,
      canManageAircraft: false,
      canViewReports: true,
      canAccessChat: true,
      canManageSystem: false,
      canViewDashboard: true,
      canManageCrew: false,
      canViewFlightPlans: true,
      canAccessNavigation: true
    },
    PISTE: {
      canManageUsers: false,
      canManageFlights: true,
      canManageAircraft: false,
      canViewReports: true,
      canAccessChat: true,
      canManageSystem: false,
      canViewDashboard: true,
      canManageCrew: false,
      canViewFlightPlans: true,
      canAccessNavigation: false
    },
    AIS: {
      canManageUsers: false,
      canManageFlights: true,
      canManageAircraft: false,
      canViewReports: true,
      canAccessChat: true,
      canManageSystem: false,
      canViewDashboard: true,
      canManageCrew: false,
      canViewFlightPlans: true,
      canAccessNavigation: true
    },
    RH_FORMATION: {
      canManageUsers: true,
      canManageFlights: false,
      canManageAircraft: false,
      canViewReports: true,
      canAccessChat: true,
      canManageSystem: false,
      canViewDashboard: true,
      canManageCrew: true,
      canViewFlightPlans: false,
      canAccessNavigation: false
    },
    QUALITE: {
      canManageUsers: false,
      canManageFlights: true,
      canManageAircraft: false,
      canViewReports: true,
      canAccessChat: true,
      canManageSystem: false,
      canViewDashboard: true,
      canManageCrew: false,
      canViewFlightPlans: true,
      canAccessNavigation: false
    },
    CONSULTATION: {
      canManageUsers: false,
      canManageFlights: false,
      canManageAircraft: false,
      canViewReports: false,
      canAccessChat: true,
      canManageSystem: false,
      canViewDashboard: false,
      canManageCrew: false,
      canViewFlightPlans: false,
      canAccessNavigation: false
    }
  };

  constructor() {
    // Initialize user from localStorage if available
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      this.currentUserSubject.next(JSON.parse(storedUser));
    }
  }

  setCurrentUser(user: User): void {
    this.currentUserSubject.next(user);
    localStorage.setItem('currentUser', JSON.stringify(user));
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  getUserRoles(): UserRole[] {
    const user = this.getCurrentUser();
    if (!user) return [];
    return user.roles as UserRole[];
  }

  getHighestPriorityRole(): UserRole {
    const roles = this.getUserRoles();
    const rolePriority = ['ADMIN', 'SUPERVISEUR', 'ATC', 'PISTE', 'AIS', 'RH_FORMATION', 'QUALITE', 'CONSULTATION'];
    
    for (const role of rolePriority) {
      if (roles.includes(role as UserRole)) {
        return role as UserRole;
      }
    }
    
    return 'CONSULTATION';
  }

  hasRole(role: UserRole): boolean {
    return this.getUserRoles().includes(role);
  }

  hasAnyRole(roles: UserRole[]): boolean {
    return roles.some(role => this.hasRole(role));
  }

  getPermissions(): RolePermissions {
    const userRole = this.getHighestPriorityRole();
    return this.rolePermissions[userRole];
  }

  hasPermission(permission: keyof RolePermissions): boolean {
    const permissions = this.getPermissions();
    return permissions[permission];
  }

  canAccessRoute(route: string): boolean {
    const permissions = this.getPermissions();
    
    switch (route) {
      case '/admin':
      case '/users':
        return this.hasRole('ADMIN');
      case '/flights':
        return permissions.canManageFlights;
      case '/aircraft':
        return permissions.canManageAircraft;
      case '/reports':
        return permissions.canViewReports;
      case '/dashboard':
        return permissions.canViewDashboard;
      case '/navigation':
        return permissions.canAccessNavigation;
      case '/chat':
        return permissions.canAccessChat;
      default:
        return true;
    }
  }

  getRoleDisplayName(role: UserRole): string {
    const roleNames = {
      ADMIN: 'Administrateur',
      SUPERVISEUR: 'Superviseur',
      ATC: 'Contrôleur aérien',
      PISTE: 'Agent de piste',
      AIS: 'AIS',
      RH_FORMATION: 'RH & Formation',
      QUALITE: 'Qualité',
      CONSULTATION: 'Consultation'
    };
    return roleNames[role] || 'Inconnu';
  }

  getRoleIcon(role: UserRole): string {
    const icons = {
      ADMIN: 'shield',
      SUPERVISEUR: 'user-shield',
      ATC: 'radio',
      PISTE: 'navigation',
      AIS: 'compass',
      RH_FORMATION: 'graduation-cap',
      QUALITE: 'check-circle',
      CONSULTATION: 'user'
    };
    return icons[role] || 'user';
  }

  getRoleColor(role: UserRole): string {
    const colors = {
      ADMIN: '#ef4444',
      SUPERVISEUR: '#f59e0b',
      ATC: '#3b82f6',
      PISTE: '#10b981',
      AIS: '#8b5cf6',
      RH_FORMATION: '#ec4899',
      QUALITE: '#06b6d4',
      CONSULTATION: '#6b7280'
    };
    return colors[role] || '#6b7280';
  }

  logout(): void {
    this.currentUserSubject.next(null);
    localStorage.removeItem('currentUser');
  }
}
