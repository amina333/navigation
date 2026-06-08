import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { AIAssistantComponent } from '../../components/chatbot/ai-assistant/ai-assistant.component';
import { UserRole } from '../../models/user.model';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet, AIAssistantComponent],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss']
})
export class MainLayoutComponent implements OnInit, OnDestroy {

  // ── Sidebar : OUVERTE par défaut ─────────────────────────
  isSidebarOpen = true;

  // ── Topbar dropdowns ──────────────────────────────────────
  showNotificationsPanel = false;
  showProfilePanel = false;

  // ── Notification badge ────────────────────────────────────
  unreadCount = 0;

  // ── User info ─────────────────────────────────────────────
  get userInitials(): string {
    const name = this.authService.getCurrentUser()?.fullName || 'Agent';
    const parts = name.split(' ');
    return parts.length >= 2 ? parts[0][0] + parts[1][0] : name.substring(0, 2);
  }

  get userName(): string {
    return this.authService.getCurrentUser()?.fullName || 'Agent';
  }

  get userRole(): string {
    const roles = this.authService.getCurrentUser()?.roles || [];
    const map: Record<string, string> = {
      ADMIN: 'Administrateur', SUPERVISEUR: 'Superviseur', ATC: 'Contrôleur ATC',
      PISTE: 'Agent Piste', AIS: 'Agent AIS', RH_FORMATION: 'RH / Formation',
      QUALITE: 'Qualité', CONSULTATION: 'Consultation'
    };
    const found = roles.find(r => map[r]);
    return found ? map[found] : 'Agent';
  }

  get isAdmin(): boolean { return this.authService.hasRole(UserRole.ADMIN); }

  private subs = new Subscription();

  constructor(
    private authService: AuthService,
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.subs.add(
      this.notificationService.unreadCount$.subscribe(count => {
        this.unreadCount = count;
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  toggleSidebar(): void {
    this.isSidebarOpen = !this.isSidebarOpen;
  }

  toggleNotificationsPanel(): void {
    this.showNotificationsPanel = !this.showNotificationsPanel;
    this.showProfilePanel = false;
  }

  toggleProfilePanel(): void {
    this.showProfilePanel = !this.showProfilePanel;
    this.showNotificationsPanel = false;
  }

  closeAllPanels(): void {
    this.showNotificationsPanel = false;
    this.showProfilePanel = false;
  }

  logout(): void {
    this.authService.logout();
  }

  navigateTo(path: string): void {
    this.closeAllPanels();
    this.router.navigate([path]);
  }
}
