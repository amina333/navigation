import { Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import Chart from 'chart.js/auto';
import type { ChartConfiguration } from 'chart.js';
import { environment } from '../../../../environments/environment';

interface Point { label: string; value: number; }

interface Stats {
  generatedAt: string;
  kpis: Record<string, number>;
  usersByRole: Point[];
  usersStatus: Point[];
  usersCreatedMonthly: Point[];
  recentUsers: { id: number; fullName: string; email: string; roles: string[]; active: boolean; createdAt: string }[];
  flightsByStatus: Point[];
  flightsByType: Point[];
  flightsByAirline: Point[];
  flightsByAircraft: Point[];
  flightsByHour: Point[];
  flightsDaily: { date: string; total: number; delayed: number; cancelled: number }[];
  roomsByType: Point[];
  messagesDaily: Point[];
  topRooms: { name: string; type: string; messages: number }[];
  aiByIntent: Point[];
  aiByMode: Point[];
  aiDaily: Point[];
}

interface TableView { headers: string[]; rows: (string | number)[][]; }

/** Palette de référence validée (dataviz) — rôles, pas de couleurs en dur dans les graphiques */
const C = {
  s1: '#2a78d6',   // série 1 — bleu
  s2: '#eb6834',   // série 2 — orange
  s3: '#1baf7a',   // série 3 — aqua (contraste < 3:1 → vue tableau disponible)
  grid: '#e1e0d9',
  axis: '#c3c2b7',
  muted: '#898781',
  ink: '#0b0b0b',
  ink2: '#52514e',
  surface: '#ffffff'
};

const STATUS_LABELS: Record<string, string> = {
  PROGRAMME: 'Programmé', EN_EMBARQUEMENT: 'Embarquement', DECOLLAGE: 'Décollage', EN_VOL: 'En vol',
  EN_APPROCHE: 'En approche', ATTERRI: 'Atterri', AU_SOL: 'Au sol', ARRIVE: 'Arrivé',
  RETARDE: 'Retardé', ANNULE: 'Annulé', DIVERTE: 'Dérouté', INCONNU: 'Inconnu'
};
const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrateur', SUPERVISEUR: 'Superviseur', ATC: 'Contrôleur ATC', PISTE: 'Agent piste',
  AIS: 'Agent AIS', RH_FORMATION: 'RH / Formation', QUALITE: 'Qualité', CONSULTATION: 'Consultation'
};
const ROOM_LABELS: Record<string, string> = {
  PUBLIC: 'Salons publics', SERVICE_CHANNEL: 'Salons de service', GROUP: 'Groupes', PRIVATE_DM: 'Conversations privées'
};
const INTENT_LABELS: Record<string, string> = {
  GREETING: 'Salutation', HELP: 'Aide', WHOAMI: 'Profil', FLIGHT_LOOKUP: 'Fiche vol', FLIGHT_STATS: 'Trafic',
  DELAYS: 'Retards', CANCELLATIONS: 'Annulations', ACTIVE_FLIGHTS: 'Vols en cours', ANOMALIES: 'Anomalies',
  REPORT: 'Rapport', COMPARE: 'Comparaison', METAR: 'METAR', WEATHER: 'Météo', EMERGENCY: 'Urgences',
  RUNWAY: 'Pistes', ATC: 'Procédures ATC', REGULATION: 'Réglementation', CERTIFICATION: 'Certifications',
  DOCUMENTS: 'Documents', SUMMARY: 'Résumé', CLASSIFY: 'Classification', CHAT_HELP: 'Messagerie',
  STAFF: 'Effectifs', UNKNOWN: 'Non reconnue', Autres: 'Autres'
};

@Component({
  selector: 'app-statistics',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './statistics.component.html',
  styleUrls: ['./statistics.component.scss']
})
export class StatisticsComponent implements OnInit, OnDestroy {

  @ViewChildren('chartCanvas') canvases!: QueryList<ElementRef<HTMLCanvasElement>>;

  stats: Stats | null = null;
  loading = true;
  error = '';
  tables: Record<string, TableView> = {};
  tableOpen: Record<string, boolean> = {};

  readonly roleLabel = (r: string) => ROLE_LABELS[r] || r;

  private charts: Chart[] = [];

  constructor(private http: HttpClient) {}

  ngOnInit(): void { this.load(); }

