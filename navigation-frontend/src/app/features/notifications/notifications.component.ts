import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Observable } from 'rxjs';
import { NotificationService } from '../../services/notification.service';
import { 
  Notification, 
  NotificationType, 
  NotificationPriority, 
  NotificationStats,
  NotificationPreference 
} from '../../models/notification.model';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.scss']
})
export class NotificationsComponent implements OnInit {
  
  notifications$!: Observable<Notification[]>;
  stats$!: Observable<NotificationStats>;
  filteredNotifications: Notification[] = [];
  selectedNotifications: string[] = [];
  
  typeFilter = '';
  priorityFilter = '';
  statusFilter = '';
  activeFilter = 'all';
  selectedType = '';
  
  showPreferences = false;
  preferencesForm!: FormGroup;
  
  unreadCount = 0;
  stats: NotificationStats | null = null;
  preferences: NotificationPreference = {
    userId: 1,
    emailNotifications: true,
    browserNotifications: true,
    mobileNotifications: false,
    notificationTypes: {
      [NotificationType.FLIGHT]: true,
      [NotificationType.SYSTEM]: true,
      [NotificationType.USER]: true,
      [NotificationType.MESSAGE]: true,
      [NotificationType.ALERT]: true,
      [NotificationType.WEATHER]: true,
      [NotificationType.SECURITY]: true,
      [NotificationType.MAINTENANCE]: false
    },
    quietHours: {
      enabled: false,
      start: '22:00',
      end: '08:00',
      timezone: 'Europe/Paris'
    },
    weeklyDigest: true,
    soundEnabled: true
  };

  constructor(
    private notificationService: NotificationService,
    private fb: FormBuilder
  ) {
    this.preferencesForm = this.fb.group({
      emailNotifications: [true],
      browserNotifications: [true],
      mobileNotifications: [false]
    });
  }

  ngOnInit(): void {
    this.loadNotifications();
    this.loadStats();
  }

  loadNotifications(): void {
    this.notifications$ = this.notificationService.getNotifications();
    this.notifications$.subscribe(notifications => {
      this.filteredNotifications = notifications;
      this.unreadCount = notifications.filter(n => !n.read).length;
    });
  }

  loadStats(): void {
    this.stats$ = this.notificationService.getNotificationStats();
    this.stats$.subscribe(stats => {
      this.stats = stats;
    });
  }

  setFilter(filter: string): void {
    this.activeFilter = filter;
    this.filterNotifications();
  }

  filterNotifications(): void {
    this.notifications$.subscribe(notifications => {
      this.filteredNotifications = notifications.filter(notification => {
        if (this.activeFilter === 'unread') {
          return !notification.read;
        }
        if (this.activeFilter === 'urgent') {
          return notification.priority === NotificationPriority.URGENT;
        }
        return true;
      }).filter(notification => {
        if (this.selectedType) {
          return notification.type === this.selectedType;
        }
        return true;
      });
    });
  }

  markAsRead(notificationId: string): void {
    this.notificationService.markAsRead(notificationId);
  }

  markAllAsRead(): void {
    this.notificationService.markAllAsRead();
  }

  deleteNotification(notificationId: string): void {
    this.notificationService.deleteNotification(notificationId);
  }

  clearAll(): void {
    this.notificationService.clearAll();
  }

  toggleNotification(notification: Notification): void {
    if (!notification.read) {
      this.markAsRead(notification.id);
    }
  }

  toggleNotificationSelection(notificationId: string, event: Event): void {
    event.stopPropagation();
    if (this.selectedNotifications.includes(notificationId)) {
      this.selectedNotifications = this.selectedNotifications.filter(id => id !== notificationId);
    } else {
      this.selectedNotifications.push(notificationId);
    }
  }

  isNotificationSelected(notificationId: string): boolean {
    return this.selectedNotifications.includes(notificationId);
  }

  markSelectedAsRead(): void {
    this.notificationService.markMultipleAsRead(this.selectedNotifications);
    this.clearSelection();
  }

  deleteSelected(): void {
    this.notificationService.deleteMultipleNotifications(this.selectedNotifications);
    this.clearSelection();
  }

  clearSelection(): void {
    this.selectedNotifications = [];
  }

  togglePreferences(): void {
    this.showPreferences = !this.showPreferences;
  }

  savePreferences(): void {
    // TODO: Implement updateUserPreferences in NotificationService
    console.log('Preferences saved:', this.preferencesForm.value);
    this.showPreferences = false;
  }

  getPriorityLabel(priority: NotificationPriority): string {
    const labels = {
      [NotificationPriority.LOW]: 'Basse',
      [NotificationPriority.MEDIUM]: 'Moyenne',
      [NotificationPriority.HIGH]: 'Haute',
      [NotificationPriority.URGENT]: 'Urgente'
    };
    return labels[priority] || priority;
  }

  getTypeLabel(type: NotificationType): string {
    const labels = {
      [NotificationType.FLIGHT]: 'Vol',
      [NotificationType.SYSTEM]: 'Système',
      [NotificationType.USER]: 'Utilisateur',
      [NotificationType.MESSAGE]: 'Message',
      [NotificationType.ALERT]: 'Alerte',
      [NotificationType.WEATHER]: 'Météo',
      [NotificationType.SECURITY]: 'Sécurité',
      [NotificationType.MAINTENANCE]: 'Maintenance'
    };
    return labels[type] || type;
  }

  formatTime(timestamp: Date | string): string {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    
    if (diffMins < 1) return 'À l\'instant';
    if (diffMins < 60) return `Il y a ${diffMins} min`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `Il y a ${diffHours}h`;
    const diffDays = Math.floor(diffHours / 24);
    return `Il y a ${diffDays}j`;
  }
}
