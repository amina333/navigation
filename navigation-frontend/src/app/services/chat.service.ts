import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { ChatMessage, ChatMessageRequest, ChatRoom, ChatRoomRequest } from '../models/chat.model';
import { AuthService } from './auth.service';
import { WebSocketService } from './websocket.service';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ChatService {

  private readonly API = `${environment.apiUrl}/chat`;

  private connectedSubject = new BehaviorSubject<boolean>(false);
  connected$ = this.connectedSubject.asObservable();

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private webSocketService: WebSocketService
  ) {
    this.webSocketService.getConnectionStatus().subscribe(c => this.connectedSubject.next(c));
  }

  // ── SALONS ────────────────────────────────────────────────────────
  getPublicRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(`${this.API}/rooms/public`);
  }

  getMyRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(`${this.API}/rooms/my`);
  }

  createRoom(room: ChatRoomRequest): Observable<ChatRoom> {
    return this.http.post<ChatRoom>(`${this.API}/rooms`, room);
  }

  /** Ouvre/crée une conversation privée avec un utilisateur */
  getOrCreateDm(targetUserId: number): Observable<ChatRoom> {
    return this.http.post<ChatRoom>(`${this.API}/dm/${targetUserId}`, {});
  }

  /** Obtient/crée un salon de service (ATC, PISTE, AIS…) */
  getOrCreateServiceChannel(serviceCode: string): Observable<ChatRoom> {
    return this.http.post<ChatRoom>(`${this.API}/service/${serviceCode}`, {});
  }

  // ── MESSAGES ──────────────────────────────────────────────────────
  getRoomHistory(roomId: number): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.API}/rooms/${roomId}/messages`);
  }

  searchMessages(roomId: number, query: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.API}/rooms/${roomId}/search`, { params: { q: query } });
  }

  sendMessageRest(msg: ChatMessageRequest): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.API}/messages`, msg);
  }

  // ── PIÈCES JOINTES (MinIO) ────────────────────────────────────────
  uploadFile(roomId: number, file: File): Observable<ChatMessage> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ChatMessage>(`${this.API}/rooms/${roomId}/upload`, form);
  }

  // ── MESSAGES ÉPINGLÉS ─────────────────────────────────────────────
  getPinnedMessages(roomId: number): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.API}/rooms/${roomId}/pinned`);
  }

  pinMessage(messageId: number | string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.API}/messages/${messageId}/pin`, {});
  }

  unpinMessage(messageId: number | string): Observable<ChatMessage> {
    return this.http.delete<ChatMessage>(`${this.API}/messages/${messageId}/pin`);
  }

  // ── WEBSOCKET ─────────────────────────────────────────────────────
  connect(): void { this.webSocketService.connect(); }
  disconnect(): void { this.webSocketService.disconnect(); }

  subscribeToRoom(roomId: number, cb: (msg: ChatMessage) => void): void {
    this.webSocketService.subscribeToChatRoom(roomId, cb);
  }

  unsubscribeFromRoom(roomId: number): void {
    this.webSocketService.unsubscribeFromChatRoom(roomId);
  }

  sendMessageWs(roomId: number, content: string): void {
    this.webSocketService.sendChatMessage(roomId, content);
  }

  sendTypingIndicator(roomId: number, isTyping: boolean): void {
    this.webSocketService.sendTypingIndicator(roomId, isTyping);
  }
}
