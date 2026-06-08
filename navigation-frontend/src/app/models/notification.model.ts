export enum NotificationType {
  FLIGHT = 'FLIGHT',
  SYSTEM = 'SYSTEM',
  USER = 'USER',
  MESSAGE = 'MESSAGE',
  ALERT = 'ALERT',
  WEATHER = 'WEATHER',
  SECURITY = 'SECURITY',
  MAINTENANCE = 'MAINTENANCE'
}

export enum NotificationPriority {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  URGENT = 'URGENT'
}

export interface NotificationAction {
  label: string;
  type: 'primary' | 'secondary' | 'link';
  action?: string;
  url?: string;
  data?: any;
}

export interface Notification {
  id: string;
  title: string;
  message: string;
  type: NotificationType;
  priority: NotificationPriority;
  read: boolean;
  dateEnvoi: Date;
  userId?: number;
  senderId?: number;
  relatedEntityId?: number;
  relatedEntityType?: string;
  actionUrl?: string;
  actions?: NotificationAction[];
  expiresAt?: Date;
  metadata?: Record<string, any>;
  deliveryStatus: {
    email: boolean;
    browser: boolean;
    mobile: boolean;
  };
}

export interface CreateNotificationRequest {
  title: string;
  message: string;
  type: NotificationType;
  priority: NotificationPriority;
  userId?: number;
  senderId?: number;
  relatedEntityId?: number;
  relatedEntityType?: string;
  actionUrl?: string;
  actions?: NotificationAction[];
  expiresAt?: Date;
  metadata?: Record<string, any>;
  deliveryChannels?: {
    email?: boolean;
    browser?: boolean;
    mobile?: boolean;
  };
}

export interface NotificationPreference {
  userId: number;
  emailNotifications: boolean;
  browserNotifications: boolean;
  mobileNotifications: boolean;
  notificationTypes: Record<string, boolean>;
  quietHours?: {
    enabled: boolean;
    start: string; // HH:mm format
    end: string;   // HH:mm format
    timezone: string;
  };
  weeklyDigest: boolean;
  soundEnabled: boolean;
}

export interface NotificationStats {
  total: number;
  unread: number;
  read: number;
  byType: Record<NotificationType, number>;
  byPriority: Record<NotificationPriority, number>;
  recentCount: number; // Last 24 hours
  weeklyCount: number; // Last 7 days
  monthlyCount: number; // Last 30 days
  deliveryRate: number; // Percentage successfully delivered
}

export interface NotificationFilter {
  type?: NotificationType[];
  priority?: NotificationPriority[];
  read?: boolean;
  dateFrom?: Date;
  dateTo?: Date;
  userId?: number;
  senderId?: number;
  relatedEntityType?: string;
}

export interface NotificationSearchResult {
  notifications: Notification[];
  total: number;
  unreadCount: number;
  page: number;
  pageSize: number;
  filters: NotificationFilter;
}

export interface NotificationTemplate {
  id: string;
  name: string;
  type: NotificationType;
  defaultPriority: NotificationPriority;
  titleTemplate: string;
  messageTemplate: string;
  variables: string[];
  actions?: NotificationAction[];
  enabled: boolean;
  createdBy: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface BulkNotificationRequest {
  userIds: number[];
  notification: CreateNotificationRequest;
  scheduleAt?: Date;
  repeatPattern?: {
    type: 'daily' | 'weekly' | 'monthly';
    interval: number;
    endDate?: Date;
  };
}
