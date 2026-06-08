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

// Alias pour rétrocompatibilité avec le composant existant
export { RoomType as ChatRoomType };

export interface ChatRoom {
  id: number;
  name: string;
  description?: string;
  roomType: RoomType;
  serviceCode?: string;
  privateRoom: boolean;
  memberCount: number;
  memberNames?: string[];
  createdAt?: string;
  createdByFullName?: string;
  lastMessagePreview?: string;
  lastMessageAt?: string;
  unreadCount: number;
  // UI only
  isOnline?: boolean;
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
  isDeleted?: boolean;
}

export interface ChatMessageRequest {
  roomId: number;
  content: string;
  type?: MessageType;
}

export interface ChatRoomRequest {
  name: string;
  description?: string;
  privateRoom: boolean;
  type?: RoomType;
  memberIds?: number[];
}

export interface TypingIndicator {
  roomId: number;
  userId: number;
  userFullName: string;
  isTyping: boolean;
  timestamp: Date;
}