  ngOnDestroy(): void { this.destroyCharts(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.http.get<Stats>(`${environment.apiUrl}/admin/statistics`).subscribe({
      next: s => {
        this.stats = s;
        this.loading = false;
        this.buildTables(s);
        // Les <canvas> apparaissent après le rendu du template
        setTimeout(() => this.renderCharts(s));
      },
      error: err => {
        this.loading = false;
        this.error = err?.status === 403
          ? 'Accès réservé aux administrateurs et superviseurs.'
          : err?.status === 0 ? 'Serveur injoignable.' : (err?.error?.message || 'Impossible de charger les statistiques.');
      }
    });
  }

  toggleTable(key: string): void {
    this.tableOpen[key] = !this.tableOpen[key];
  }

  // ── Valeurs dérivées pour le template ─────────────────────────
  kpi(key: string): number { return this.stats?.kpis?.[key] ?? 0; }

  share(points: Point[] | undefined, label: string): number {
    if (!points?.length) return 0;
    const total = points.reduce((s, p) => s + p.value, 0);
    const v = points.find(p => p.label === label)?.value ?? 0;
    return total ? Math.round((v / total) * 100) : 0;
  }

  valueOf(points: Point[] | undefined, label: string): number {
    return points?.find(p => p.label === label)?.value ?? 0;
  }

  roomTypeLabel(t: string): string { return ROOM_LABELS[t] || t; }

  initials(name: string): string {
    return (name || '?').split(/\s+/).filter(Boolean).map(p => p[0]).join('').substring(0, 2).toUpperCase();
  }

  roomCount(t: string): number { return this.valueOf(this.stats?.roomsByType, t); }

  sparkTrend(points: Point[] | undefined): number {
    if (!points || points.length < 14) return 0;
    const last7 = points.slice(-7).reduce((s, p) => s + p.value, 0);
    const prev7 = points.slice(-14, -7).reduce((s, p) => s + p.value, 0);
    if (prev7 === 0) return last7 > 0 ? 100 : 0;
    return Math.round(((last7 - prev7) / prev7) * 100);
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  // ── Graphiques ───────────────────────────────────────────────
  private destroyCharts(): void {
    this.charts.forEach(c => c.destroy());
    this.charts = [];
  }

  private canvas(key: string): HTMLCanvasElement | undefined {
    return this.canvases?.find(c => c.nativeElement.dataset['key'] === key)?.nativeElement;
  }

  private renderCharts(s: Stats): void {
    this.destroyCharts();
    const shortDay = (d: string) => new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });

    this.line('traffic', s.flightsDaily.map(d => shortDay(d.date)), [
      { label: 'Vols', data: s.flightsDaily.map(d => d.total), color: C.s1 },
      { label: 'Retardés', data: s.flightsDaily.map(d => d.delayed), color: C.s2 },
      { label: 'Annulés', data: s.flightsDaily.map(d => d.cancelled), color: C.s3 }
    ]);

    this.line('activity', s.messagesDaily.map(p => shortDay(p.label)), [
      { label: 'Messages', data: s.messagesDaily.map(p => p.value), color: C.s1 },
      { label: 'Requêtes SkyAI', data: s.aiDaily.map(p => p.value), color: C.s2 }
    ]);

    this.bars('status', s.flightsByStatus.map(p => STATUS_LABELS[p.label] || p.label), s.flightsByStatus.map(p => p.value), 'Vols', true);
    this.bars('hours', s.flightsByHour.map(p => p.label), s.flightsByHour.map(p => p.value), 'Vols prévus', false);
    this.bars('airlines', s.flightsByAirline.map(p => p.label), s.flightsByAirline.map(p => p.value), 'Vols', true);
    this.bars('roles', s.usersByRole.map(p => ROLE_LABELS[p.label] || p.label), s.usersByRole.map(p => p.value), 'Agents', true);
    this.bars('monthly', s.usersCreatedMonthly.map(p => this.monthLabel(p.label)), s.usersCreatedMonthly.map(p => p.value), 'Comptes créés', false);
    this.bars('intents', s.aiByIntent.map(p => INTENT_LABELS[p.label] || p.label), s.aiByIntent.map(p => p.value), 'Requêtes', true);
  }

