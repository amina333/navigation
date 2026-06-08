import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { FlightService } from '../../../services/flight.service';
import { Flight, FlightStatus, FlightType, FlightRequest } from '../../../models/flight.model';
import { NotificationService } from '../../../services/notification.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-flight-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, DatePipe],
  templateUrl: './flight-dashboard.component.html',
  styleUrls: ['./flight-dashboard.component.scss']
})
export class FlightDashboardComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  // Form and modal state
  flightForm!: FormGroup;
  showFlightModal = false;
  showDetailsModal = false;       // ← modal Détails
  editingFlight: Flight | null = null;
  selectedFlight: Flight | null = null;
  selectedFlightEvents: any[] = []; // Section 8.1 UML : historique événements
  saving = false;
  errorMessage = '';              // ← affichage erreurs backend

  // Data
  flights: Flight[] = [];
  filteredFlights: Flight[] = [];
  loading = false;

  // Statistics
  totalFlights = 0;
  activeFlights = 0;
  scheduledFlights = 0;
  delayedFlights = 0;
  landedFlights = 0;

  // Filters
  searchTerm = '';
  statusFilter: FlightStatus | '' = '';
  typeFilter: FlightType | '' = '';
  airlineFilter = '';

  // Sorting
  sortField = 'flightNumber';
  sortDirection: 'asc' | 'desc' = 'asc';

  // View mode
  viewMode: 'cards' | 'table' = 'cards';

  constructor(
    private flightService: FlightService,
    private fb: FormBuilder,
    private notificationService: NotificationService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.initializeForm();
    this.loadData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeForm(): void {
    this.flightForm = this.fb.group({
      flightNumber: ['', Validators.required],
      airline: ['', Validators.required],
      type: ['COMMERCIAL' as FlightType, Validators.required],
      aircraft: this.fb.group({
        type: ['', Validators.required],
        registration: ['', Validators.required]
      }),
      origin: ['', Validators.required],
      destination: ['', Validators.required],
      scheduledTime: ['', Validators.required],
      status: ['PROGRAMME' as FlightStatus, Validators.required],
      runway: [''],
      gate: ['']
    });
  }

  private loadData(): void {
    this.loading = true;
    this.flightService.getAllFlights().pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (flights) => {
        this.flights = flights;
        this.filteredFlights = [...flights];
        this.calculateStatistics();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading flights:', error);
        this.loading = false;
      }
    });
  }

  private calculateStatistics(): void {
    this.totalFlights = this.flights.length;
    this.activeFlights = this.flights.filter(f => f.status === FlightStatus.EN_VOL).length;
    this.scheduledFlights = this.flights.filter(f => f.status === FlightStatus.PROGRAMME).length;
    this.delayedFlights = this.flights.filter(f => f.status === FlightStatus.RETARDE).length;
    this.landedFlights = this.flights.filter(f => f.status === FlightStatus.ARRIVE).length;
  }

  filterFlights(): void {
    let filtered = [...this.flights];

    // Search filter
    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      filtered = filtered.filter(flight =>
        flight.flightNumber.toLowerCase().includes(term) ||
        flight.airline.toLowerCase().includes(term) ||
        (flight.origin || '').toLowerCase().includes(term) ||
        (flight.destination || '').toLowerCase().includes(term) ||
        (flight.aircraftType || '').toLowerCase().includes(term)
      );
    }

    // Status filter
    if (this.statusFilter) {
      filtered = filtered.filter(flight => flight.status === this.statusFilter);
    }

    // Type filter — backend uses flightType, frontend form uses type
    if (this.typeFilter) {
      filtered = filtered.filter(flight =>
        (flight.flightType || flight.type) === this.typeFilter
      );
    }

    // Airline filter
    if (this.airlineFilter) {
      filtered = filtered.filter(flight => 
        flight.airline.toLowerCase().includes(this.airlineFilter.toLowerCase())
      );
    }

    this.filteredFlights = filtered;
  }

  sortFlights(field: string): void {
    if (this.sortField === field) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortField = field;
      this.sortDirection = 'asc';
    }

    this.filteredFlights.sort((a: any, b: any) => {
      let aVal = this.getNestedValue(a, field);
      let bVal = this.getNestedValue(b, field);

      if (typeof aVal === 'string') {
        aVal = aVal.toLowerCase();
        bVal = bVal.toLowerCase();
      }

      if (aVal < bVal) return this.sortDirection === 'asc' ? -1 : 1;
      if (aVal > bVal) return this.sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }

  private getNestedValue(obj: any, path: string): any {
    return path.split('.').reduce((current, key) => current?.[key], obj);
  }

  openCreateFlightModal(): void {
    this.editingFlight = null;
    this.flightForm.reset({
      type: 'COMMERCIAL',
      status: 'PROGRAMME'
    });
    this.showFlightModal = true;
  }

  closeFlightModal(): void {
    this.showFlightModal = false;
    this.editingFlight = null;
    this.errorMessage = '';
    this.saving = false;
    this.flightForm.reset();
  }

  editFlight(flight: Flight): void {
    this.editingFlight = flight;
    // Patch les champs plats
    this.flightForm.patchValue({
      flightNumber: flight.flightNumber,
      airline: flight.airline,
      type: flight.flightType || flight.type || 'COMMERCIAL',
      origin: flight.origin,
      destination: flight.destination,
      scheduledTime: flight.scheduledTime,
      status: flight.status,
      runway: flight.runway || '',
      gate: flight.gate || ''
    });
    // Patch le sous-groupe aircraft séparément
    this.flightForm.get('aircraft')?.patchValue({
      type: flight.aircraftType || '',
      registration: flight.aircraftRegistration || ''
    });
    this.showFlightModal = true;
  }

  viewFlightDetails(flight: Flight): void {
    this.selectedFlight = flight;
    this.selectedFlightEvents = [];
    this.showDetailsModal = true;
    // Section 8.1 UML : charger l'historique des événements du vol
    this.http.get<any[]>(`${environment.apiUrl}/flights/${flight.id}/events`)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: events  => this.selectedFlightEvents = events,
        error: ()     => this.selectedFlightEvents = []
      });
  }

  closeDetailsModal(): void {
    this.showDetailsModal = false;
    this.selectedFlight = null;
    this.selectedFlightEvents = [];
  }

  deleteFlight(flight: Flight): void {
    if (confirm(`Êtes-vous sûr de vouloir supprimer le vol ${flight.flightNumber}?`)) {
      this.flightService.deleteFlight(flight.id).pipe(
        takeUntil(this.destroy$)
      ).subscribe({
        next: () => {
          this.loadData();
          this.notificationService.addNotification(
            'Vol supprimé',
            `Le vol ${flight.flightNumber} a été supprimé avec succès`,
            'SYSTEM' as any
          );
        },
        error: (error) => {
          console.error('Error deleting flight:', error);
        }
      });
    }
  }

  saveFlight(): void {
    if (this.flightForm.invalid) return;

    this.saving = true;
    const formValue = this.flightForm.value;

    // Convertir la datetime-local en ISO-8601 attendu par le backend
    const rawTime: string = formValue.scheduledTime || '';
    const isoTime = rawTime.length === 16 ? rawTime + ':00' : rawTime; // "2026-05-07T11:57" → "2026-05-07T11:57:00"

    const flightRequest: FlightRequest = {
      flightNumber:        formValue.flightNumber,
      airline:             formValue.airline,
      type:                formValue.type as FlightType,      // alias local
      flightType:          formValue.type as FlightType,      // champ attendu par le backend
      aircraftType:        formValue.aircraft?.type || '',
      aircraftRegistration:formValue.aircraft?.registration || '',
      origin:              formValue.origin,
      destination:         formValue.destination,
      scheduledTime:       isoTime,
      status:              formValue.status || 'PROGRAMME',
      runway:              formValue.runway || '',
      gate:                formValue.gate   || ''
    } as any;

    if (this.editingFlight) {
      // Update existing flight
      this.flightService.updateFlight(this.editingFlight.id, flightRequest).pipe(
        takeUntil(this.destroy$)
      ).subscribe({
        next: () => {
          this.loadData();
          this.closeFlightModal();
          this.notificationService.addNotification(
            'Vol mis à jour',
            `Le vol ${formValue.flightNumber} a été mis à jour avec succès`,
            'SYSTEM' as any
          );
        },
        error: (error) => {
          this.errorMessage = this.parseErrorMessage(error);
          console.error('Error updating flight:', error);
          this.saving = false;
        }
      });
    } else {
      // Create new flight
      this.errorMessage = '';
      this.flightService.createFlight(flightRequest).pipe(
        takeUntil(this.destroy$)
      ).subscribe({
        next: () => {
          this.loadData();
          this.closeFlightModal();
          this.notificationService.addNotification(
            'Vol créé',
            `Le vol ${formValue.flightNumber} a été créé avec succès`,
            'SYSTEM' as any
          );
        },
        error: (error) => {
          this.errorMessage = this.parseErrorMessage(error);
          console.error('Error creating flight:', error);
          this.saving = false;
        }
      });
    }
  }

  /** Export CSV client-side (données filtrées) */
  exportAsCsv(): void {
    const data = this.filteredFlights.length > 0 ? this.filteredFlights : this.flights;
    if (data.length === 0) return;

    const headers = ['N° Vol','Compagnie','Type Aéronef','Immatriculation','Départ','Arrivée','Type','Statut','Heure Prévue','Piste','Porte','Passagers','Équipage'];
    const rows = data.map(f => [
      f.flightNumber,
      f.airline,
      f.aircraftType || '',
      f.aircraftRegistration || '',
      f.origin,
      f.destination,
      f.flightType || '',
      this.getStatusText(f.status),
      f.scheduledTime ? new Date(f.scheduledTime).toLocaleString('fr-FR') : '',
      f.runway || '',
      f.gate || '',
      f.passengersCount ?? '',
      f.crewCount ?? ''
    ]);

    const bom = '\uFEFF'; // UTF-8 BOM pour Excel
    const csv = bom + [headers, ...rows]
      .map(row => row.map(v => `"${String(v).replace(/"/g, '""')}"`).join(';'))
      .join('\n');

    this.downloadFile(csv, `vols_${new Date().toISOString().slice(0,10)}.csv`, 'text/csv;charset=utf-8;');
  }

  /** Export Excel (format HTML-XLS) client-side */
  exportAsExcel(): void {
    const data = this.filteredFlights.length > 0 ? this.filteredFlights : this.flights;
    if (data.length === 0) return;

    const headers = ['N° Vol','Compagnie','Type Aéronef','Immat.','Départ','Arrivée','Type','Statut','Heure Prévue','Piste','Porte','PAX','Équipage'];

    const headerRow = headers.map(h =>
      `<th style="background:#1d4ed8;color:#fff;padding:8px 12px;font-size:12px;border:1px solid #1e40af;white-space:nowrap;">${h}</th>`
    ).join('');

    const dataRows = data.map((f, i) => {
      const bg = i % 2 === 0 ? '#ffffff' : '#f0f7ff';
      const cells = [
        `<b>${f.flightNumber}</b>`,
        f.airline,
        f.aircraftType || '—',
        f.aircraftRegistration || '—',
        f.origin,
        f.destination,
        f.flightType || '—',
        this.getStatusText(f.status),
        f.scheduledTime ? new Date(f.scheduledTime).toLocaleString('fr-FR') : '—',
        f.runway || '—',
        f.gate || '—',
        f.passengersCount ?? '—',
        f.crewCount ?? '—'
      ].map(v => `<td style="padding:6px 12px;border:1px solid #e2e8f0;font-size:12px;">${v}</td>`).join('');
      return `<tr style="background:${bg};">${cells}</tr>`;
    }).join('');

    const html = `
      <html xmlns:o="urn:schemas-microsoft-com:office:office"
            xmlns:x="urn:schemas-microsoft-com:office:excel"
            xmlns="http://www.w3.org/TR/REC-html40">
      <head><meta charset="UTF-8">
        <style>table{border-collapse:collapse;font-family:Arial,sans-serif;}</style>
      </head>
      <body>
        <h2 style="color:#1d4ed8;font-family:Arial;">📋 Rapport des Vols — SkyGuide</h2>
        <p style="color:#64748b;font-size:12px;">Exporté le ${new Date().toLocaleString('fr-FR')} · ${data.length} vol(s)</p>
        <table><thead><tr>${headerRow}</tr></thead><tbody>${dataRows}</tbody></table>
      </body></html>`;

    this.downloadFile(html, `vols_${new Date().toISOString().slice(0,10)}.xls`, 'application/vnd.ms-excel;charset=utf-8;');
  }

  private downloadFile(content: string, filename: string, mimeType: string): void {
    const blob = new Blob([content], { type: mimeType });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }



  toggleMapView(): void {
    console.log('Toggling map view...');
  }

  /** Traduit les erreurs backend en messages lisibles pour l'utilisateur */
  private parseErrorMessage(error: any): string {
    const raw: string = error?.error?.message || error?.message || '';

    // Erreurs de désérialisation JSON (enum invalide, format date, etc.)
    if (raw.includes('JSON parse error') || raw.includes('Cannot deserialize')) {
      if (raw.includes('FlightType')) {
        return 'Type de vol invalide. Veuillez sélectionner "Départ" ou "Arrivée".';
      }
      if (raw.includes('FlightStatus')) {
        return 'Statut de vol invalide. Veuillez choisir un statut dans la liste.';
      }
      if (raw.includes('LocalDateTime') || raw.includes('date')) {
        return 'Format de date/heure invalide. Vérifiez le champ "Heure Prévue".';
      }
      return 'Données invalides. Vérifiez les champs du formulaire.';
    }

    // Contrainte d'unicité
    if (raw.includes('existe déjà') || raw.includes('duplicate') || raw.includes('already exists')) {
      return 'Un vol avec ce numéro existe déjà. Utilisez un numéro différent.';
    }

    // Validation @NotBlank / @NotNull
    if (raw.includes('obligatoire') || raw.includes('must not be blank') || raw.includes('must not be null')) {
      return 'Certains champs obligatoires sont manquants. Vérifiez le formulaire.';
    }

    // Accès refusé
    if (error?.status === 403) {
      return 'Accès refusé. Vous n\'avez pas les droits pour effectuer cette action.';
    }

    // Serveur indisponible
    if (error?.status === 0 || error?.status === 503) {
      return 'Serveur indisponible. Vérifiez que le backend est démarré sur le port 8080.';
    }

    // Erreur générique avec message lisible
    if (raw && raw.length < 200 && !raw.includes('com.air') && !raw.includes('java.')) {
      return raw;
    }

    return 'Une erreur est survenue lors de l\'enregistrement. Veuillez réessayer.';
  }

  // Helper methods
  get uniqueAirlines(): string[] {
    return [...new Set(this.flights.map(f => f.airline).filter(Boolean))];
  }

  isDelayed(flight: Flight): boolean {
    return flight.status === FlightStatus.RETARDE;
  }

  getDelayMinutes(flight: Flight): number {
    if (!flight.actualTime || !flight.scheduledTime) return 0;
    const scheduled = new Date(flight.scheduledTime).getTime();
    const actual = new Date(flight.actualTime).getTime();
    return Math.round((actual - scheduled) / (1000 * 60));
  }

  getStatusClass(status: FlightStatus): string {
    const statusClasses: { [key in FlightStatus]: string } = {
      [FlightStatus.PROGRAMME]: 'scheduled',
      [FlightStatus.EN_EMBARQUEMENT]: 'boarding',
      [FlightStatus.DECOLLAGE]: 'takeoff',
      [FlightStatus.EN_VOL]: 'active',
      [FlightStatus.EN_APPROCHE]: 'approaching',
      [FlightStatus.ARRIVE]: 'landed',
      [FlightStatus.AU_SOL]: 'ground',
      [FlightStatus.RETARDE]: 'delayed',
      [FlightStatus.ANNULE]: 'cancelled',
      [FlightStatus.DIVERTE]: 'diverted'
    };
    return statusClasses[status] || '';
  }

  getSortIcon(field: string): string {
    if (this.sortField !== field) return '↕️';
    return this.sortDirection === 'asc' ? '↑' : '↓';
  }

  // Additional methods required by HTML
  refreshFlights(): void {
    this.loadData();
  }

  getAirportName(code: any): string {
    // Handle both string code and Airport object
    if (typeof code === 'string') {
      return code;
    }
    if (code && code.name) {
      return code.name;
    }
    return code || '';
  }

  formatTime(date: any): string {
    if (!date) return '';
    const d = new Date(date);
    return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  getStatusText(status: string): string {
    const statusMap: { [key: string]: string } = {
      'PROGRAMME': 'Programmé',
      'EN_EMBARQUEMENT': 'Embarquement',
      'DECOLLAGE': 'Décollage',
      'EN_VOL': 'En vol',
      'EN_APPROCHE': 'En approche',
      'ARRIVE': 'ARRIVE',
      'AU_SOL': 'Au sol',
      'RETARDE': 'Retardé',
      'ANNULE': 'Annulé',
      'DIVERTE': 'Détourné'
    };
    return statusMap[status] || status;
  }

  updateFlightStatus(flight: Flight): void {
    // Ouvre le modal d'édition avec ce vol pré-rempli
    this.editFlight(flight);
  }

  canUpdateStatus(): boolean {
    // Check if current user has permission to update flight status
    return true; // Simplified for now
  }
}
