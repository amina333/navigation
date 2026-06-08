import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

export interface WebSocketMessage {
  type: string;
  data: any;
  timestamp: Date;
  sender?: string;
  roomId?: number;
}

export interface NotificationMessage {
  id: string;
  title: string;
  message: string;
  type: 'info' | 'success' | 'warning' | 'error';
  timestamp: Date;
  read: boolean;
}

export interface FlightUpdateMessage {
  flightId: number;
  flightNumber: string;
  status: string;
  update: string;
  timestamp: Date;
}

export interface TypingIndicator {
  userId: number;
  userName: string;
  roomId: number;
  isTyping: boolean;
  timestamp: Date;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  
  private readonly WS_URL = `${environment.apiUrl.replace('/api', '')}/ws`;
  
  private stompClient: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();
  
  // Subjects for different message types
  private notificationsSubject = new BehaviorSubject<NotificationMessage[]>([]);
  private flightUpdatesSubject = new Subject<FlightUpdateMessage>();
  private typingIndicatorsSubject = new Subject<TypingIndicator>();
  private connectionStatusSubject = new BehaviorSubject<boolean>(false);
  private messagesSubject = new Subject<WebSocketMessage>();
  
  // Public observables
  public notifications$ = this.notificationsSubject.asObservable();
  public flightUpdates$ = this.flightUpdatesSubject.asObservable();
  public typingIndicators$ = this.typingIndicatorsSubject.asObservable();
  public connectionStatus$ = this.connectionStatusSubject.asObservable();
  public messages$ = this.messagesSubject.asObservable();
  
  // Notification storage
  private notifications: NotificationMessage[] = [];
  
  constructor(private authService: AuthService) {}
  
