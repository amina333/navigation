import { Component, OnInit, OnDestroy, ViewChild, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { ChatService } from '../../../services/chat.service';
import { AuthService } from '../../../services/auth.service';
import {
  ChatRoom, ChatMessage, MessageType, MessageStatus, RoomType, ChatContact,
  ChatInboxEvent, TypingEvent, ReadEvent, CHAT_LIMITS
} from '../../../models/chat.model';

import { PreferencesService } from '../../../core/preferences/preferences.service';
import { AppDatePipe } from '../../../core/preferences/pipes';

type RoomFilter ='all' | 'dm' | 'service' | 'group' | 'public' | 'unread';
type SidePanel = 'none' | 'info' | 'pinned' | 'search';

interface MessageGroup {
  day: string;
  items: ChatMessage[];
}

/** Au moins un membre sélectionné pour un groupe privé */
function membersRequiredForGroup(group: AbstractControl): ValidationErrors | null {
  const isPrivate = group.get('privateRoom')?.value;
  const members: number[] = group.get('memberIds')?.value || [];
  return isPrivate && members.length === 0 ? { membersRequired: true } : null;
}

@Component({
  selector: 'app-chat-room',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, AppDatePipe],
  templateUrl: './chat-room.component.html',
  styleUrls: ['./chat-room.component.scss']
})
export class ChatRoomComponent implements OnInit, OnDestroy {

  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;
  @ViewChild('composer') private composer?: ElementRef<HTMLTextAreaElement>;

  readonly RoomType = RoomType;
  readonly MessageType = MessageType;
  readonly MessageStatus = MessageStatus;
  readonly LIMITS = CHAT_LIMITS;

  // ── Données ──────────────────────────────────────────────────────
  rooms: ChatRoom[] = [];
  filteredRooms: ChatRoom[] = [];
  selectedRoom: ChatRoom | null = null;
  messages: ChatMessage[] = [];
  groupedMessages: MessageGroup[] = [];
  pinnedMessages: ChatMessage[] = [];
  contacts: ChatContact[] = [];

  // ── État UI ──────────────────────────────────────────────────────
  isConnected = false;
  loadingRooms = true;
  loadingMessages = false;
  uploadingFile = false;
  roomsError = '';
  composerError = '';
  toast = '';

  searchTerm = '';
  activeFilter: RoomFilter = 'all';
  sidePanel: SidePanel = 'none';
  mobileShowThread = false;

  newMessage = '';
  searchQuery = '';
  searchResults: ChatMessage[] = [];
  isSearching = false;

  typingUsers = new Map<number, { name: string; timer: any }>();

  // Modales
  showDmModal = false;
  showGroupModal = false;
  showAddMemberModal = false;
  contactSearch = '';
  creatingDm = false;
  groupForm: FormGroup;
  groupSubmitting = false;
  groupServerError = '';

  // ── Utilisateur courant ───────────────────────────────────────────
  currentUserId = 0;
  currentUserName = '';
  canCreatePublic = false;

  readonly filters: { key: RoomFilter; label: string }[] = [
    { key: 'all', label: 'Tous' },
    { key: 'dm', label: 'Privés' },
    { key: 'service', label: 'Services' },
    { key: 'group', label: 'Groupes' },
    { key: 'public', label: 'Publics' },
    { key: 'unread', label: 'Non lus' }
  ];

  private subs = new Subscription();
  private subscribedRooms = new Set<number>();
  private seenIds = new Set<string | number>();
  private searchSubject = new Subject<string>();
  private typingTimer: any;
  private lastTypingSent = 0;
  private toastTimer: any;
  private inboxSubscribed = false;

