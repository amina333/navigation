import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { UserRole } from '../../../models/user.model';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss']
})
export class MainLayoutComponent {
  isMenuOpen = false;
  
  constructor(private authService: AuthService) {}

  get isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  get isSuperviseur(): boolean {
    return this.authService.isSuperviseur();
  }

  get userName(): string {
    return this.authService.getCurrentUser()?.fullName || 'Agent';
  }

  get userInitials(): string {
    const name = this.userName;
    if (!name) return 'A';
    const parts = name.split(' ');
    return parts.length > 1 ? parts[0][0] + parts[1][0] : parts[0][0];
  }

  get primaryRole(): string {
    const roles = this.authService.getCurrentUser()?.roles || [];
    if (roles.includes('ADMIN' as UserRole)) return 'Admin';
    if (roles.includes('SUPERVISEUR' as UserRole)) return 'Superviseur';
    if (roles.includes('ATC' as UserRole)) return 'Contrôle (ATC)';
    if (roles.includes('PISTE' as UserRole)) return 'Piste';
    return 'Agent';
  }

  logout(): void {
    this.authService.logout();
  }
}
