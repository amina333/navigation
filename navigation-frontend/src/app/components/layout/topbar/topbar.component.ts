import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { RoleService } from '../../../services/role.service';
import { NotificationService } from '../../../services/notification.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './topbar.component.html',
  styleUrls: ['./topbar.component.scss']
})
export class TopbarComponent implements OnInit {
  
  currentUser: any;
  notifications: any[] = [];
  unreadCount = 0;
  showNotifications = false;
  showProfile = false;
  currentPage = 'Tableau de bord';

  constructor(
    private authService: AuthService,
    private roleService: RoleService,
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getCurrentUser();
    this.loadNotifications();
    this.updateCurrentPage();
  }

  loadNotifications(): void {
    this.notificationService.notifications$.subscribe(notifications => {
      this.notifications = notifications;
      this.unreadCount = notifications.filter((n: any) => !n.read).length;
    });
    // Seed des notifications de bienvenue (locale)
    this.addSampleNotifications();
  }

  addSampleNotifications(): void {
    // Utilise les types corrects de l'enum NotificationType
    this.notificationService.addNotification(
      'Bienvenue sur Navigation Aérienne',
      'Système de navigation aérienne opérationnel.',
      'SYSTEM' as any
    );
    this.notificationService.addNotification(
      'Vol AF1234 — À l\'heure',
      'Atterrissage confirmé à 14h30.',
      'FLIGHT_UPDATE' as any
    );
    this.notificationService.addNotification(
      'Mise à jour planifiée',
      'Maintenance système prévue à 22h00.',
      'SYSTEM' as any
    );
  }

  updateCurrentPage(): void {
    const url = this.router.url;
    if (url.includes('/flights')) {
      this.currentPage = 'Gestion des vols';
    } else if (url.includes('/users')) {
      this.currentPage = 'Gestion des utilisateurs';
    } else if (url.includes('/chat')) {
      this.currentPage = 'Chat';
    } else if (url.includes('/profile')) {
      this.currentPage = 'Mon profil';
    } else if (url.includes('/settings')) {
      this.currentPage = 'Paramètres';
    } else {
      this.currentPage = 'Tableau de bord';
    }
  }

  toggleNotifications(): void {
    this.showNotifications = !this.showNotifications;
    this.showProfile = false;
  }

  toggleProfile(): void {
    this.showProfile = !this.showProfile;
    this.showNotifications = false;
  }

  markAsRead(notificationId: string): void {
    // Mise à jour locale uniquement
    const notifs = this.notifications.map((n: any) =>
      n.id === notificationId ? { ...n, read: true } : n
    );
    this.notifications = notifs;
    this.unreadCount = notifs.filter((n: any) => !n.read).length;
  }

  markAllAsRead(): void {
    // Mise à jour locale uniquement
    this.notifications = this.notifications.map((n: any) => ({ ...n, read: true }));
    this.unreadCount = 0;
  }

  viewAllNotifications(): void {
    this.router.navigate(['/notifications']);
    this.showNotifications = false;
  }

  goToProfile(): void {
    this.router.navigate(['/profile']);
    this.showProfile = false;
  }

  goToSettings(): void {
    this.router.navigate(['/settings']);
    this.showProfile = false;
    this.showNotifications = false;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  getRoleDisplayName(): string {
    const role = this.roleService.getHighestPriorityRole();
    return this.roleService.getRoleDisplayName(role);
  }

  getRoleClass(): string {
    const role = this.roleService.getHighestPriorityRole();
    return role.toLowerCase();
  }

  formatTime(timestamp: Date): string {
    const now = new Date();
    const date = new Date(timestamp);
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'À l\'instant';
    if (diffMins < 60) return `Il y a ${diffMins} min`;
    if (diffMins < 1440) return `Il y a ${Math.floor(diffMins / 60)}h`;
    return `Il y a ${Math.floor(diffMins / 1440)}j`;
  }

  toggleSidebar(): void {
    // This would be handled by the main layout component
    document.body.classList.toggle('sidebar-collapsed');
  }
}