  constructor(
    private chatService: ChatService,
    private authService: AuthService,
    private fb: FormBuilder,
    private prefs: PreferencesService
  ) {
    const u = this.authService.getCurrentUser();
    this.currentUserId = u?.id || 0;
    this.currentUserName = u?.fullName || 'Moi';
    const roles: string[] = (u as any)?.roles || [];
    this.canCreatePublic = roles.includes('ADMIN') || roles.includes('SUPERVISEUR');

    this.groupForm = this.fb.group({
      name: ['', [
        Validators.required,
        Validators.minLength(CHAT_LIMITS.ROOM_NAME_MIN),
        Validators.maxLength(CHAT_LIMITS.ROOM_NAME_MAX),
        Validators.pattern(/^[\p{L}0-9 _#'.\-]+$/u)
      ]],
      description: ['', [Validators.maxLength(CHAT_LIMITS.ROOM_DESC_MAX)]],
      privateRoom: [true],
      memberIds: [[] as number[]]
    }, { validators: membersRequiredForGroup });
  }

  // ══════════════════════════════════════════════════════
  // CYCLE DE VIE
  // ══════════════════════════════════════════════════════

  ngOnInit(): void {
    this.chatService.connect();

    this.subs.add(this.chatService.connected$.subscribe(connected => {
      this.isConnected = connected;
      if (connected) {
        // Après une (re)connexion, toutes les souscriptions STOMP sont perdues
        this.subscribedRooms.clear();
        this.inboxSubscribed = false;
        this.subscribeRealtime();
      }
    }));

    this.subs.add(this.searchSubject.pipe(debounceTime(350), distinctUntilChanged())
      .subscribe(q => this.doSearch(q)));

    // Boîte personnelle (flux partagé avec les notifications globales)
    this.subs.add(this.chatService.subscribeToInbox(e => this.onInboxEvent(e)));

    this.loadRooms();
    this.chatService.getContacts().subscribe({ next: c => this.contacts = c, error: () => {} });
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.subscribedRooms.forEach(id => this.chatService.unsubscribeFromRoom(id));
    this.chatService.unsubscribeFromChannel('/user/queue/errors');
    clearTimeout(this.typingTimer);
    clearTimeout(this.toastTimer);
    this.typingUsers.forEach(t => clearTimeout(t.timer));
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.showDmModal = false;
    this.showGroupModal = false;
    this.showAddMemberModal = false;
  }

  // ══════════════════════════════════════════════════════
  // SALONS
  // ══════════════════════════════════════════════════════

  loadRooms(selectId?: number): void {
    this.roomsError = '';
    this.chatService.getMyRooms().subscribe({
      next: rooms => {
        this.rooms = rooms;
        this.loadingRooms = false;
        this.applyRoomFilter();
        this.subscribeRealtime();

        const keepId = this.selectedRoom?.id;
        const wanted = selectId ? rooms.find(r => r.id === selectId) : undefined;
        if (wanted) {
          this.selectRoom(wanted);
        } else if (keepId) {
          // Rafraîchit les infos du salon ouvert sans recharger ses messages
          const current = rooms.find(r => r.id === keepId);
          if (current) {
            current.unreadCount = 0;
            this.selectedRoom = current;
            this.updateUnreadTotal();
          }
        } else if (rooms.length && window.innerWidth > 900) {
          this.selectRoom(rooms[0]);
        }
      },
      error: err => {
        this.loadingRooms = false;
        this.roomsError = err?.error?.message || 'Impossible de charger les conversations.';
      }
    });
  }

  applyRoomFilter(): void {
    const t = this.normalize(this.searchTerm);
    this.filteredRooms = this.rooms.filter(r => {
      if (t && !this.normalize(`${r.displayName} ${r.description || ''}`).includes(t)) return false;
      switch (this.activeFilter) {
        case 'dm':      return r.roomType === RoomType.PRIVATE_DM;
        case 'service': return r.roomType === RoomType.SERVICE_CHANNEL;
        case 'group':   return r.roomType === RoomType.GROUP;
        case 'public':  return r.roomType === RoomType.PUBLIC;
        case 'unread':  return r.unreadCount > 0;
        default:        return true;
      }
    });
  }

  setFilter(f: RoomFilter): void {
    this.activeFilter = f;
    this.applyRoomFilter();
  }

  filterCount(f: RoomFilter): number {
    if (f === 'unread') return this.rooms.filter(r => r.unreadCount > 0).length;
    return 0;
  }

  selectRoom(room: ChatRoom): void {
    this.mobileShowThread = true;
    if (this.selectedRoom?.id === room.id && this.messages.length) return;

    this.selectedRoom = room;
    this.messages = [];
    this.groupedMessages = [];
    this.pinnedMessages = [];
    this.searchResults = [];
    this.searchQuery = '';
    this.composerError = '';
    this.typingUsers.forEach(t => clearTimeout(t.timer));
    this.typingUsers.clear();
    if (this.sidePanel === 'search') this.sidePanel = 'none';
    this.loadingMessages = true;

    this.chatService.getRoomHistory(room.id).subscribe({
      next: history => {
        if (this.selectedRoom?.id !== room.id) return;
        this.messages = history;
        history.forEach(m => this.seenIds.add(m.id));
        this.rebuildGroups();
        this.loadingMessages = false;
        this.scrollToBottom(true);
        this.markSelectedAsRead();
      },
      error: err => {
        this.loadingMessages = false;
        this.showToast(err?.error?.message || 'Impossible de charger les messages');
      }
    });

    this.chatService.getPinnedMessages(room.id).subscribe({ next: p => this.pinnedMessages = p, error: () => {} });
    this.subscribeRoomExtras(room.id);
    setTimeout(() => this.composer?.nativeElement.focus(), 50);
  }

  backToList(): void {
    this.mobileShowThread = false;
  }

  // ══════════════════════════════════════════════════════
  // TEMPS RÉEL
  // ══════════════════════════════════════════════════════

  private subscribeRealtime(): void {
    if (!this.isConnected) return;

    if (!this.inboxSubscribed) {
      this.inboxSubscribed = true;
      this.chatService.subscribeToErrors(e => this.showToast(e.error));
    }

    // Une souscription par conversation pour mettre à jour aperçus et non-lus en direct
    this.rooms.forEach(r => {
      if (this.subscribedRooms.has(r.id)) return;
      this.subscribedRooms.add(r.id);
      this.chatService.subscribeToRoom(r.id, msg => this.onIncomingMessage(msg));
    });

    if (this.selectedRoom) this.subscribeRoomExtras(this.selectedRoom.id);
  }

  private subscribeRoomExtras(roomId: number): void {
    if (!this.isConnected) return;
    this.chatService.subscribeToTyping(roomId, e => this.onTyping(e));
    this.chatService.subscribeToRead(roomId, e => this.onRead(e));
  }

  private onInboxEvent(e: ChatInboxEvent): void {
    // Nouveau DM ou ajout à un groupe : la conversation n'est pas encore dans la liste
    if (!this.rooms.some(r => r.id === e.roomId)) {
      this.loadRooms();
      if (e.message) this.showToast(`Nouveau message de ${e.message.senderFullName}`);
    }
  }

  private onIncomingMessage(msg: ChatMessage): void {
    const room = this.rooms.find(r => r.id === msg.roomId);
    if (!room) { this.loadRooms(); return; }

    const isMine = msg.senderId === this.currentUserId;
    const isNew = !this.seenIds.has(msg.id) && !msg.deleted && !msg.pinned;
    this.seenIds.add(msg.id);

    // Aperçu de la conversation
    if (isNew) {
      room.lastMessagePreview = msg.messageType === MessageType.TEXT || msg.messageType === MessageType.SYSTEM
        ? this.truncate(msg.content, 80) : `📎 ${msg.fileName}`;
      room.lastMessageAt = this.toIso(msg.sentAt);
    }

    if (this.selectedRoom?.id === msg.roomId) {
      this.upsertMessage(msg);
      if (msg.pinned) {
        if (!this.pinnedMessages.some(p => p.id === msg.id)) this.pinnedMessages = [msg, ...this.pinnedMessages];
      } else {
        this.pinnedMessages = this.pinnedMessages.filter(p => p.id !== msg.id);
      }
      if (isNew && !isMine) {
        this.typingUsers.delete(msg.senderId);
        this.markSelectedAsRead();
      }
      if (isNew) this.scrollToBottom(isMine);
    } else if (isNew && !isMine && msg.messageType !== MessageType.SYSTEM) {
      room.unreadCount = (room.unreadCount || 0) + 1;
    }

    this.sortRooms();
    this.updateUnreadTotal();
  }

  private onTyping(e: TypingEvent): void {
    if (e.userId === this.currentUserId || e.roomId !== this.selectedRoom?.id) return;
    const existing = this.typingUsers.get(e.userId);
    if (existing) clearTimeout(existing.timer);
    if (e.typing) {
      const timer = setTimeout(() => this.typingUsers.delete(e.userId), 4000);
      this.typingUsers.set(e.userId, { name: e.userName, timer });
    } else {
      this.typingUsers.delete(e.userId);
    }
  }

  private onRead(e: ReadEvent): void {
    if (e.userId === this.currentUserId || e.roomId !== this.selectedRoom?.id) return;
    this.messages = this.messages.map(m => m.senderId === this.currentUserId ? { ...m, read: true } : m);
    this.rebuildGroups();
  }

  get typingLabel(): string {
    const names = Array.from(this.typingUsers.values()).map(t => t.name.split(' ')[0]);
    if (!names.length) return '';
    if (names.length === 1) return `${names[0]} est en train d'écrire…`;
    return `${names.slice(0, 2).join(' et ')} écrivent…`;
  }

  private markSelectedAsRead(): void {
    const room = this.selectedRoom;
    if (!room) return;
    const listRoom = this.rooms.find(r => r.id === room.id);
    if (listRoom) listRoom.unreadCount = 0;
    room.unreadCount = 0;
    this.updateUnreadTotal();
    this.chatService.markAsRead(room.id).subscribe({ error: () => {} });
  }

  private updateUnreadTotal(): void {
    this.chatService.setUnreadTotal(this.rooms.reduce((s, r) => s + (r.unreadCount || 0), 0));
    this.applyRoomFilter();
  }

  // ══════════════════════════════════════════════════════
  // ENVOI
  // ══════════════════════════════════════════════════════

  get remainingChars(): number {
    return CHAT_LIMITS.MESSAGE_MAX - this.newMessage.length;
  }

  get canSend(): boolean {
    const t = this.newMessage.trim();
    return !!this.selectedRoom && t.length > 0 && t.length <= CHAT_LIMITS.MESSAGE_MAX;
  }

  sendMessage(): void {
    const content = this.newMessage.trim();
    this.composerError = '';
    if (!this.selectedRoom) return;
    if (!content) { this.composerError = 'Le message ne peut pas être vide.'; return; }
    if (content.length > CHAT_LIMITS.MESSAGE_MAX) {
      this.composerError = `Le message ne doit pas dépasser ${CHAT_LIMITS.MESSAGE_MAX} caractères.`;
      return;
    }

    const room = this.selectedRoom;
    const tempId = `local-${Date.now()}`;
    const optimistic: ChatMessage = {
      id: tempId, content, messageType: MessageType.TEXT,
      senderId: this.currentUserId, senderFullName: this.currentUserName, senderEmail: '',
      roomId: room.id, roomName: room.name, sentAt: new Date(), status: MessageStatus.SENDING
    };
    this.messages = [...this.messages, optimistic];
    this.rebuildGroups();
    this.newMessage = '';
    this.resizeComposer();
    this.scrollToBottom(true);
    this.stopTyping();

    this.chatService.sendMessageRest({ roomId: room.id, content }).subscribe({
      next: saved => {
        this.seenIds.add(saved.id);
        const alreadyThere = this.messages.some(m => m.id === saved.id);
        this.messages = alreadyThere
          ? this.messages.filter(m => m.id !== tempId)
          : this.messages.map(m => m.id === tempId ? saved : m);
        this.rebuildGroups();
        const listRoom = this.rooms.find(r => r.id === room.id);
        if (listRoom) {
          listRoom.lastMessagePreview = this.truncate(content, 80);
          listRoom.lastMessageAt = this.toIso(saved.sentAt);
          this.sortRooms();
          this.applyRoomFilter();
        }
      },
      error: err => {
        this.messages = this.messages.map(m => m.id === tempId ? { ...m, status: MessageStatus.FAILED } : m);
        this.rebuildGroups();
        this.composerError = this.extractError(err, 'Échec de l\'envoi du message.');
      }
    });
  }

  retry(msg: ChatMessage): void {
    this.messages = this.messages.filter(m => m.id !== msg.id);
    this.rebuildGroups();
    this.newMessage = msg.content;
    this.sendMessage();
  }

  onComposerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  onComposerInput(): void {
    if (this.composerError && this.newMessage.trim()) this.composerError = '';
    this.resizeComposer();
    if (!this.selectedRoom || !this.isConnected) return;
    const now = Date.now();
    if (now - this.lastTypingSent > 1500) {
      this.chatService.sendTypingIndicator(this.selectedRoom.id, true);
      this.lastTypingSent = now;
    }
    clearTimeout(this.typingTimer);
    this.typingTimer = setTimeout(() => this.stopTyping(), 2500);
  }

  private stopTyping(): void {
    clearTimeout(this.typingTimer);
    if (this.selectedRoom && this.isConnected && this.lastTypingSent) {
      this.chatService.sendTypingIndicator(this.selectedRoom.id, false);
    }
    this.lastTypingSent = 0;
  }

  private resizeComposer(): void {
    setTimeout(() => {
      const el = this.composer?.nativeElement;
      if (!el) return;
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 160) + 'px';
    });
  }

  // ══════════════════════════════════════════════════════
  // FICHIERS
  // ══════════════════════════════════════════════════════

  triggerFileUpload(): void { this.fileInput?.nativeElement.click(); }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.selectedRoom) return;

