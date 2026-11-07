import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { PreferencesService, TIMEZONES, UserPreferences } from '../../core/preferences/preferences.service';
import { AlertsService } from '../../core/preferences/alerts.service';
import { TranslatePipe } from '../../core/preferences/pipes';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class SettingsComponent implements OnInit, OnDestroy {

  readonly timezones = TIMEZONES;
  readonly timeouts = [15, 30, 60, 120, 0];

  prefs!: UserPreferences;
  status: 'idle' | 'saving' | 'saved' | 'error' = 'idle';
  error = '';
  now = new Date();
  permission: NotificationPermission | 'unsupported' = 'default';
  userEmail = '';

  private clock: any;
  private statusTimer: any;
  private sub = new Subscription();

  constructor(
    public p: PreferencesService,
    private alerts: AlertsService,
    private auth: AuthService
  ) {}

  ngOnInit(): void {
    this.userEmail = this.auth.getCurrentUser()?.email || '';
    this.permission = this.alerts.permission;
    this.sub.add(this.p.prefs$.subscribe(v => this.prefs = { ...v }));
    this.p.load().subscribe({ error: () => {} });
    this.clock = setInterval(() => this.now = new Date(), 1000);
  }

  ngOnDestroy(): void {
    clearInterval(this.clock);
    clearTimeout(this.statusTimer);
    this.sub.unsubscribe();
  }

  /** Aperçu de l'heure dans un fuseau donné */
  timeIn(tz: string): string {
    try {
      return new Intl.DateTimeFormat(this.p.locale, { timeZone: tz, hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(this.now);
    } catch { return '--:--'; }
  }

  dateIn(tz: string): string {
    try {
      return new Intl.DateTimeFormat(this.p.locale, { timeZone: tz, weekday: 'long', day: 'numeric', month: 'long' }).format(this.now);
    } catch { return ''; }
  }

  timeoutLabel(m: number): string {
    if (m === 0) return this.p.t('settings.never');
    if (m < 60) return `${m} ${this.p.t('settings.minutes')}`;
    const h = m / 60;
    return `${h} ${h > 1 ? this.p.t('settings.hours') : this.p.t('settings.hour')}`;
  }

  // ── Enregistrement automatique ─────────────────────────
  async update(patch: Partial<UserPreferences>): Promise<void> {
    // Notifications navigateur : demander l'autorisation au moment de l'activation
    if (patch.browserNotifications) {
      this.permission = await this.alerts.requestPermission();
    }
    const previous = { ...this.p.value };
    this.status = 'saving';
    this.error = '';
    clearTimeout(this.statusTimer);
    this.p.save(patch).subscribe({
      next: () => {
        this.status = 'saved';
        if (patch.sessionTimeoutMinutes !== undefined) this.alerts.resetIdle();
        this.statusTimer = setTimeout(() => this.status = 'idle', 2500);
      },
      error: err => {
        this.p.restore(previous);
        this.status = 'error';
        this.error = err?.error?.message || this.p.t('common.error');
      }
    });
  }

  testBrowser(): void {
    this.alerts.requestPermission().then(perm => {
      this.permission = perm;
      if (perm === 'granted') this.alerts.notify(this.p.t('settings.testTitle'), this.p.t('settings.testBody'), undefined, true);
    });
  }

  testSound(): void {
    this.alerts.playSound(true);
  }
}
