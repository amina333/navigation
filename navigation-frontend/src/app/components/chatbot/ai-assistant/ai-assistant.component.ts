import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../services/auth.service';

interface AiResponse {
  id?: number;
  question?: string;
  response: string;
  mode?: 'LLM' | 'RULES';
  intent?: string;
  sources?: string[];
  suggestions?: string[];
  requiresHumanValidation?: boolean;
  createdAt?: string;
}

interface AiMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  html?: string;
  timestamp: Date;
  mode?: string;
  sources?: string[];
  suggestions?: string[];
  validation?: boolean;
  error?: boolean;
}

@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-assistant.component.html',
  styleUrls: ['./ai-assistant.component.scss']
})
export class AIAssistantComponent implements OnInit {

  @ViewChild('messagesContainer') private messagesContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('inputEl') private inputEl?: ElementRef<HTMLTextAreaElement>;

  readonly MAX_LENGTH = 4000;

  isOpen = false;
  isTyping = false;
  userInput = '';
  inputError = '';
  messages: AiMessage[] = [];
  mode: 'LLM' | 'RULES' | '' = '';
  modelName = '';
  starterSuggestions: string[] = [];
  private historyLoaded = false;

  private readonly API = `${environment.apiUrl}/ai`;

  constructor(private http: HttpClient, private authService: AuthService) {}

  ngOnInit(): void {
    this.messages = [this.welcome()];
  }

  private welcome(): AiMessage {
    const first = (this.authService.getCurrentUser()?.fullName || '').split(' ')[0];
    const content = `✈️ Bonjour ${first} ! Je suis **SkyAI**, votre assistant métier.\n` +
      `Je m'appuie sur les **données réelles de la plateforme** et les références **OACI**, et je cite mes sources.\n` +
      `_Je vous assiste : les décisions opérationnelles restent les vôtres._`;
    return { id: 'welcome', role: 'assistant', content, html: this.render(content), timestamp: new Date() };
  }

  toggleChat(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.loadStatusAndHistory();
      setTimeout(() => { this.inputEl?.nativeElement.focus(); this.scrollToBottom(); }, 80);
    }
  }

  closeChat(): void { this.isOpen = false; }

  private loadStatusAndHistory(): void {
    if (this.historyLoaded) return;
    this.historyLoaded = true;

    this.http.get<{ mode: 'LLM' | 'RULES'; model: string; suggestions: string[] }>(`${this.API}/status`).subscribe({
      next: s => { this.mode = s.mode; this.modelName = s.model; this.starterSuggestions = s.suggestions || []; },
      error: () => {}
    });

    this.http.get<AiResponse[]>(`${this.API}/history`, { params: { limit: 20 } }).subscribe({
      next: history => {
        const restored: AiMessage[] = [];
        history.forEach(h => {
          const ts = h.createdAt ? new Date(h.createdAt) : new Date();
          restored.push({ id: `u-${h.id}`, role: 'user', content: h.question || '', timestamp: ts });
          restored.push(this.toAssistant(h, ts));
        });
        this.messages = [this.welcome(), ...restored];
        this.scrollToBottom();
      },
      error: () => {}
    });
  }

  // ── Envoi ─────────────────────────────────────────────────────
  get remaining(): number { return this.MAX_LENGTH - this.userInput.length; }

  get canSend(): boolean {
    const t = this.userInput.trim();
    return !this.isTyping && t.length > 0 && t.length <= this.MAX_LENGTH;
  }

  ask(text: string): void {
    if (text.includes('…')) {
      // Suggestion à compléter (ex. « Classe ce document : … »)
      this.userInput = text.replace('…', '').trimEnd() + ' ';
      setTimeout(() => this.inputEl?.nativeElement.focus());
      return;
    }
    if (text === 'Décoder un METAR') {
      this.userInput = 'METAR DTTA 071030Z 33012G25KT 8000 -RA SCT015 BKN030 18/14 Q1012 NOSIG';
      setTimeout(() => this.inputEl?.nativeElement.focus());
      return;
    }
    this.userInput = text;
    this.sendMessage();
  }

  sendMessage(): void {
    const text = this.userInput.trim();
    this.inputError = '';
    if (!text) { this.inputError = 'Veuillez saisir une question.'; return; }
    if (text.length > this.MAX_LENGTH) { this.inputError = `Maximum ${this.MAX_LENGTH} caractères.`; return; }
    if (this.isTyping) return;

    this.messages.push({ id: `u-${Date.now()}`, role: 'user', content: text, timestamp: new Date() });
    this.userInput = '';
    this.resizeInput();
    this.isTyping = true;
    this.scrollToBottom();

    this.http.post<AiResponse>(`${this.API}/chat`, { message: text }).subscribe({
      next: res => {
        this.isTyping = false;
        if (res.mode) this.mode = res.mode;
        this.messages.push(this.toAssistant(res, new Date()));
        this.scrollToBottom();
      },
      error: err => {
        this.isTyping = false;
        const msg = err?.error?.message || err?.error?.message?.message
          || (err?.status === 0 ? 'Serveur injoignable. Vérifiez que le backend est démarré.' : 'Le service SkyAI est temporairement indisponible.');
        this.messages.push({ id: `e-${Date.now()}`, role: 'assistant', content: msg, html: this.render('⚠️ ' + msg), timestamp: new Date(), error: true });
        this.scrollToBottom();
      }
    });
  }

  retryLast(): void {
    const lastUser = [...this.messages].reverse().find(m => m.role === 'user');
    if (lastUser) { this.userInput = lastUser.content; this.sendMessage(); }
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  onInput(): void {
    if (this.inputError && this.userInput.trim()) this.inputError = '';
    this.resizeInput();
  }

  clearChat(): void {
    if (!confirm('Effacer l\'historique SkyAI ? (il reste archivé pour la traçabilité)')) return;
    this.http.delete(`${this.API}/history`).subscribe({ next: () => {}, error: () => {} });
    this.messages = [this.welcome()];
  }

  copy(msg: AiMessage): void {
    navigator.clipboard?.writeText(msg.content);
  }

  get lastSuggestions(): string[] {
    const last = this.messages[this.messages.length - 1];
    if (last?.role === 'assistant' && last.suggestions?.length) return last.suggestions;
    return this.messages.length <= 1 ? this.starterSuggestions : [];
  }

  // ── Rendu ─────────────────────────────────────────────────────
  private toAssistant(res: AiResponse, ts: Date): AiMessage {
    return {
      id: `a-${res.id ?? Date.now()}`,
      role: 'assistant',
      content: res.response,
      html: this.render(res.response),
      timestamp: ts,
      mode: res.mode,
      sources: res.sources || [],
      suggestions: res.suggestions || [],
      validation: !!res.requiresHumanValidation
    };
  }

  /** Markdown léger et sûr : échappement HTML puis **gras**, _italique_, `code`, listes, retours ligne */
  private render(text: string): string {
    const esc = (text || '')
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    return esc
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/(^|[\s(])_(.+?)_(?=[\s).,!?:;]|$)/gm, '$1<em>$2</em>')
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/^#{1,3}\s+(.+)$/gm, '<strong>$1</strong>')
      .replace(/^\s*[-*]\s+/gm, '• ')
      .replace(/\n/g, '<br>');
  }

  private resizeInput(): void {
    setTimeout(() => {
      const el = this.inputEl?.nativeElement;
      if (!el) return;
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 120) + 'px';
    });
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.messagesContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 40);
  }
}