    const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
    if (CHAT_LIMITS.FORBIDDEN_EXT.includes(ext)) {
      this.composerError = `Type de fichier non autorisé (.${ext}).`;
      return;
    }
    if (file.size === 0) { this.composerError = 'Le fichier est vide.'; return; }
    if (file.size > CHAT_LIMITS.FILE_MAX_BYTES) {
      this.composerError = `Le fichier dépasse 25 Mo (${this.formatFileSize(file.size)}).`;
      return;
    }

    this.composerError = '';
    this.uploadingFile = true;
    const roomId = this.selectedRoom.id;
    this.chatService.uploadFile(roomId, file).subscribe({
      next: msg => {
        this.uploadingFile = false;
        this.seenIds.add(msg.id);
        if (this.selectedRoom?.id === roomId) {
          this.upsertMessage(msg);
          this.scrollToBottom(true);
        }
      },
      error: err => {
        this.uploadingFile = false;
        this.composerError = this.extractError(err, 'Échec de l\'envoi du fichier.');
      }
    });
  }

  // ══════════════════════════════════════════════════════
  // ACTIONS MESSAGE
  // ══════════════════════════════════════════════════════

  togglePin(msg: ChatMessage): void {
    const req = msg.pinned ? this.chatService.unpinMessage(msg.id) : this.chatService.pinMessage(msg.id);
    req.subscribe({
      next: updated => {
        this.upsertMessage(updated);
        this.pinnedMessages = updated.pinned
          ? [updated, ...this.pinnedMessages.filter(p => p.id !== updated.id)]
          : this.pinnedMessages.filter(p => p.id !== updated.id);
        this.showToast(updated.pinned ? 'Message épinglé' : 'Message désépinglé');
      },
      error: err => this.showToast(this.extractError(err, 'Action impossible'))
    });
  }

  deleteMessage(msg: ChatMessage): void {
    if (!confirm('Supprimer ce message ? Il restera archivé pour la traçabilité.')) return;
    this.chatService.deleteMessage(msg.id).subscribe({
      next: updated => {
        this.upsertMessage(updated);
        this.pinnedMessages = this.pinnedMessages.filter(p => p.id !== updated.id);
      },
      error: err => this.showToast(this.extractError(err, 'Suppression impossible'))
    });
  }

  copyMessage(msg: ChatMessage): void {
    navigator.clipboard?.writeText(msg.content).then(() => this.showToast('Message copié'));
  }

  jumpToMessage(msg: ChatMessage): void {
    const el = document.getElementById(`msg-${msg.id}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      el.classList.add('flash');
      setTimeout(() => el.classList.remove('flash'), 1600);
    }
  }

  // ══════════════════════════════════════════════════════
  // PANNEAUX
  // ══════════════════════════════════════════════════════

  togglePanel(panel: SidePanel): void {
    this.sidePanel = this.sidePanel === panel ? 'none' : panel;
    if (this.sidePanel === 'search') setTimeout(() => document.getElementById('chat-search-input')?.focus(), 50);
  }

  onSearchInput(): void { this.searchSubject.next(this.searchQuery); }

  private doSearch(q: string): void {
    const query = q.trim();
    if (query.length < 2 || !this.selectedRoom) { this.searchResults = []; return; }
    this.isSearching = true;
    this.chatService.searchMessages(this.selectedRoom.id, query).subscribe({
      next: r => { this.searchResults = r; this.isSearching = false; },
      error: () => this.isSearching = false
    });
  }

  leaveGroup(): void {
    const room = this.selectedRoom;
    if (!room || !confirm(`Quitter le groupe « ${room.displayName} » ?`)) return;
    this.chatService.leaveRoom(room.id).subscribe({
      next: () => {
        this.chatService.unsubscribeFromRoom(room.id);
        this.subscribedRooms.delete(room.id);
        this.rooms = this.rooms.filter(r => r.id !== room.id);
        this.selectedRoom = null;
        this.messages = [];
        this.groupedMessages = [];
        this.sidePanel = 'none';
        this.applyRoomFilter();
        this.showToast('Vous avez quitté le groupe');
      },
      error: err => this.showToast(this.extractError(err, 'Action impossible'))
    });
  }

  // ══════════════════════════════════════════════════════
  // MODALE : CONVERSATION PRIVÉE (ONE-TO-ONE)
  // ══════════════════════════════════════════════════════

  openDmModal(): void {
    this.contactSearch = '';
    this.showDmModal = true;
    if (!this.contacts.length) {
      this.chatService.getContacts().subscribe({ next: c => this.contacts = c, error: () => {} });
    }
    setTimeout(() => document.getElementById('dm-search')?.focus(), 50);
  }

  get filteredContacts(): ChatContact[] {
    const t = this.normalize(this.contactSearch);
    if (!t) return this.contacts;
    return this.contacts.filter(c =>
      this.normalize(`${c.fullName} ${c.email} ${c.serviceNom || ''} ${c.roles.join(' ')}`).includes(t));
  }

  startDm(contact: ChatContact): void {
    if (this.creatingDm) return;
    const existing = this.rooms.find(r => r.roomType === RoomType.PRIVATE_DM && r.otherUserId === contact.id);
    if (existing) {
      this.showDmModal = false;
      this.selectRoom(existing);
      return;
    }
    this.creatingDm = true;
    this.chatService.getOrCreateDm(contact.id).subscribe({
      next: room => {
        this.creatingDm = false;
        this.showDmModal = false;
        if (!this.rooms.some(r => r.id === room.id)) this.rooms = [room, ...this.rooms];
        this.applyRoomFilter();
        this.subscribeRealtime();
        this.selectRoom(room);
      },
      error: err => {
        this.creatingDm = false;
        this.showToast(this.extractError(err, 'Impossible d\'ouvrir la conversation'));
      }
    });
  }

  // ══════════════════════════════════════════════════════
  // MODALE : GROUPE / SALON
  // ══════════════════════════════════════════════════════

  openGroupModal(): void {
    this.groupForm.reset({ name: '', description: '', privateRoom: true, memberIds: [] });
    this.groupServerError = '';
    this.contactSearch = '';
    this.showGroupModal = true;
    if (!this.contacts.length) {
      this.chatService.getContacts().subscribe({ next: c => this.contacts = c, error: () => {} });
    }
  }

  get selectedMemberIds(): number[] { return this.groupForm.get('memberIds')?.value || []; }

  isMemberSelected(id: number): boolean { return this.selectedMemberIds.includes(id); }

  toggleMember(id: number): void {
    const ids = this.selectedMemberIds;
    const next = ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id];
    if (next.length > 50) return;
    this.groupForm.patchValue({ memberIds: next });
    this.groupForm.markAsDirty();
  }

  contactName(id: number): string {
    return this.contacts.find(c => c.id === id)?.fullName || '';
  }

  fieldError(name: string): string {
    const c = this.groupForm.get(name);
    if (!c || !(c.touched || c.dirty) || !c.errors) return '';
    if (c.errors['required']) return 'Ce champ est obligatoire.';
    if (c.errors['minlength']) return `Minimum ${c.errors['minlength'].requiredLength} caractères.`;
    if (c.errors['maxlength']) return `Maximum ${c.errors['maxlength'].requiredLength} caractères.`;
    if (c.errors['pattern']) return 'Caractères autorisés : lettres, chiffres, espace, - _ # \' .';
    return 'Valeur invalide.';
  }

  submitGroup(): void {
    this.groupForm.markAllAsTouched();
    this.groupServerError = '';
    if (this.groupForm.invalid) return;

    const v = this.groupForm.value;
    this.groupSubmitting = true;
    this.chatService.createRoom({
      name: v.name.trim(),
      description: v.description?.trim() || undefined,
      privateRoom: !!v.privateRoom,
      memberIds: v.privateRoom ? v.memberIds : []
    }).subscribe({
      next: room => {
        this.groupSubmitting = false;
        this.showGroupModal = false;
        this.rooms = [room, ...this.rooms.filter(r => r.id !== room.id)];
        this.applyRoomFilter();
        this.subscribeRealtime();
        this.selectRoom(room);
        this.showToast(v.privateRoom ? 'Groupe créé' : 'Salon créé');
      },
      error: err => {
        this.groupSubmitting = false;
        this.groupServerError = this.extractError(err, 'Création impossible.');
      }
    });
  }

  // ── Ajout de membres (groupe existant) ──────────────────────────
  openAddMemberModal(): void {
    this.contactSearch = '';
    this.showAddMemberModal = true;
  }

  get addableContacts(): ChatContact[] {
    const members = this.selectedRoom?.memberIds || [];
    return this.filteredContacts.filter(c => !members.includes(c.id));
  }

  addMember(contact: ChatContact): void {
    if (!this.selectedRoom) return;
    this.chatService.addMember(this.selectedRoom.id, contact.id).subscribe({
      next: room => {
        this.selectedRoom = { ...room, unreadCount: 0 };
        this.rooms = this.rooms.map(r => r.id === room.id ? { ...room, unreadCount: 0 } : r);
        this.applyRoomFilter();
        this.showToast(`${contact.fullName} a été ajouté`);
      },
      error: err => this.showToast(this.extractError(err, 'Ajout impossible'))
    });
  }

  // ══════════════════════════════════════════════════════
  // HELPERS
  // ══════════════════════════════════════════════════════

  isMine(msg: ChatMessage): boolean { return msg.senderId === this.currentUserId; }

  /** Regroupe les messages consécutifs d'un même auteur (< 5 min) */
  isGroupedWithPrevious(list: ChatMessage[], i: number): boolean {
    if (i === 0) return false;
    const prev = list[i - 1], cur = list[i];
    if (prev.messageType === MessageType.SYSTEM || cur.messageType === MessageType.SYSTEM) return false;
    return prev.senderId === cur.senderId &&
      (new Date(cur.sentAt).getTime() - new Date(prev.sentAt).getTime()) < 5 * 60000;
  }

  getInitials(name?: string): string {
    if (!name) return '?';
    return name.split(/\s+/).filter(Boolean).map(n => n[0]).join('').substring(0, 2).toUpperCase();
  }

  avatarColor(seed?: string | number): string {
    const palette = ['#2563eb', '#7c3aed', '#db2777', '#ea580c', '#059669', '#0891b2', '#4f46e5', '#b45309'];
    const s = String(seed ?? '');
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
    return palette[h % palette.length];
  }

  roomSubtitle(room: ChatRoom): string {
    switch (room.roomType) {
      case RoomType.PRIVATE_DM:      return room.otherUserEmail || 'Conversation privée';
      case RoomType.SERVICE_CHANNEL: return `Salon officiel · ${room.serviceCode}`;
      case RoomType.GROUP:           return `Groupe privé · ${room.memberCount} membres`;
      default:                       return room.description || 'Salon public';
    }
  }

  roleLabel(role: string): string {
    const map: Record<string, string> = {
      ADMIN: 'Admin', SUPERVISEUR: 'Superviseur', ATC: 'ATC', PISTE: 'Piste', AIS: 'AIS',
      RH_FORMATION: 'RH', QUALITE: 'Qualité', CONSULTATION: 'Consultation'
    };
    return map[role] || role;
  }

  /** Heures affichées dans le fuseau horaire choisi dans Paramètres */
  formatListTime(d?: string): string {
    if (!d) return '';
    const key = this.prefs.dayKey(d);
    if (key === this.prefs.dayKey(new Date())) return this.prefs.formatDate(d, 'time');
    if (key === this.prefs.dayKey(new Date(Date.now() - 86400000))) return this.prefs.lang === 'en' ? 'Yesterday' : 'Hier';
    return this.prefs.formatDate(d, 'short');
  }

  formatTime(d: string | Date): string {
    return this.prefs.formatDate(d, 'time');
  }

  formatFileSize(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / 1048576).toFixed(1)} Mo`;
  }

  isImage(msg: ChatMessage): boolean {
    return msg.messageType === MessageType.IMAGE || (msg.fileType?.startsWith('image/') ?? false);
  }

  openUrl(url?: string): void {
    if (url) window.open(url, '_blank', 'noopener');
  }

  trackById = (_: number, item: { id: any }) => item.id;
  trackByDay = (_: number, g: MessageGroup) => g.day;

  private upsertMessage(msg: ChatMessage): void {
    const idx = this.messages.findIndex(m => m.id === msg.id);
    if (idx >= 0) {
      const copy = [...this.messages];
      copy[idx] = msg;
      this.messages = copy;
    } else {
      // Remplace un message optimiste identique encore en cours d'envoi
      const pending = this.messages.findIndex(m =>
        m.status === MessageStatus.SENDING && m.senderId === msg.senderId && m.content === msg.content);
      if (pending >= 0) {
        const copy = [...this.messages];
        copy[pending] = msg;
        this.messages = copy;
      } else {
        this.messages = [...this.messages, msg];
      }
    }
    this.rebuildGroups();
  }

  private rebuildGroups(): void {
    const groups: MessageGroup[] = [];
    const en = this.prefs.lang === 'en';
    const today = this.prefs.dayKey(new Date());
    const yesterday = this.prefs.dayKey(new Date(Date.now() - 86400000));
    for (const m of this.messages) {
      const d = new Date(m.sentAt);
      const key = this.prefs.dayKey(d);
      const label = key === today ? (en ? 'Today' : 'Aujourd\'hui')
        : key === yesterday ? (en ? 'Yesterday' : 'Hier')
        : new Intl.DateTimeFormat(this.prefs.locale, { timeZone: this.prefs.timezone, weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(d);
      const last = groups[groups.length - 1];
      if (last && last.day === label) last.items.push(m);
      else groups.push({ day: label, items: [m] });
    }
    this.groupedMessages = groups;
  }

  private sortRooms(): void {
    this.rooms = [...this.rooms].sort((a, b) => {
      const ta = a.lastMessageAt ? new Date(a.lastMessageAt).getTime() : 0;
      const tb = b.lastMessageAt ? new Date(b.lastMessageAt).getTime() : 0;
      return tb - ta || a.displayName.localeCompare(b.displayName);
    });
  }

  private scrollToBottom(force = false): void {
    setTimeout(() => {
      const el = this.scrollContainer?.nativeElement;
      if (!el) return;
      const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 160;
      if (force || nearBottom) el.scrollTop = el.scrollHeight;
    }, 30);
  }

  private showToast(text: string): void {
    this.toast = text;
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toast = '', 3200);
  }

  private extractError(err: any, fallback: string): string {
    const e = err?.error;
    if (!e) return fallback;
    if (typeof e === 'string') return e;
    if (e.message) return e.message;
    const first = Object.values(e).find(v => typeof v === 'string');
    return (first as string) || fallback;
  }

  private normalize(s: string): string {
    return (s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();
  }

  private truncate(s: string, max: number): string {
    return s && s.length > max ? s.substring(0, max) + '…' : s;
  }

  private toIso(d: string | Date): string {
    return typeof d === 'string' ? d : d.toISOString();
  }
}