  private baseOptions(horizontal: boolean, showLegend: boolean): ChartConfiguration['options'] {
    const valueAxis = {
      beginAtZero: true,
      border: { display: false },
      grid: { color: C.grid, lineWidth: 1, drawTicks: false },
      ticks: { color: C.muted, font: { size: 11 }, padding: 6, precision: 0 }
    };
    const catAxis = {
      border: { display: true, color: C.axis },
      grid: { display: false },
      ticks: { color: C.ink2, font: { size: 11 }, padding: 6, autoSkip: true, maxRotation: 0 }
    };
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 400 },
      indexAxis: horizontal ? 'y' : 'x',
      interaction: { mode: 'index', intersect: false },
      layout: { padding: { top: 4, right: 8 } },
      plugins: {
        legend: {
          display: showLegend,
          position: 'top',
          align: 'start',
          labels: { color: C.ink2, usePointStyle: true, pointStyle: 'rectRounded', boxWidth: 10, boxHeight: 10, padding: 14, font: { size: 12 } }
        },
        tooltip: {
          backgroundColor: '#0f172a',
          titleColor: '#ffffff',
          bodyColor: '#e2e8f0',
          padding: 10,
          cornerRadius: 8,
          displayColors: true,
          boxPadding: 4,
          titleFont: { weight: 'bold' }
        }
      },
      scales: horizontal ? { x: valueAxis, y: catAxis } : { x: catAxis, y: valueAxis }
    } as ChartConfiguration['options'];
  }

  private line(key: string, labels: string[], series: { label: string; data: number[]; color: string }[]): void {
    const el = this.canvas(key);
    if (!el) return;
    this.charts.push(new Chart(el, {
      type: 'line',
      data: {
        labels,
        datasets: series.map(s => ({
          label: s.label,
          data: s.data,
          borderColor: s.color,
          backgroundColor: s.color,
          borderWidth: 2,
          // Comptes journaliers : segments droits (aucun lissage qui inventerait des valeurs entre deux jours)
          tension: 0,
          pointRadius: 2,
          pointHoverRadius: 5,
          pointHoverBorderWidth: 2,
          pointHoverBorderColor: C.surface,
          fill: false
        }))
      },
      options: this.baseOptions(false, series.length > 1)
    }));
  }

  private bars(key: string, labels: string[], data: number[], seriesLabel: string, horizontal: boolean): void {
    const el = this.canvas(key);
    if (!el) return;
    this.charts.push(new Chart(el, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: seriesLabel,
          data,
          backgroundColor: C.s1,
          hoverBackgroundColor: '#1c5cab',
          borderRadius: { topLeft: horizontal ? 0 : 4, topRight: 4, bottomRight: horizontal ? 4 : 0, bottomLeft: 0 },
          borderSkipped: 'start',
          maxBarThickness: horizontal ? 16 : 22,
          categoryPercentage: 0.75,
          barPercentage: 0.9
        }]
      },
      options: this.baseOptions(horizontal, false)
    }));
  }

  private monthLabel(ym: string): string {
    const [y, m] = ym.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('fr-FR', { month: 'short', year: '2-digit' });
  }

  // ── Vues tableau (accessibilité) ─────────────────────────────
  private buildTables(s: Stats): void {
    const pts = (rows: Point[], h: string[], map?: (l: string) => string): TableView =>
      ({ headers: h, rows: rows.map(p => [map ? map(p.label) : p.label, p.value]) });
    this.tables = {
      traffic: { headers: ['Date', 'Vols', 'Retardés', 'Annulés'], rows: s.flightsDaily.map(d => [d.date, d.total, d.delayed, d.cancelled]) },
      activity: { headers: ['Date', 'Messages', 'Requêtes SkyAI'], rows: s.messagesDaily.map((p, i) => [p.label, p.value, s.aiDaily[i]?.value ?? 0]) },
      status: pts(s.flightsByStatus, ['Statut', 'Vols'], l => STATUS_LABELS[l] || l),
      hours: pts(s.flightsByHour, ['Heure', 'Vols']),
      airlines: pts(s.flightsByAirline, ['Compagnie', 'Vols']),
      roles: pts(s.usersByRole, ['Rôle', 'Agents'], l => ROLE_LABELS[l] || l),
      monthly: pts(s.usersCreatedMonthly, ['Mois', 'Comptes créés']),
      intents: pts(s.aiByIntent, ['Intention', 'Requêtes'], l => INTENT_LABELS[l] || l)
    };
  }
}
