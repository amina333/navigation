import { Injectable, NgZone } from '@angular/core';
import { PreferencesService } from './preferences.service';

/**
 * Alertes locales : notifications du navigateur, son, et déconnexion après inactivité.
 * Chaque fonction respecte les préférences de l'utilisateur.
 */
@Injectable({ providedIn: 'root' })
export class AlertsService {

  private audioCtx: AudioContext | null = null;
  private idleTimer: any;
  private idleHandler = () => this.resetIdle();
  private onIdle: (() => void) | null = null;

  constructor(private prefs: PreferencesService, private zone: NgZone) {}

  // ── Notifications du navigateur ───────────────────────
  get browserSupported(): boolean { return typeof window !== 'undefined' && 'Notification' in window; }

  get permission(): NotificationPermission | 'unsupported' {
    return this.browserSupported ? Notification.permission : 'unsupported';
  }

  async requestPermission(): Promise<NotificationPermission | 'unsupported'> {
    if (!this.browserSupported) return 'unsupported';
    if (Notification.permission === 'default') {
      try { return await Notification.requestPermission(); } catch { return Notification.permission; }
    }
    return Notification.permission;
  }

  /** Affiche une notification système si l'utilisateur l'a activée et autorisée */
  notify(title: string, body: string, onClick?: () => void, force = false): void {
    if (!force && !this.prefs.value.browserNotifications) return;
    if (!this.browserSupported || Notification.permission !== 'granted') return;
    try {
      const n = new Notification(title, { body, icon: '/favicon.ico', tag: 'skyguide-' + title });
      n.onclick = () => {
        window.focus();
        n.close();
        if (onClick) this.zone.run(onClick);
      };
      setTimeout(() => n.close(), 8000);
    } catch {}
  }

  // ── Son ────────────────────────────────────────────────
  /** Petit signal à deux notes (Web Audio, aucun fichier requis) */
  playSound(force = false): void {
    if (!force && !this.prefs.value.notificationSounds) return;
    try {
      const Ctx = (window as any).AudioContext || (window as any).webkitAudioContext;
      if (!Ctx) return;
      this.audioCtx = this.audioCtx || new Ctx();
      const ctx = this.audioCtx!;
      const now = ctx.currentTime;
      [880, 1318.5].forEach((freq, i) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.value = freq;
        const t0 = now + i * 0.12;
        gain.gain.setValueAtTime(0.0001, t0);
        gain.gain.exponentialRampToValueAtTime(0.15, t0 + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.25);
        osc.connect(gain).connect(ctx.destination);
        osc.start(t0);
        osc.stop(t0 + 0.3);
      });
    } catch {}
  }

  // ── Déconnexion après inactivité ──────────────────────
  startIdleWatch(onIdle: () => void): void {
    this.onIdle = onIdle;
    this.zone.runOutsideAngular(() => {
      ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'].forEach(ev =>
        window.addEventListener(ev, this.idleHandler, { passive: true }));
    });
    this.resetIdle();
  }

  stopIdleWatch(): void {
    clearTimeout(this.idleTimer);
    ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'].forEach(ev =>
      window.removeEventListener(ev, this.idleHandler));
    this.onIdle = null;
  }

  /** À rappeler quand la durée change */
  resetIdle(): void {
    clearTimeout(this.idleTimer);
    const minutes = this.prefs.value.sessionTimeoutMinutes;
    if (!this.onIdle || !minutes) return;
    this.idleTimer = setTimeout(() => this.zone.run(() => this.onIdle && this.onIdle()), minutes * 60000);
  }
}
