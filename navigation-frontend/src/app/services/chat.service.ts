import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import {
  ChatContact, ChatInboxEvent, ChatMessage, ChatMessageRequest, ChatRoom, ChatRoomRequest,
  ReadEvent, TypingEvent
} from '../models/chat.model';
import { WebSocketService } from './websocket.service';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ChatService {

  private readonly API = `${environment.apiUrl}/chat`;

  private connectedSubject = new BehaviorSubject<boolean>(false);
  connected$ = this.connectedSubject.asObservable();

  /** Total des messages non lus (badge du menu « Communications ») */
  private unreadSubject = new BehaviorSubject<number>(0);
  unreadCount$ = this.unreadSubject.asObservable();

  constructor(
    private http: HttpClient,
    private webSocketService: WebSocketService
  ) {
    this.webSocketService.getConnectionStatus().subscribe(c => this.connectedSubject.next(c));
  }

  // ── SALONS ────────────────────────────────────────────────────────
  /** Toutes les conversations accessibles : publics, services, DM, groupes */
  getMyRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(`${this.API}/rooms/my`).pipe(
      tap(rooms => this.setUnreadTotal(rooms.reduce((s, r) => s + (r.unreadCount || 0), 0)))
    );
  }

  getRoom(roomId: number): Observable<ChatRoom> {
    return this.http.get<ChatRoom>(`${this.API}/rooms/${roomId}`);
  }

  createRoom(room: ChatRoomRequest): Observable<ChatRoom> {
    return this.http.post<ChatRoom>(`${this.API}/rooms`, room);
  }

  addMember(roomId: number, userId: number): Observable<ChatRoom> {
    return this.http.patch<ChatRoom>(`${this.API}/rooms/${roomId}/members/${userId}`, {});
  }

  leaveRoom(roomId: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/rooms/${roomId}/members/me`);
  }

  markAsRead(roomId: number): Observable<void> {
    return this.http.post<void>(`${this.API}/rooms/${roomId}/read`, {});
  }

  refreshUnreadCount(): void {
    this.http.get<{ count: number }>(`${this.API}/unread-count`)
      .subscribe({ next: r => this.setUnreadTotal(r.count), error: () => {} });
  }

  setUnreadTotal(count: number): void {
    this.unreadSubject.next(Math.max(0, count));
  }

  getContacts(): Observable<ChatContact[]> {
    return this.http.get<ChatContact[]>(`${this.API}/contacts`);
  }

  /** Ouvre/crée une conversation privée (one-to-one) avec un utilisateur */
  getOrCreateDm(targetUserId: number): Observable<ChatRoom> {
    return this.http.post<ChatRoom>(`${this.API}/dm/${targetUserId}`, {});
  }

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

  deleteMessage(messageId: number | string): Observable<ChatMessage> {
    return this.http.delete<ChatMessage>(`${this.API}/messages/${messageId}`);
  }

  // ── PIÈCES JOINTES ────────────────────────────────────────────────
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
  /** La connexion est partagée avec toute l'application : on ne la ferme jamais ici */
  connect(): void { this.webSocketService.connect(); }

  subscribeToRoom(roomId: number, cb: (msg: ChatMessage) => void): void {
    this.webSocketService.subscribeToChatRoom(roomId, cb);
  }

  subscribeToTyping(roomId: number, cb: (e: TypingEvent) => void): void {
    this.webSocketService.subscribeToRoomTyping(roomId, cb);
  }

  subscribeToRead(roomId: number, cb: (e: ReadEvent) => void): void {
    this.webSocketService.subscribeToRoomRead(roomId, cb);
  }

  subscribeToInbox(cb: (e: ChatInboxEvent) => void) {
    return this.webSocketService.subscribeToChatInbox(cb);
  }

  subscribeToErrors(cb: (e: { error: string }) => void): void {
    this.webSocketService.subscribeToChatErrors(cb);
  }

  unsubscribeFromRoom(roomId: number): void {
    this.webSocketService.unsubscribeFromChatRoom(roomId);
  }

  unsubscribeFromChannel(destination: string): void {
    this.webSocketService.unsubscribeFromChannel(destination);
  }

  sendMessageWs(roomId: number, content: string): void {
    this.webSocketService.sendChatMessage(roomId, content);
  }

  sendTypingIndicator(roomId: number, isTyping: boolean): void {
    this.webSocketService.sendTypingIndicator(roomId, isTyping);
  }
}
