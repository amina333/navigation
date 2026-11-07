// ══════════════════════════════════════════════════════════════
// CHAT MODULE §8.7 — Modèles alignés avec le backend
// ══════════════════════════════════════════════════════════════

export enum RoomType {
  PUBLIC          = 'PUBLIC',
  PRIVATE_DM      = 'PRIVATE_DM',
  SERVICE_CHANNEL = 'SERVICE_CHANNEL',
  GROUP           = 'GROUP'
}

export enum MessageType {
  TEXT   = 'TEXT',
  IMAGE  = 'IMAGE',
  FILE   = 'FILE',
  AUDIO  = 'AUDIO',
  SYSTEM = 'SYSTEM'
}

export enum MessageStatus {
  SENDING   = 'SENDING',
  SENT      = 'SENT',
  DELIVERED = 'DELIVERED',
  READ      = 'READ',
  FAILED    = 'FAILED'
}

// Alias pour rétrocompatibilité
export { RoomType as ChatRoomType };

export interface ChatRoom {
  id: number;
  name: string;
  /** Nom de l'interlocuteur pour un DM, libellé du service pour un salon de service */
  displayName: string;
  description?: string;
  roomType: RoomType;
  serviceCode?: string;
  privateRoom: boolean;
  otherUserId?: number;
  otherUserEmail?: string;
  memberCount: number;
  memberNames?: string[];
  memberIds?: number[];
  createdAt?: string;
  createdByFullName?: string;
  createdById?: number;
  canManage?: boolean;
  lastMessagePreview?: string;
  lastMessageAt?: string;
  unreadCount: number;
}

export interface ChatMessage {
  id: string | number;
  content: string;
  messageType: MessageType;
  senderId: number;
  senderFullName: string;
  senderEmail: string;
  roomId: number;
  roomName: string;
  // Pièce jointe
  fileUrl?: string;
  fileName?: string;
  fileSize?: number;
  fileType?: string;
  // Épinglage
  pinned?: boolean;
  pinnedByFullName?: string;
  pinnedAt?: string;
  // État
  read?: boolean;
  deleted?: boolean;
  sentAt: string | Date;
  editedAt?: string;
  // UI only
  status?: MessageStatus;
}

export interface ChatMessageRequest {
  roomId: number;
  content: string;
}

export interface ChatRoomRequest {
  name: string;
  description?: string;
  /** true = groupe privé, false = salon public (admin/superviseur) */
  privateRoom: boolean;
  memberIds?: number[];
}

export interface ChatContact {
  id: number;
  fullName: string;
  email: string;
  serviceNom?: string;
  grade?: string;
  roles: string[];
}

export interface ChatInboxEvent {
  type: 'MESSAGE' | 'ROOM_ADDED';
  roomId: number;
  message?: ChatMessage;
}

export interface TypingEvent {
  roomId: number;
  userId: number;
  userName: string;
  typing: boolean;
}

export interface ReadEvent {
  roomId: number;
  userId: number;
  readAt: string;
}

/** Limites partagées avec le backend */
export const CHAT_LIMITS = {
  MESSAGE_MAX: 4000,
  ROOM_NAME_MIN: 3,
  ROOM_NAME_MAX: 60,
  ROOM_DESC_MAX: 255,
  FILE_MAX_BYTES: 25 * 1024 * 1024,
  FORBIDDEN_EXT: ['exe', 'bat', 'cmd', 'com', 'msi', 'ps1', 'sh', 'vbs', 'js', 'jar', 'scr', 'dll']
};