  /**
   * Connect to WebSocket server
   */
  connect(): void {
    if (this.stompClient?.connected) {
      console.log('WebSocket already connected');
      return;
    }
    
    const token = this.authService.getToken();
    if (!token) {
      console.warn('No authentication token found');
      return;
    }
    
    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(this.WS_URL) as any,
      connectHeaders: { 
        Authorization: `Bearer ${token}`,
        'X-User-Id': this.authService.getCurrentUser()?.id?.toString() || '0'
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('✅ WebSocket connected successfully');
        this.connectionStatusSubject.next(true);
        this.subscribeToDefaultChannels();
      },
      onDisconnect: () => {
        console.log('❌ WebSocket disconnected');
        this.connectionStatusSubject.next(false);
      },
      onStompError: (frame) => {
        console.error('WebSocket STOMP error:', frame);
        this.connectionStatusSubject.next(false);
      }
    });
    
    this.stompClient.activate();
  }
  
  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions.clear();
    
    if (this.stompClient?.connected) {
      this.stompClient.deactivate();
    }
    
    this.connectionStatusSubject.next(false);
    console.log('WebSocket disconnected');
  }
  
  /**
   * Subscribe to default channels based on user role
   */
  private subscribeToDefaultChannels(): void {
    const user = this.authService.getCurrentUser();
    if (!user) return;
    
    // Subscribe to user-specific notifications
    this.subscribeToChannel(`/user/${user.id}/notifications`, (message) => {
      const notification: NotificationMessage = JSON.parse(message.body);
      this.addNotification(notification);
    });
    
    // Subscribe to global flight updates
    this.subscribeToChannel('/topic/flights/updates', (message) => {
      const flightUpdate: FlightUpdateMessage = JSON.parse(message.body);
      this.flightUpdatesSubject.next(flightUpdate);
    });
    
    // Subscribe to role-specific channels
    user.roles.forEach(role => {
      switch (role) {
        case 'ADMIN':
          this.subscribeToChannel('/topic/admin/updates', (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body);
            this.messagesSubject.next(wsMessage);
          });
          break;
        case 'ATC':
          this.subscribeToChannel('/topic/atc/updates', (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body);
            this.messagesSubject.next(wsMessage);
          });
          break;
        case 'PISTE':
          this.subscribeToChannel('/topic/ground/updates', (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body);
            this.messagesSubject.next(wsMessage);
          });
          break;
        case 'SUPERVISEUR':
          this.subscribeToChannel('/topic/supervisor/updates', (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body);
            this.messagesSubject.next(wsMessage);
          });
          break;
        case 'AIS':
          this.subscribeToChannel('/topic/ais/updates', (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body);
            this.messagesSubject.next(wsMessage);
          });
          break;
        case 'RH_FORMATION':
          this.subscribeToChannel('/topic/hr/updates', (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body);
            this.messagesSubject.next(wsMessage);
          });
          break;
        case 'QUALITE':
          this.subscribeToChannel('/topic/quality/updates', (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body);
            this.messagesSubject.next(wsMessage);
          });
          break;
        case 'CONSULTATION':
          this.subscribeToChannel('/topic/consultant/updates', (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body);
            this.messagesSubject.next(wsMessage);
          });
          break;
      }
    });
  }
  
  /**
   * Subscribe to a specific channel
   */
  subscribeToChannel(destination: string, callback: (message: IMessage) => void): void {
    if (!this.stompClient?.connected) {
      console.warn('Cannot subscribe: WebSocket not connected');
      return;
    }
    
    // Unsubscribe if already subscribed
    if (this.subscriptions.has(destination)) {
      this.subscriptions.get(destination)?.unsubscribe();
    }
    
    const subscription = this.stompClient.subscribe(destination, callback);
    this.subscriptions.set(destination, subscription);
    console.log(`Subscribed to: ${destination}`);
  }
  
  /**
   * Unsubscribe from a specific channel
   */
  unsubscribeFromChannel(destination: string): void {
    const subscription = this.subscriptions.get(destination);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(destination);
      console.log(`Unsubscribed from: ${destination}`);
    }
  }
  
  /**
   * Send a message to a specific destination
   */
  sendMessage(destination: string, body: any): void {
    if (!this.stompClient?.connected) {
      console.warn('Cannot send message: WebSocket not connected');
      return;
    }
    
    this.stompClient.publish({
      destination,
      body: JSON.stringify(body)
    });
  }
  
  /**
   * Send a notification to specific users
   */
  sendNotification(userIds: number[], notification: Partial<NotificationMessage>): void {
    this.sendMessage('/app/notifications/send', {
      userIds,
      notification: {
        ...notification,
        id: this.generateId(),
        timestamp: new Date(),
        read: false
      }
    });
  }
  
  /**
   * Send flight update
   */
  sendFlightUpdate(flightId: number, update: string, status: string): void {
    const user = this.authService.getCurrentUser();
    this.sendMessage('/app/flights/update', {
      flightId,
      update,
      status,
      updatedBy: user?.fullName,
      timestamp: new Date()
    });
  }
  
  /**
   * Send typing indicator for chat
   */
  sendTypingIndicator(roomId: number, isTyping: boolean): void {
    const user = this.authService.getCurrentUser();
    if (!user) return;
    
    this.sendMessage('/app/chat/typing', {
      roomId,
      userId: user.id,
      userName: user.fullName,
      isTyping,
      timestamp: new Date()
    });
  }
  
  /**
   * Send chat message
   */
  sendChatMessage(roomId: number, content: string): void {
    const user = this.authService.getCurrentUser();
    if (!user) return;

    // Destination doit correspondre à @MessageMapping("/chat.send") → /app/chat.send
    this.sendMessage('/app/chat.send', {
      roomId,
      content,
      senderId: user.id
    });
  }
  
  /**
   * Subscribe to chat room
   */
  subscribeToChatRoom(roomId: number, callback: (message: any) => void): void {
    this.subscribeToChannel(`/topic/chat/${roomId}`, (message) => {
      const chatMessage = JSON.parse(message.body);
      callback(chatMessage);
    });
  }
  
  /**
   * Unsubscribe from chat room
   */
  unsubscribeFromChatRoom(roomId: number): void {
    this.unsubscribeFromChannel(`/topic/chat/${roomId}`);
  }
  
  /**
   * Subscribe to typing indicators for a room
   */
  subscribeToTypingIndicators(roomId: number): void {
    this.subscribeToChannel(`/topic/chat/${roomId}/typing`, (message) => {
      const typingIndicator: TypingIndicator = JSON.parse(message.body);
      this.typingIndicatorsSubject.next(typingIndicator);
    });
  }
  
  /**
   * Add notification to the list
   */
  private addNotification(notification: NotificationMessage): void {
    this.notifications.unshift(notification);
    
    // Keep only last 50 notifications
    if (this.notifications.length > 50) {
      this.notifications = this.notifications.slice(0, 50);
    }
    
    this.notificationsSubject.next([...this.notifications]);
    
    // Show browser notification if permitted
    this.showBrowserNotification(notification);
  }
  
  /**
   * Show browser notification
   */
  private showBrowserNotification(notification: NotificationMessage): void {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification(notification.title, {
        body: notification.message,
        icon: '/assets/icons/notification-icon.png',
        tag: notification.id
      });
    }
  }
  
  /**
   * Mark notification as read
   */
  markNotificationAsRead(notificationId: string): void {
    const notification = this.notifications.find(n => n.id === notificationId);
    if (notification) {
      notification.read = true;
      this.notificationsSubject.next([...this.notifications]);
    }
  }
  
  /**
   * Clear all notifications
   */
  clearNotifications(): void {
    this.notifications = [];
    this.notificationsSubject.next([]);
  }
  
  /**
   * Get unread notifications count
   */
  getUnreadNotificationsCount(): number {
    return this.notifications.filter(n => !n.read).length;
  }
  
  /**
   * Check if WebSocket is connected
   */
  isConnected(): boolean {
    return this.stompClient?.connected || false;
  }
  
  /**
   * Get connection status
   */
  getConnectionStatus(): Observable<boolean> {
    return this.connectionStatus$;
  }
  
  /**
   * Generate unique ID
   */
  private generateId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
  }
  
  /**
   * Request browser notification permission
   */
  requestNotificationPermission(): void {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }
}
