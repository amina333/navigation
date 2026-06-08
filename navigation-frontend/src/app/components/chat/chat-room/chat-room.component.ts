import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../../services/chat.service';
import { AuthService } from '../../../services/auth.service';
import {
  ChatRoom, ChatMessage, ChatRoomRequest, MessageType, MessageStatus,
  ChatMessageRequest, RoomType
} from '../../../models/chat.model';
import { Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

@Component({
  selector: 'app-chat-room',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-room.component.html',
  styleUrls: ['./chat-room.component.scss']
})
export class ChatRoomComponent implements OnInit, OnDestroy, AfterViewChecked {

  @ViewChild('scrollContainer') private scrollContainer!: ElementRef;
  @ViewChild('fileInput') private fileInput!: ElementRef;

  // ── Données ──────────────────────────────────────────────────────
  rooms: ChatRoom[] = [];
  filteredRooms: ChatRoom[] = [];
  selectedRoom: ChatRoom | null = null;
  messages: ChatMessage[] = [];
  pinnedMessages: ChatMessage[] = [];

  // ── UI State ─────────────────────────────────────────────────────
  newMessage = '';
  isConnected = false;
  loadingMessages = false;
  uploadingFile = false;
  searchQuery = '';
  searchResults: ChatMessage[] = [];
  isSearching = false;

  searchTerm = '';
  activeFilter: 'all' | 'public' | 'dm' | 'service' | 'unread' = 'all';

  showCreateRoomModal = false;
  showPinnedPanel = false;
  showSearchPanel = false;
  showRoomInfo = false;

  typingUsers: string[] = [];
  contextMenuMsg: ChatMessage | null = null;
  contextMenuX = 0;
  contextMenuY = 0;

  // ── Formulaire nouveau salon ──────────────────────────────────────
  newRoom: ChatRoomRequest = { name: '', description: '', privateRoom: false };

  // ── Utilisateur courant ───────────────────────────────────────────
  currentUserId = 0;
  currentUserName = 'Moi';

  readonly RoomType = RoomType;
  readonly MessageType = MessageType;

  private subs = new Subscription();
  private typingTimer: any;
  private lastTypingTime = 0;
  private subscribedRoomId: number | null = null;
  private searchSubject = new Subject<string>();

  constructor(
    private chatService: ChatService,
    private authService: AuthService
  ) {
    const u = this.authService.getCurrentUser();
    this.currentUserId = u?.id || 0;
    this.currentUserName = u?.fullName || 'Moi';
  }

  ngOnInit(): void {
    this.chatService.connect();

    this.subs.add(
      this.chatService.connected$.subscribe(status => {
        this.isConnected = status;
        if (status && this.selectedRoom && this.subscribedRoomId !== this.selectedRoom.id) {
          this.subscribeToCurrentRoom();
        }
      })
    );

    this.subs.add(
      this.searchSubject.pipe(debounceTime(400), distinctUntilChanged()).subscribe(q => {
        this._doSearch(q);
      })
    );

    this.loadRooms();
  }

  ngAfterViewChecked(): void { this.scrollToBottom(); }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    if (this.selectedRoom) this.chatService.unsubscribeFromRoom(this.selectedRoom.id);
    this.chatService.disconnect();
  }

  // ── Chargement ───────────────────────────────────────────────────
  loadRooms(): void {
    // Charger salons publics + mes salons (fusionnés)
    this.chatService.getPublicRooms().subscribe({
      next: publicRooms => {
        this.chatService.getMyRooms().subscribe({
          next: myRooms => {
            const all = [...publicRooms, ...myRooms];
            const unique = all.filter((r, i, a) => a.findIndex(x => x.id === r.id) === i);
            this.rooms = unique;
            this.applyRoomFilter();
            if (this.rooms.length > 0 && !this.selectedRoom) {
              this.selectRoom(this.rooms[0]);
            }
          },
          error: () => {
            this.rooms = publicRooms;
            this.applyRoomFilter();
            if (this.rooms.length > 0 && !this.selectedRoom) this.selectRoom(this.rooms[0]);
          }
        });
      },
      error: () => {
        this.chatService.getMyRooms().subscribe(rooms => {
          this.rooms = rooms;
          this.applyRoomFilter();
          if (this.rooms.length > 0 && !this.selectedRoom) this.selectRoom(this.rooms[0]);
        });
      }
    });
  }

  applyRoomFilter(): void {
    let filtered = [...this.rooms];
    if (this.searchTerm) {
      const t = this.searchTerm.toLowerCase();
      filtered = filtered.filter(r =>
        r.name.toLowerCase().includes(t) || r.description?.toLowerCase().includes(t)
      );
    }
    switch (this.activeFilter) {
      case 'public':   filtered = filtered.filter(r => r.roomType === RoomType.PUBLIC); break;
      case 'dm':       filtered = filtered.filter(r => r.roomType === RoomType.PRIVATE_DM); break;
      case 'service':  filtered = filtered.filter(r => r.roomType === RoomType.SERVICE_CHANNEL); break;
      case 'unread':   filtered = filtered.filter(r => r.unreadCount > 0); break;
    }
    this.filteredRooms = filtered;
  }

  setFilter(f: 'all' | 'public' | 'dm' | 'service' | 'unread'): void {
    this.activeFilter = f;
    this.applyRoomFilter();
  }

  selectRoom(room: ChatRoom): void {
    if (this.selectedRoom?.id === room.id) return;
    if (this.selectedRoom) {
      this.chatService.unsubscribeFromRoom(this.selectedRoom.id);
      this.subscribedRoomId = null;
    }
    this.selectedRoom = room;
    this.messages = [];
    this.pinnedMessages = [];
    this.showPinnedPanel = false;
    this.showSearchPanel = false;
    this.loadingMessages = true;

    this.chatService.getRoomHistory(room.id).subscribe({
      next: h => { this.messages = h; this.loadingMessages = false; this.scrollToBottom(); },
      error: () => this.loadingMessages = false
    });

    this.chatService.getPinnedMessages(room.id).subscribe(p => this.pinnedMessages = p);

    if (this.isConnected) this.subscribeToCurrentRoom();
  }

  subscribeToCurrentRoom(): void {
    if (!this.selectedRoom) return;
    if (this.subscribedRoomId !== null && this.subscribedRoomId !== this.selectedRoom.id) {
      this.chatService.unsubscribeFromRoom(this.subscribedRoomId);
    }
    if (this.subscribedRoomId === this.selectedRoom.id) return;
    this.subscribedRoomId = this.selectedRoom.id;
    this.chatService.subscribeToRoom(this.selectedRoom.id, (msg: ChatMessage) => {
      // Deduplication : eviter d'ajouter deux fois le meme message (upload REST + WS broadcast)
      const alreadyExists = this.messages.some(m => m.id === msg.id);
      if (!alreadyExists) {
        this.messages = [...this.messages, msg];
        this.scrollToBottom();
      }
    });
  }

  // ── Envoi message ─────────────────────────────────────────────────
  sendMessage(): void {
    const content = this.newMessage.trim();
    if (!content || !this.selectedRoom) return;
    this.newMessage = '';

    if (this.isConnected) {
      this.chatService.sendMessageWs(this.selectedRoom.id, content);
    } else {
      const req: ChatMessageRequest = { roomId: this.selectedRoom.id, content, type: MessageType.TEXT };
      const optimistic: ChatMessage = {
        id: `local-${Date.now()}`, content, messageType: MessageType.TEXT,
        senderId: this.currentUserId, senderFullName: this.currentUserName,
        senderEmail: '', roomId: this.selectedRoom.id, roomName: this.selectedRoom.name,
        sentAt: new Date(), status: MessageStatus.SENDING, isDeleted: false
      };
      this.messages = [...this.messages, optimistic];
      this.chatService.sendMessageRest(req).subscribe({
        next: saved => this.messages = this.messages.map(m => m.id === optimistic.id ? saved : m),
        error: () => this.messages = this.messages.map(m =>
          m.id === optimistic.id ? { ...m, status: MessageStatus.FAILED } : m)
      });
    }
  }

  onEnter(event: Event): void {
    const ke = event as KeyboardEvent;
    if (!ke.shiftKey) { event.preventDefault(); this.sendMessage(); }
  }

  // ── Upload fichier ────────────────────────────────────────────────
  triggerFileUpload(): void { this.fileInput?.nativeElement.click(); }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length || !this.selectedRoom) return;
    const file = input.files[0];
    this.uploadingFile = true;

    this.chatService.uploadFile(this.selectedRoom.id, file).subscribe({
      next: msg => {
        // Le message arrive via WebSocket (broadcast serveur)
        // On l'ajoute localement seulement si le WS n'est pas connecte
        if (!this.isConnected) {
          this.messages = [...this.messages, msg];
          this.scrollToBottom();
        }
        this.uploadingFile = false;
      },
      error: () => this.uploadingFile = false
    });
    input.value = '';
  }

  // ── Épinglage ─────────────────────────────────────────────────────
  pinMessage(msg: ChatMessage): void {
    this.chatService.pinMessage(msg.id).subscribe(updated => {
      this.messages = this.messages.map(m => m.id === updated.id ? updated : m);
      this.pinnedMessages = [...this.pinnedMessages, updated];
    });
    this.closeContextMenu();
  }

  unpinMessage(msg: ChatMessage): void {
    this.chatService.unpinMessage(msg.id).subscribe(updated => {
      this.messages = this.messages.map(m => m.id === updated.id ? updated : m);
      this.pinnedMessages = this.pinnedMessages.filter(p => p.id !== updated.id);
    });
    this.closeContextMenu();
  }

  togglePinnedPanel(): void { this.showPinnedPanel = !this.showPinnedPanel; this.showSearchPanel = false; }

  // ── Recherche ─────────────────────────────────────────────────────
  toggleSearchPanel(): void { this.showSearchPanel = !this.showSearchPanel; this.showPinnedPanel = false; }

  onSearchInput(): void { this.searchSubject.next(this.searchQuery); }

  private _doSearch(q: string): void {
    if (!q.trim() || !this.selectedRoom) { this.searchResults = []; return; }
    this.isSearching = true;
    this.chatService.searchMessages(this.selectedRoom.id, q).subscribe({
      next: r => { this.searchResults = r; this.isSearching = false; },
      error: () => this.isSearching = false
    });
  }

  // ── Salon ─────────────────────────────────────────────────────────
  createRoom(): void {
    if (!this.newRoom.name.trim()) return;
    this.chatService.createRoom(this.newRoom).subscribe(r => {
      this.rooms.unshift(r);
      this.applyRoomFilter();
      this.showCreateRoomModal = false;
      this.newRoom = { name: '', description: '', privateRoom: false };
      this.selectRoom(r);
    });
  }

  // ── Context menu ──────────────────────────────────────────────────
  openContextMenu(event: MouseEvent, msg: ChatMessage): void {
    event.preventDefault();
    this.contextMenuMsg = msg;
    this.contextMenuX = event.clientX;
    this.contextMenuY = event.clientY;
  }

  closeContextMenu(): void { this.contextMenuMsg = null; }

  // ── Typing ───────────────────────────────────────────────────────
  onTyping(): void {
    if (!this.selectedRoom || !this.isConnected) return;
    const now = Date.now();
    if (now - this.lastTypingTime > 1000) {
      this.chatService.sendTypingIndicator(this.selectedRoom.id, true);
      this.lastTypingTime = now;
    }
    clearTimeout(this.typingTimer);
    this.typingTimer = setTimeout(() => this.chatService.sendTypingIndicator(this.selectedRoom!.id, false), 2000);
  }

  // ── Helpers ──────────────────────────────────────────────────────
  isMyMessage(msg: ChatMessage): boolean { return msg.senderId === this.currentUserId; }

  getInitials(name: string): string {
    return name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
  }

  getRoomIcon(room: ChatRoom): string {
    switch (room.roomType) {
      case RoomType.PRIVATE_DM:      return '💬';
      case RoomType.SERVICE_CHANNEL: return '#';
      case RoomType.GROUP:           return '👥';
      default:                       return '#';
    }
  }

  formatTime(d: string | Date): string {
    const date = new Date(d);
    const now = new Date();
    const diff = Math.floor((now.getTime() - date.getTime()) / 60000);
    if (diff < 1)    return 'À l\'instant';
    if (diff < 60)   return `${diff}min`;
    if (diff < 1440) return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    return date.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
  }

  formatFileSize(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024)       return `${bytes} B`;
    if (bytes < 1048576)    return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1048576).toFixed(1)} MB`;
  }

  openUrl(url: string): void {
    window.open(url, '_blank');
  }

  isImage(msg: ChatMessage): boolean {
    return msg.messageType === MessageType.IMAGE || (msg.fileType?.startsWith('image/') ?? false);
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      try { this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight; } catch {}
    }, 80);
  }
}
