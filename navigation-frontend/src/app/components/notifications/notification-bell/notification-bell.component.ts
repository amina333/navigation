import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { trigger, transition, animate, style, state } from '@angular/animations';

import { NotificationService } from '../../../services/notification.service';
import { AuthService } from '../../../services/auth.service';
import { Notification, NotificationType, NotificationAction } from '../../../models/notification.model';

import { Subscription } from 'rxjs';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-bell.component.html',
  styleUrls: ['./notification-bell.component.scss'],
  animations: [
    trigger('dropdownAnimation', [
      state('closed', style({
        opacity: 0,
        transform: 'translateY(-10px) scale(0.95)',
        visibility: 'hidden'
      })),
      state('open', style({
        opacity: 1,
        transform: 'translateY(0) scale(1)',
        visibility: 'visible'
      })),
      transition('closed => open', animate('200ms ease-out')),
      transition('open => closed', animate('150ms ease-in'))
    ]),
    trigger('backdropAnimation', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('150ms ease-out', style({ opacity: 1 }))
      ]),
      transition(':leave', [
        animate('150ms ease-in', style({ opacity: 0 }))
      ])
    ])
  ]
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  notifications: Notification[] = [];
  filteredNotifications: Notification[] = [];
  unreadCount = 0;
  hasNewNotifications = false;
  
  showDropdown = false;
  loading = false;
  activeFilter: 'all' | 'unread' | 'flights' | 'system' = 'all';
  
  private subscriptions: Subscription = new Subscription();
  private currentUserId: number = 0;

  constructor(
    private notificationService: NotificationService,
    private authService: AuthService,
    private router: Router
  ) {
    this.currentUserId = this.authService.getCurrentUser()?.id || 0;
  }

  ngOnInit(): void {
    this.loadNotifications();
    this.setupWebSocketListeners();
    this.setupRealtimeUpdates();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as Element;
    const isClickInside = target.closest('.notification-bell-container');
    
    if (!isClickInside && this.showDropdown) {
      this.closeDropdown();
    }
  }

  private loadNotifications(): void {
    this.loading = true;
    
    this.subscriptions.add(
      this.notificationService.getNotifications().subscribe({
        next: (notifications) => {
          this.notifications = notifications;
          this.filteredNotifications = [...notifications];
          this.calculateUnreadCount();
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading notifications:', error);
          this.loading = false;
        }
      })
    );
  }

  private setupWebSocketListeners(): void {
    // Real-time notification updates
    this.subscriptions.add(
      this.notificationService.onNewNotification().subscribe({
        next: (notification) => {
          this.addNewNotification(notification);
        }
      })
    );

    // Real-time unread count updates
    this.subscriptions.add(
      this.notificationService.onUnreadCountUpdate().subscribe({
        next: (count) => {
          this.unreadCount = count;
        }
      })
    );
  }

  private setupRealtimeUpdates(): void {
    // Connect to WebSocket for real-time notifications
    this.notificationService.connectWebSocket();
    
    // Periodic refresh for fallback
    setInterval(() => {
      if (!this.showDropdown) {
        this.refreshUnreadCount();
      }
    }, 30000); // Refresh every 30 seconds
  }

  private addNewNotification(notification: Notification): void {
    this.notifications.unshift(notification);
    this.updateFilteredNotifications();
    this.unreadCount++;
    this.hasNewNotifications = true;
    
    // Show browser notification if permission granted
    this.showBrowserNotification(notification);
    
    // Remove new notification indicator after 3 seconds
    setTimeout(() => {
      this.hasNewNotifications = false;
    }, 3000);
  }

  private showBrowserNotification(notification: Notification): void {
    if ('Notification' in window && Notification.permission === 'granted') {
      const browserNotification = new Notification(notification.title, {
        body: notification.message,
        icon: '/assets/icons/notification-icon.png',
        tag: notification.id.toString()
      });

      browserNotification.onclick = () => {
        window.focus();
        this.handleNotificationClick(notification);
        browserNotification.close();
      };

      // Auto-close after 5 seconds
      setTimeout(() => {
        browserNotification.close();
      }, 5000);
    }
  }

  toggleDropdown(): void {
    this.showDropdown = !this.showDropdown;
    
    if (this.showDropdown) {
      this.updateFilteredNotifications();
      // Mark notifications as read after 2 seconds
      setTimeout(() => {
        if (this.showDropdown) {
          this.markVisibleAsRead();
        }
      }, 2000);
    }
  }

  closeDropdown(): void {
    this.showDropdown = false;
  }

  private updateFilteredNotifications(): void {
    let filtered = [...this.notifications];
    
    switch (this.activeFilter) {
      case 'unread':
        filtered = filtered.filter(n => !n.read);
        break;
      case 'flights':
        filtered = filtered.filter(n => n.type === NotificationType.FLIGHT);
        break;
      case 'system':
        filtered = filtered.filter(n => n.type === NotificationType.SYSTEM);
        break;
    }
    
    this.filteredNotifications = filtered;
  }

  markAllAsRead(): void {
    const unreadNotifications = this.notifications.filter(n => !n.read);
    
    if (unreadNotifications.length === 0) return;
    
    this.subscriptions.add(
      this.notificationService.markAllAsRead().subscribe({
        next: () => {
          this.notifications.forEach(n => n.read = true);
          this.updateFilteredNotifications();
          this.calculateUnreadCount();
        },
        error: (error) => {
          console.error('Error marking all as read:', error);
        }
      })
    );
  }

  private markVisibleAsRead(): void {
    const visibleUnread = this.filteredNotifications.filter(n => !n.read);
    
    if (visibleUnread.length === 0) return;
    
    const notificationIds = visibleUnread.map(n => n.id);
    
    this.subscriptions.add(
      this.notificationService.markMultipleAsRead(notificationIds).subscribe({
        next: () => {
          visibleUnread.forEach(n => n.read = true);
          this.calculateUnreadCount();
        },
        error: (error) => {
          console.error('Error marking notifications as read:', error);
        }
      })
    );
  }

  handleNotificationClick(notification: Notification): void {
    // Mark as read
    if (!notification.read) {
      this.subscriptions.add(
        this.notificationService.markAsRead(notification.id).subscribe({
          next: () => {
            notification.read = true;
            this.calculateUnreadCount();
          }
        })
      );
    }

    // Close dropdown
    this.closeDropdown();

    // Navigate based on notification type
    this.navigateFromNotification(notification);
  }

  private navigateFromNotification(notification: Notification): void {
    switch (notification.type) {
      case NotificationType.FLIGHT:
        if (notification.relatedEntityId) {
          this.router.navigate(['/flights', notification.relatedEntityId]);
        } else {
          this.router.navigate(['/flights']);
        }
        break;
      case NotificationType.USER:
        if (notification.relatedEntityId) {
          this.router.navigate(['/users', notification.relatedEntityId]);
        } else {
          this.router.navigate(['/users']);
        }
        break;
      case NotificationType.MESSAGE:
        if (notification.relatedEntityId) {
          this.router.navigate(['/chat', notification.relatedEntityId]);
        } else {
          this.router.navigate(['/chat']);
        }
        break;
      case NotificationType.SYSTEM:
        if (notification.actionUrl) {
          this.router.navigateByUrl(notification.actionUrl);
        } else {
          this.router.navigate(['/dashboard']);
        }
        break;
      default:
        this.router.navigate(['/dashboard']);
    }
  }

  executeAction(action: NotificationAction, notification: Notification, event: Event): void {
    event.stopPropagation();
    
    switch (action.type) {
      case 'primary':
        this.handleNotificationClick(notification);
        break;
      case 'secondary':
        // Handle secondary action (e.g., dismiss, snooze)
        if (action.action === 'dismiss') {
          this.dismissNotification(notification);
        }
        break;
      case 'link':
        if (action.url) {
          this.router.navigateByUrl(action.url);
          this.closeDropdown();
        }
        break;
    }
  }

  private dismissNotification(notification: Notification): void {
    this.subscriptions.add(
      this.notificationService.dismissNotification(notification.id).subscribe({
        next: () => {
          const index = this.notifications.findIndex(n => n.id === notification.id);
          if (index > -1) {
            this.notifications.splice(index, 1);
            this.updateFilteredNotifications();
            this.calculateUnreadCount();
          }
        }
      })
    );
  }

  openSettings(): void {
    this.closeDropdown();
    this.router.navigate(['/settings/notifications']);
  }

  viewAllNotifications(): void {
    this.closeDropdown();
    this.router.navigate(['/notifications']);
  }

  private calculateUnreadCount(): void {
    this.unreadCount = this.notifications.filter(n => !n.read).length;
  }

  private refreshUnreadCount(): void {
    this.subscriptions.add(
      this.notificationService.getUnreadCount().subscribe({
        next: (count) => {
          this.unreadCount = count;
        }
      })
    );
  }

  getNotificationClass(type: NotificationType): string {
    switch (type) {
      case NotificationType.FLIGHT:
        return 'flight-notification';
      case NotificationType.SYSTEM:
        return 'system-notification';
      case NotificationType.USER:
        return 'user-notification';
      case NotificationType.MESSAGE:
        return 'message-notification';
      case NotificationType.ALERT:
        return 'alert-notification';
      default:
        return 'default-notification';
    }
  }

  formatTime(date: Date | string): string {
    const notificationDate = new Date(date);
    const now = new Date();
    const diffInMinutes = Math.floor((now.getTime() - notificationDate.getTime()) / (1000 * 60));
    
    if (diffInMinutes < 1) {
      return 'À l\'instant';
    } else if (diffInMinutes < 60) {
      return `Il y a ${diffInMinutes} min`;
    } else if (diffInMinutes < 1440) { // Less than 24 hours
      const hours = Math.floor(diffInMinutes / 60);
      return `Il y a ${hours}h`;
    } else if (diffInMinutes < 10080) { // Less than 7 days
      const days = Math.floor(diffInMinutes / 1440);
      return `Il y a ${days}j`;
    } else {
      return notificationDate.toLocaleDateString('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
      });
    }
  }
}
