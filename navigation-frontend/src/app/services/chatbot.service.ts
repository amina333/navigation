import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';

interface ChatbotResponse {
  message: string;
  actions?: Array<{
    label: string;
    action: string;
  }>;
}

interface FlightStatus {
  active: number;
  scheduled: number;
  delayed: number;
  landed: number;
}

interface WeatherAlert {
  type: string;
  description: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatbotService {
  private readonly API_URL = `${environment.apiUrl}/chatbot`;
  private connectedSubject = new BehaviorSubject<boolean>(false);
  private typingSubject = new BehaviorSubject<boolean>(false);
  private messageSubject = new BehaviorSubject<ChatbotResponse | null>(null);
  
  connected$ = this.connectedSubject.asObservable();
  typing$ = this.typingSubject.asObservable();
  onMessage = () => this.messageSubject.asObservable();
  onTyping = () => this.typingSubject.asObservable();
  onConnectionChange = () => this.connectedSubject.asObservable();

  constructor(private http: HttpClient) {}

  connect(): Observable<boolean> {
    // Simulate connection
    setTimeout(() => {
      this.connectedSubject.next(true);
    }, 1000);
    return of(true);
  }

  disconnect(): void {
    this.connectedSubject.next(false);
  }

  sendMessage(message: string, settings: any): Observable<ChatbotResponse> {
    this.typingSubject.next(true);
    
    // Simulate AI response
    return this.http.post<ChatbotResponse>(`${this.API_URL}/chat`, { 
      message, 
      settings 
    }).pipe(
      map(response => {
        this.typingSubject.next(false);
        this.messageSubject.next(response);
        return response;
      }),
      catchError(error => {
        this.typingSubject.next(false);
        return of({
          message: 'Désolé, je ne peux pas répondre pour le moment. Veuillez réessayer plus tard.',
          actions: []
        });
      })
    );
  }

  sendTypingIndicator(isTyping: boolean): void {
    // In a real implementation, this would send to backend
    console.log('Typing indicator:', isTyping);
  }

  getFlightStatus(): Observable<FlightStatus> {
    return this.http.get<FlightStatus>(`${this.API_URL}/flight-status`).pipe(
      catchError(() => of({
        active: 12,
        scheduled: 8,
        delayed: 2,
        landed: 25
      }))
    );
  }

  getWeatherAlerts(): Observable<WeatherAlert[]> {
    return this.http.get<WeatherAlert[]>(`${this.API_URL}/weather-alerts`).pipe(
      catchError(() => of([
        {
          type: 'Vent fort',
          description: 'Rafales de vent jusqu\'à 45 noeuds prévues cet après-midi'
        }
      ]))
    );
  }
}
