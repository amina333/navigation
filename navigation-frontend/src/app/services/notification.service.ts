import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { 
  Notification, 
  NotificationType, 
  NotificationPriority, 
  CreateNotificationRequest,
  NotificationPreference,
  NotificationStats 
} from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly API_URL = `${environment.apiUrl}/notifications`;
  
  private notificationsSubject = new BehaviorSubject<Notification[]>([]);
  notifications$ = this.notificationsSubject.asObservable();
  
  private unreadCountSubject = new BehaviorSubject<number>(0);
  unreadCount$ = this.unreadCountSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadNotifications();
  }

  // Load notifications from backend
  private loadNotifications(): void {
    this.getNotificationsFromAPI().subscribe({
      next: (notifications) => {
        this.notificationsSubject.next(notifications);
        this.updateUnreadCount();
      },
      error: (error) => {
        console.error('Error loading notifications:', error);
      }
    });
  }

  // Get all notifications from API — graceful degradation si l'API n'est pas prête
  private getNotificationsFromAPI(): Observable<Notification[]> {
    return this.http.get<Notification[]>(this.API_URL).pipe(
      catchError(err => {
        console.warn('[NotificationService] API indisponible, fallback []', err);
        return of([]);
      })
    );
  }

  // Get all notifications for current user
  getNotifications(): Observable<Notification[]> {
    return this.notifications$;
  }

  // Get notifications by type
  getNotificationsByType(type: NotificationType): Observable<Notification[]> {
    return this.http.get<Notification[]>(`${this.API_URL}/type/${type}`);
  }

  // Get user notification preferences
  getUserPreferences(userId: number): Observable<NotificationPreference> {
    return this.http.get<NotificationPreference>(`${this.API_URL}/preferences/${userId}`);
  }

  // Update user notification preferences
  updateUserPreferences(userId: number, preferences: NotificationPreference): Observable<NotificationPreference> {
    return this.http.put<NotificationPreference>(`${this.API_URL}/preferences/${userId}`, preferences);
  }

  // Get unread notifications
  getUnreadNotifications(): Observable<Notification[]> {
    return this.http.get<Notification[]>(`${this.API_URL}/unread`);
  }

  // Get notification statistics
  getNotificationStats(): Observable<NotificationStats> {
    return this.http.get<NotificationStats>(`${this.API_URL}/statistics`);
  }

  // Create new notification
  createNotification(request: CreateNotificationRequest): Observable<Notification> {
    return this.http.post<Notification>(this.API_URL, request).pipe(
      // Update local list after successful creation
      // This will be handled by WebSocket real-time updates
    );
  }

  // Mark notification as read
  markAsRead(notificationId: string): Observable<void> {
    return this.http.patch<void>(`${this.API_URL}/${notificationId}/read`, {});
  }

  // Mark multiple notifications as read
  markMultipleAsRead(notificationIds: string[]): Observable<void> {
    return this.http.patch<void>(`${this.API_URL}/mark-read`, { notificationIds });
  }

  // Mark all notifications as read
  markAllAsRead(): Observable<void> {
    return this.http.patch<void>(`${this.API_URL}/mark-all-read`, {});
  }

  // Delete notification
  deleteNotification(notificationId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${notificationId}`);
  }

  // Delete multiple notifications
  deleteMultipleNotifications(notificationIds: string[]): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/delete-multiple`, { notificationIds });
  }

  // Clear all notifications
  clearAll(): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/clear`);
  }

  // Get unread notifications count
  getUnreadNotificationsCount(): number {
    return this.unreadCountSubject.value;
  }

  // Update unread count
  private updateUnreadCount(): void {
    const count = this.notificationsSubject.value.filter(n => !n.read).length;
    this.unreadCountSubject.next(count);
  }

  /**
   * Ajoute une notification LOCAL uniquement (pas d'appel backend).
   * Le backend /api/notifications est un stub — on évite le 500.
   */
  addNotification(
    title: string,
    message: string,
    type: NotificationType = NotificationType.SYSTEM,
    priority: NotificationPriority = NotificationPriority.MEDIUM,
    options?: Partial<CreateNotificationRequest>
  ): void {
    const localNotif: any = {
      id:        `local-${Date.now()}`,
      title,
      message,
      type,
      priority,
      read:      false,
      createdAt: new Date().toISOString(),
      ...options
    };
    const current = this.notificationsSubject.value;
    this.notificationsSubject.next([localNotif, ...current]);
    this.updateUnreadCount();
  }

  // Method to update notifications from WebSocket
  updateNotificationsFromWebSocket(notification: Notification): void {
    const currentNotifications = this.notificationsSubject.value;
    const existingIndex = currentNotifications.findIndex(n => n.id === notification.id);
    
    if (existingIndex >= 0) {
      // Update existing notification
      currentNotifications[existingIndex] = notification;
    } else {
      // Add new notification at the beginning
      currentNotifications.unshift(notification);
    }
    
    this.notificationsSubject.next([...currentNotifications]);
    this.updateUnreadCount();
  }

  // WebSocket connection methods
  connectWebSocket(): void {
    // In a real implementation, this would establish WebSocket connection
    console.log('Connecting to notification WebSocket...');
  }

  onNewNotification(): Observable<Notification> {
    return new Observable(observer => {
      // In a real implementation, this would listen to WebSocket messages
      console.log('Listening for new notifications...');
      return () => {};
    });
  }

  onUnreadCountUpdate(): Observable<number> {
    return this.unreadCount$;
  }

  dismissNotification(notificationId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${notificationId}/dismiss`);
  }

  getUnreadCount(): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/unread-count`);
  }

  showNotification(notification: {
    title: string;
    message: string;
    type: NotificationType;
    relatedEntityId?: number;
  }): void {
    this.addNotification(
      notification.title,
      notification.message,
      notification.type,
      NotificationPriority.MEDIUM,
      { relatedEntityId: notification.relatedEntityId }
    );
  }
}
