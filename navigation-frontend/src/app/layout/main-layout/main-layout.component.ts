import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { AIAssistantComponent } from '../../components/chatbot/ai-assistant/ai-assistant.component';
import { UserRole } from '../../models/user.model';
import { Subscription, interval } from 'rxjs';
import { ChatService } from '../../services/chat.service';
import { PreferencesService } from '../../core/preferences/preferences.service';
import { AlertsService } from '../../core/preferences/alerts.service';
import { TranslatePipe } from '../../core/preferences/pipes';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet, AIAssistantComponent, TranslatePipe],
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
    const map: Record<string, string> = this.prefs.lang === 'en'
      ? { ADMIN: 'Administrator', SUPERVISEUR: 'Supervisor', ATC: 'ATC controller', PISTE: 'Ramp agent',
          AIS: 'AIS agent', RH_FORMATION: 'HR / Training', QUALITE: 'Quality', CONSULTATION: 'Read-only' }
      : { ADMIN: 'Administrateur', SUPERVISEUR: 'Superviseur', ATC: 'Contrôleur ATC', PISTE: 'Agent Piste',
          AIS: 'Agent AIS', RH_FORMATION: 'RH / Formation', QUALITE: 'Qualité', CONSULTATION: 'Consultation' };
    const found = roles.find(r => map[r]);
    return found ? map[found] : 'Agent';
  }

  get isAdmin(): boolean { return this.authService.hasRole(UserRole.ADMIN); }

  private subs = new Subscription();

  /** Messages de chat non lus (badge du menu Communications) */
  chatUnread = 0;

  constructor(
    private authService: AuthService,
    private notificationService: NotificationService,
    private chatService: ChatService,
    private router: Router,
    public prefs: PreferencesService,
    private alerts: AlertsService
  ) {}

  /** Horloge de la barre supérieure (fuseau choisi + UTC) */
  now = new Date();
  private clockTimer: any;

  get localTime(): string { return this.prefs.formatDate(this.now, 'time'); }
  get utcTime(): string {
    return new Intl.DateTimeFormat('fr-FR', { timeZone: 'UTC', hour: '2-digit', minute: '2-digit' }).format(this.now);
  }
  get tzShort(): string { return this.prefs.timezone === 'UTC' ? 'UTC' : this.prefs.timezone.split('/').pop()!.replace('_', ' '); }

  ngOnInit(): void {
    this.subs.add(
      this.notificationService.unreadCount$.subscribe(count => {
        this.unreadCount = count;
      })
    );

    // Préférences : langue, fuseau, notifications, session
    this.prefs.load().subscribe({ next: () => this.alerts.resetIdle(), error: () => {} });
    this.alerts.startIdleWatch(() => this.logout());
    this.clockTimer = setInterval(() => this.now = new Date(), 15000);

    // Badge « Communications » = messages de chat non lus
    this.subs.add(this.chatService.unreadCount$.subscribe(c => this.chatUnread = c));
    this.chatService.refreshUnreadCount();
    this.subs.add(interval(30000).subscribe(() => {
      if (!this.router.url.startsWith('/dashboard/chat')) this.chatService.refreshUnreadCount();
    }));

    // Notifications globales : nouveau message privé / de groupe reçu
    this.chatService.connect();
    this.subs.add(this.chatService.subscribeToInbox(e => this.onChatEvent(e)));
  }

  private onChatEvent(e: { type: string; roomId: number; message?: any }): void {
    if (e.type !== 'MESSAGE' || !e.message) return;
    const myId = this.authService.getCurrentUser()?.id;
    if (e.message.senderId === myId) return;
    const onChat = this.router.url.startsWith('/dashboard/chat');

    // Son : partout sauf si la conversation est déjà visible
    if (!onChat || document.hidden) this.alerts.playSound();

    // Notification système : onglet en arrière-plan ou autre page
    if (!onChat || document.hidden) {
      const body = e.message.fileName ? '📎 ' + e.message.fileName : (e.message.content || '');
      this.alerts.notify(e.message.senderFullName, body.length > 140 ? body.slice(0, 140) + '…' : body,
        () => this.router.navigate(['/dashboard/chat']));
    }
    if (!onChat) this.chatService.refreshUnreadCount();
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    clearInterval(this.clockTimer);
    this.alerts.stopIdleWatch();
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
    this.prefs.reset();
    this.authService.logout();
  }

  navigateTo(path: string): void {
    this.closeAllPanels();
    this.router.navigate([path]);
  }
}
