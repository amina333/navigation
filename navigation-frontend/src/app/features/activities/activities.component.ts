import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { interval, Subscription } from 'rxjs';
import { catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Activity {
  id: number;
  title: string;
  description: string;
  type: 'success' | 'warning' | 'error' | 'info';
  category: 'user' | 'flight' | 'system' | 'chat' | 'security';
  actor: string;
  timestamp: string; // ISO string from backend
}

@Component({
  selector: 'app-activities',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './activities.component.html',
  styleUrls: ['./activities.component.scss']
})
export class ActivitiesComponent implements OnInit, OnDestroy {

  private readonly API = `${environment.apiUrl}/activities`;

  activities: Activity[] = [];
  filteredActivities: Activity[] = [];
  searchTerm = '';
  categoryFilter = '';
  typeFilter = '';
  loading = false;
  lastRefresh: Date | null = null;
  private refreshSub?: Subscription;

  categories = [
    { value: '',         label: 'Toutes les catégories' },
    { value: 'user',     label: 'Utilisateurs' },
    { value: 'flight',   label: 'Vols' },
    { value: 'chat',     label: 'Messages' },
    { value: 'system',   label: 'Système' },
    { value: 'security', label: 'Sécurité' }
  ];

  types = [
    { value: '',        label: 'Tous les types' },
    { value: 'success', label: 'Succès' },
    { value: 'warning', label: 'Avertissement' },
    { value: 'error',   label: 'Erreur' },
    { value: 'info',    label: 'Information' }
  ];

  constructor(private router: Router, private http: HttpClient) {}

  ngOnInit(): void {
    this.loadActivities();
    // Auto-refresh toutes les 30 secondes
    this.refreshSub = interval(30_000).subscribe(() => this.loadActivities());
  }

  ngOnDestroy(): void {
    this.refreshSub?.unsubscribe();
  }

  loadActivities(): void {
    this.loading = true;
    this.http.get<Activity[]>(`${this.API}?limit=100`).pipe(
      catchError(err => {
        console.error('[Activities] Erreur API :', err);
        return of([]);
      })
    ).subscribe(data => {
      this.activities = data;
      this.lastRefresh = new Date();
      this.filterActivities();
      this.loading = false;
    });
  }

  filterActivities(): void {
    let result = [...this.activities];

    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      result = result.filter(a =>
        a.title.toLowerCase().includes(term) ||
        a.description.toLowerCase().includes(term) ||
        (a.actor || '').toLowerCase().includes(term)
      );
    }

    if (this.categoryFilter) result = result.filter(a => a.category === this.categoryFilter);
    if (this.typeFilter)     result = result.filter(a => a.type === this.typeFilter);

    this.filteredActivities = result;
  }

  formatTime(ts: string): string {
    if (!ts) return '';
    const date = new Date(ts);
    const now = new Date();
    const diff = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (diff < 60)          return 'À l\'instant';
    if (diff < 3600)        return `Il y a ${Math.floor(diff / 60)} min`;
    if (diff < 86400)       return `Il y a ${Math.floor(diff / 3600)} h`;
    if (diff < 86400 * 7)   return `Il y a ${Math.floor(diff / 86400)} jour${Math.floor(diff / 86400) > 1 ? 's' : ''}`;
    return date.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  formatFullDate(ts: string): string {
    if (!ts) return '';
    return new Date(ts).toLocaleString('fr-FR', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  getCategoryLabel(cat: string): string {
    const map: Record<string, string> = {
      user: 'Utilisateur', flight: 'Vol', system: 'Système',
      chat: 'Message', security: 'Sécurité'
    };
    return map[cat] || cat;
  }

  goBack(): void { this.router.navigate(['/dashboard']); }

  get stats() {
    const all = this.activities;
    return {
      total:   all.length,
      success: all.filter(a => a.type === 'success').length,
      warning: all.filter(a => a.type === 'warning').length,
      error:   all.filter(a => a.type === 'error').length,
    };
  }
}
