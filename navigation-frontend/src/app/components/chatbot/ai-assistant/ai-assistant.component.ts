import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { environment } from '../../../../environments/environment';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-assistant.component.html',
  styleUrls: ['./ai-assistant.component.scss']
})
export class AIAssistantComponent implements OnInit, OnDestroy, AfterViewChecked {

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  isOpen = false;
  isTyping = false;
  userInput = '';
  messages: ChatMessage[] = [];

  private readonly API_URL = `${environment.apiUrl}/ai/chat`;

  readonly welcomeMessage: ChatMessage = {
    id: 'welcome',
    role: 'assistant',
    content: '✈️ Bonjour ! Je suis **SkyAI**, votre assistant de navigation aérienne. Posez-moi vos questions sur les vols, les statuts, les règles ATC ou les procédures d\'urgence.',
    timestamp: new Date()
  };

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.messages = [this.welcomeMessage];
  }

  ngOnDestroy(): void {}

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  toggleChat(): void {
    this.isOpen = !this.isOpen;
  }

  closeChat(): void {
    this.isOpen = false;
  }

  sendMessage(): void {
    const text = this.userInput.trim();
    if (!text || this.isTyping) return;

    // Add user message
    this.messages.push({
      id: Date.now().toString(),
      role: 'user',
      content: text,
      timestamp: new Date()
    });
    this.userInput = '';
    this.isTyping = true;

    // Send to Spring AI backend
    this.http.post<{ response: string }>(this.API_URL, { message: text }).pipe(
      catchError(() => of({ response: 'Désolé, le service IA est temporairement indisponible. Veuillez réessayer dans quelques instants.' }))
    ).subscribe(res => {
      this.isTyping = false;
      this.messages.push({
        id: Date.now().toString(),
        role: 'assistant',
        content: res.response,
        timestamp: new Date()
      });
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  clearChat(): void {
    this.messages = [this.welcomeMessage];
  }

  private scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
      }
    } catch {}
  }
}
