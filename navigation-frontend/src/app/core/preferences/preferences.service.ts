import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Lang, TRANSLATIONS } from '../i18n/translations';

export interface UserPreferences {
  language: Lang;
  timezone: string;
  emailNotifications: boolean;
  browserNotifications: boolean;
  notificationSounds: boolean;
  sessionTimeoutMinutes: number;
  twoFactorEnabled: boolean;
  mailConfigured?: boolean;
}

const STORAGE_KEY = 'nav_prefs';

export const DEFAULT_PREFS: UserPreferences = {
  language: 'fr',
  timezone: 'Africa/Tunis',
  emailNotifications: true,
  browserNotifications: true,
  notificationSounds: true,
  sessionTimeoutMinutes: 30,
  twoFactorEnabled: false
};

/** Fuseaux proposés (aviation : Tunisie, Europe, UTC/Zulu) */
export const TIMEZONES: { id: string; label: string }[] = [
  { id: 'Africa/Tunis', label: 'Tunis (CET)' },
  { id: 'UTC', label: 'UTC / Zulu' },
  { id: 'Europe/Paris', label: 'Paris' },
  { id: 'Europe/Zurich', label: 'Zurich' },
  { id: 'Europe/London', label: 'Londres / London' },
  { id: 'Africa/Algiers', label: 'Alger / Algiers' },
  { id: 'Africa/Casablanca', label: 'Casablanca' },
  { id: 'Europe/Rome', label: 'Rome' },
  { id: 'Asia/Dubai', label: 'Dubaï / Dubai' },
  { id: 'America/New_York', label: 'New York' }
];

/**
 * Préférences de l'utilisateur : chargées depuis le serveur, mises en cache localement
 * (application immédiate au démarrage), et appliquées à toute l'interface.
 */
@Injectable({ providedIn: 'root' })
export class PreferencesService {

  private readonly API = `${environment.apiUrl}/me/preferences`;
  private prefsSubject = new BehaviorSubject<UserPreferences>(this.loadCached());
  prefs$ = this.prefsSubject.asObservable();

  constructor(private http: HttpClient) {
    this.applyDocumentLang(this.prefsSubject.value.language);
  }

  get value(): UserPreferences { return this.prefsSubject.value; }
  get lang(): Lang { return this.value.language; }
  get timezone(): string { return this.value.timezone; }
  get locale(): string { return this.lang === 'en' ? 'en-GB' : 'fr-FR'; }

  load(): Observable<UserPreferences> {
    return this.http.get<UserPreferences>(this.API).pipe(tap(p => this.set(p)));
  }

  save(patch: Partial<UserPreferences>): Observable<UserPreferences> {
    // Application immédiate (optimiste), le serveur confirme
    this.set({ ...this.value, ...patch });
    return this.http.put<UserPreferences>(this.API, patch).pipe(tap(p => this.set(p)));
  }

  /** Revient à la valeur précédente si le serveur refuse */
  restore(previous: UserPreferences): void { this.set(previous); }

  reset(): void {
    try { localStorage.removeItem(STORAGE_KEY); } catch {}
    this.prefsSubject.next({ ...DEFAULT_PREFS, language: this.value.language });
  }

  // ── Traduction ─────────────────────────────────────────
  t(key: string, params?: Record<string, string | number>): string {
    let s = TRANSLATIONS[this.lang]?.[key] ?? TRANSLATIONS.fr[key] ?? key;
    if (params) Object.entries(params).forEach(([k, v]) => s = s.replace(`{${k}}`, String(v)));
    return s;
  }

  // ── Dates dans le fuseau choisi ────────────────────────
  formatDate(value: string | number | Date | null | undefined, style: 'time' | 'datetime' | 'date' | 'short' = 'datetime'): string {
    if (value === null || value === undefined || value === '') return '';
    const d = value instanceof Date ? value : new Date(value);
    if (isNaN(d.getTime())) return '';
    const opts: Intl.DateTimeFormatOptions = { timeZone: this.timezone };
    if (style === 'time') Object.assign(opts, { hour: '2-digit', minute: '2-digit' });
    if (style === 'date') Object.assign(opts, { day: '2-digit', month: 'short', year: 'numeric' });
    if (style === 'short') Object.assign(opts, { day: '2-digit', month: '2-digit' });
    if (style === 'datetime') Object.assign(opts, { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    try {
      return new Intl.DateTimeFormat(this.locale, opts).format(d);
    } catch {
      return d.toLocaleString(this.locale);
    }
  }

  /** Clé de jour (aaaa-mm-jj) dans le fuseau choisi — pour regrouper par jour */
  dayKey(value: string | Date): string {
    const d = value instanceof Date ? value : new Date(value);
    return new Intl.DateTimeFormat('en-CA', { timeZone: this.timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(d);
  }

  relative(value: string | Date): string {
    const diff = Math.max(0, Date.now() - new Date(value).getTime()) / 60000;
    if (diff < 1) return this.t('common.ago.now');
    if (diff < 60) return this.t('common.ago.min', { n: Math.floor(diff) });
    if (diff < 1440) return this.t('common.ago.h', { n: Math.floor(diff / 60) });
    return this.t('common.ago.d', { n: Math.floor(diff / 1440) });
  }

  // ── Interne ────────────────────────────────────────────
  private set(p: UserPreferences): void {
    const merged = { ...DEFAULT_PREFS, ...p };
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(merged)); } catch {}
    this.applyDocumentLang(merged.language);
    this.prefsSubject.next(merged);
  }

  private loadCached(): UserPreferences {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? { ...DEFAULT_PREFS, ...JSON.parse(raw) } : { ...DEFAULT_PREFS };
    } catch {
      return { ...DEFAULT_PREFS };
    }
  }

  private applyDocumentLang(lang: Lang): void {
    try { document.documentElement.lang = lang; } catch {}
  }
}
