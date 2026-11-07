import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PreferencesService } from '../../../core/preferences/preferences.service';
import { TranslatePipe, AgoPipe, AppDatePipe } from '../../../core/preferences/pipes';

type Period = 'today' | 'week' | 'month';

interface DashboardData {
  period: Period;
  generatedAt: string;
  users: { total: number; active: number; newInPeriod: number; newPrev: number; trendPct: number | null };
  flights: { total: number; activeNow: number; inPeriod: number; prevPeriod: number; trendPct: number | null; byStatus: Record<string, number> };
  messages: { inPeriod: number; prevPeriod: number; trendPct: number | null };
  aiQueries: { inPeriod: number };
  roles: Record<string, number>;
  system: {
    servicesUp: number; servicesTotal: number; availabilityPct: number; status: 'OK' | 'DEGRADED' | 'DOWN';
    uptimeSeconds: number; connectedUsers: number;
    checks: { key: string; label: string; ok: boolean; detail: string }[];
  };
}

interface Activity { id: number; title: string; description?: string; type: string; category?: string; timestamp: string; }

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, TranslatePipe, AgoPipe, AppDatePipe],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.scss']
})
export class AdminDashboardComponent implements OnInit, OnDestroy {

  data: DashboardData | null = null;
  activities: Activity[] = [];
  period: Period = 'today';
  isLoading = false;
  error = '';
  showSystem = false;

  readonly roleOrder = ['ADMIN', 'SUPERVISEUR', 'ATC', 'PISTE', 'AIS', 'RH_FORMATION', 'QUALITE', 'CONSULTATION'];
  readonly periods: Period[] = ['today', 'week', 'month'];

  private refreshTimer: any;

  constructor(private router: Router, private http: HttpClient, public prefs: PreferencesService) {}

  ngOnInit(): void {
    this.loadDashboardData();
    // Rafraîchissement automatique toutes les 60 s
    this.refreshTimer = setInterval(() => this.loadDashboardData(true), 60000);
  }

  ngOnDestroy(): void { clearInterval(this.refreshTimer); }

  loadDashboardData(silent = false): void {
    if (!silent) this.isLoading = true;
    this.error = '';
    this.http.get<DashboardData>(`${environment.apiUrl}/admin/dashboard`, { params: { period: this.period } }).subscribe({
      next: d => { this.data = d; this.isLoading = false; },
      error: err => {
        this.isLoading = false;
        this.error = err?.error?.message || this.prefs.t('common.error');
      }
    });
    this.http.get<Activity[]>(`${environment.apiUrl}/activities`, { params: { limit: 6 } }).subscribe({
      next: a => this.activities = a.slice(0, 6),
      error: () => this.activities = []
    });
  }

  setPeriod(p: Period): void {
    if (p === this.period) return;
    this.period = p;
    this.loadDashboardData();
  }

  refreshData(): void { this.loadDashboardData(); }

  // ── Valeurs dérivées ─────────────────────────────────
  periodLabel(p: Period): string {
    return this.prefs.t(p === 'today' ? 'dash.today' : p === 'week' ? 'dash.week' : 'dash.month');
  }

  trendClass(v: number | null | undefined): string {
    if (v === null || v === undefined || v === 0) return 'neutral';
    return v > 0 ? 'positive' : 'negative';
  }

  trendText(v: number | null | undefined): string {
    if (v === null || v === undefined) return this.prefs.t('dash.noPrev');
    const sign = v > 0 ? '+' : '';
    return `${sign}${v.toLocaleString(this.prefs.locale)} % ${this.prefs.t('dash.vsPrev')}`;
  }

  get totalRoles(): number {
    return this.data ? Object.values(this.data.roles).reduce((s, n) => s + n, 0) : 0;
  }

  get maxRole(): number {
    return this.data ? Math.max(1, ...Object.values(this.data.roles)) : 1;
  }

  roleCount(r: string): number { return this.data?.roles?.[r] ?? 0; }

  /** Barre relative au rôle le plus représenté (lisible même avec peu d'agents) */
  rolePct(r: string): number { return Math.round((this.roleCount(r) / this.maxRole) * 100); }

  status(s: string): number { return this.data?.flights?.byStatus?.[s] ?? 0; }

  get inFlight(): number { return this.status('EN_VOL') + this.status('DECOLLAGE') + this.status('EN_APPROCHE'); }
  get arrived(): number { return this.status('ARRIVE') + this.status('ATTERRI') + this.status('AU_SOL'); }

  get systemLabel(): string {
    const s = this.data?.system?.status;
    return this.prefs.t(s === 'OK' ? 'dash.online' : s === 'DEGRADED' ? 'dash.degraded' : 'dash.down');
  }

  get uptime(): string {
    const sec = this.data?.system?.uptimeSeconds ?? 0;
    const d = Math.floor(sec / 86400), h = Math.floor((sec % 86400) / 3600), m = Math.floor((sec % 3600) / 60);
    if (d > 0) return `${d} j ${h} h`;
    if (h > 0) return `${h} h ${m} min`;
    return `${m} min`;
  }

  activityIcon(a: Activity): 'success' | 'warning' | 'error' | 'info' {
    return (['success', 'warning', 'error'].includes(a.type) ? a.type : 'info') as any;
  }

  viewAllActivities(): void { this.router.navigate(['/dashboard/activities']); }

  navigateTo(route: string): void {
    const routeMap: Record<string, string> = {
      'users': '/dashboard/users',
      'flights': '/dashboard/flights',
      'chat': '/dashboard/chat',
      'statistics': '/dashboard/statistics',
      'settings': '/dashboard/settings',
      'activities': '/dashboard/activities'
    };
    this.router.navigate([routeMap[route] || `/dashboard/${route}`]);
  }
}
