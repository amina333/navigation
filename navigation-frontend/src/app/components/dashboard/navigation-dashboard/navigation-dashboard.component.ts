import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { FlightService } from '../../../services/flight.service';
import { AuthService } from '../../../services/auth.service';
import { RoleService } from '../../../services/role.service';
import { Flight, FlightStatus } from '../../../models/flight.model';
import { Subject } from 'rxjs';
import { takeUntil, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

@Component({
  selector: 'app-navigation-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './navigation-dashboard.component.html',
  styleUrls: ['./navigation-dashboard.component.scss']
})
export class NavigationDashboardComponent implements OnInit, OnDestroy {

  private destroy$ = new Subject<void>();

  currentUser: any;
  userRole = '';
  currentTime = new Date();

  // ── Live data from backend ────────────────────────────────────────
  allFlights: Flight[] = [];
  loading = false;

  // ── KPI computed ──────────────────────────────────────────────────
  get activeFlights()    { return this.allFlights.filter(f => f.status === 'EN_VOL').length; }
  get scheduledFlights() { return this.allFlights.filter(f => f.status === 'PROGRAMME').length; }
  get delayedFlights()   { return this.allFlights.filter(f => f.status === 'RETARDE').length; }
  get landedFlights()    { return this.allFlights.filter(f => f.status === 'ARRIVE').length; }

  // ── Static operational data ────────────────────────────────────────
  runways = [
    { id: 'RWY 09/27', status: 'active',   wind: '12kt', visibility: 'CAVOK' },
    { id: 'RWY 01/19', status: 'active',   wind: '08kt', visibility: '8km'   },
    { id: 'RWY 14/32', status: 'closed',   wind: '—',    visibility: '—'     },
  ];

  weatherData = {
    condition: 'Dégagé',
    temperature: '22°C',
    wind: '120° / 12kt',
    visibility: '10km+',
    qnh: '1015 hPa',
    ceiling: 'NSC',
    icon: '☀️'
  };

  alerts: { id: number; title: string; message: string; severity: 'high' | 'medium' | 'low'; time: string }[] = [
    { id: 1, title: 'Vent fort sur piste 27',     message: 'Rafales jusqu\'à 25 kt signalées', severity: 'medium', time: 'Il y a 5 min'  },
    { id: 2, title: 'Maintenance piste 14/32',    message: 'Fermeture effective depuis 14h00',  severity: 'low',    time: 'Il y a 15 min' },
    { id: 3, title: 'Trafic dense en approche',   message: '6 aéronefs dans le circuit',       severity: 'high',   time: 'Il y a 2 min'  },
  ];

  recentCommunications = [
    { callsign: 'TN123', message: 'Demande autorisation atterrissage piste 09', time: '14:32', urgent: true  },
    { callsign: 'AF456', message: 'En approche finale, conditions normales',    time: '14:28', urgent: false },
    { callsign: 'LH789', message: 'Confirmation position — holding sector 3',  time: '14:25', urgent: false },
    { callsign: 'TU202', message: 'Demande pushback porte A12',                 time: '14:21', urgent: false },
  ];

  quickActions = [
    { label: 'Voir les vols',    icon: 'plane',  route: '/dashboard/flights',  color: '#3b82f6' },
    { label: 'Communications',   icon: 'chat',   route: '/dashboard/chat',     color: '#8b5cf6' },
    { label: 'Notifications',    icon: 'bell',   route: '/dashboard/notifications', color: '#f59e0b' },
    { label: 'Activités',        icon: 'clock',  route: '/dashboard/activities', color: '#10b981' },
  ];

  constructor(
    private router: Router,
    private flightService: FlightService,
    private authService: AuthService,
    private roleService: RoleService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getCurrentUser();
    this.userRole    = this.roleService.getHighestPriorityRole();

    // Tick every second to keep clock live
    setInterval(() => this.currentTime = new Date(), 1000);

    this.loadFlights();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadFlights(): void {
    this.loading = true;
    this.flightService.getAllFlights().pipe(
      takeUntil(this.destroy$),
      catchError(() => of([]))
    ).subscribe(flights => {
      this.allFlights = flights;
      this.loading = false;
    });
  }

  /** Vols à surveiller : en vol + en approche + retardés */
  get watchedFlights(): Flight[] {
    return this.allFlights
      .filter(f => ['EN_VOL', 'EN_APPROCHE', 'RETARDE', 'PROGRAMME'].includes(f.status as string))
      .slice(0, 8);
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = {
      EN_VOL: 'st-active', EN_APPROCHE: 'st-approach',
      PROGRAMME: 'st-scheduled', RETARDE: 'st-delayed',
      ARRIVE: 'st-landed', ANNULE: 'st-cancelled', AU_SOL: 'st-ground'
    };
    return map[status] || 'st-scheduled';
  }

  getStatusText(status: string): string {
    const map: Record<string, string> = {
      PROGRAMME: 'Programmé', EN_EMBARQUEMENT: 'Embarquement', EN_VOL: 'En vol',
      EN_APPROCHE: 'En approche', ARRIVE: 'ARRIVE', AU_SOL: 'Au sol',
      RETARDE: 'Retardé', ANNULE: 'Annulé', DIVERTE: 'Détourné'
    };
    return map[status] || status;
  }

  formatTime(date: any): string {
    if (!date) return '—';
    return new Date(date).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  getRoleLabel(): string {
    const map: Record<string, string> = {
      SUPERVISEUR: 'Superviseur Opérationnel',
      ATC: 'Contrôleur Aérien (ATC)',
      PISTE: 'Agent de Piste',
      AIS: 'Information Aéronautique'
    };
    return map[this.userRole] || this.userRole;
  }

  dismissAlert(id: number): void {
    this.alerts = this.alerts.filter(a => a.id !== id);
  }

  navigateTo(route: string): void {
    this.router.navigate([route]);
  }
}
