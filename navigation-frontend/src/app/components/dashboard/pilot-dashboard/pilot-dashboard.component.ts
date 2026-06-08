import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { RoleService } from '../../../services/role.service';
import { FlightService } from '../../../services/flight.service';
import { AuthService } from '../../../services/auth.service';
import { Flight } from '../../../models/flight.model';

@Component({
  selector: 'app-pilot-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pilot-dashboard.component.html',
  styleUrls: ['./pilot-dashboard.component.scss']
})
export class PilotDashboardComponent implements OnInit {
  
  currentUser: any;
  currentFlight: any = null;
  myFlights = 0;
  upcomingFlights = 0;
  completedFlights = 0;
  weatherStatus = 'Ciel clair';
  crewMembers = 4;
  
  // Will be loaded from backend
  upcomingFlightsList: Flight[] = [];

  constructor(
    private router: Router,
    private roleService: RoleService,
    private flightService: FlightService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getCurrentUser();
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    // Load pilot's flights from backend
    this.loadPilotFlights();
    
    // Load current flight if any
    this.loadCurrentFlight();
    
    // Load weather status (should come from weather API)
    this.loadWeatherStatus();
  }

  private loadPilotFlights(): void {
    this.flightService.getAllFlights().subscribe({
      next: (flights) => {
        // Filter flights for current pilot (assuming pilotId is in flight data)
        const pilotId = this.currentUser?.id;
        this.myFlights = flights.length;
        
        // Get upcoming flights for this pilot
        this.upcomingFlightsList = flights.filter(flight => 
          flight.status === 'PROGRAMME' && 
          new Date(flight.scheduledTime) > new Date()
        );
        
        this.upcomingFlights = this.upcomingFlightsList.length;
        
        // Get completed flights for this pilot
        this.completedFlights = flights.filter(flight =>
          flight.status === 'ARRIVE' || flight.status === 'AU_SOL'
        ).length;
      },
      error: (error) => {
        console.error('Error loading pilot flights:', error);
      }
    });
  }

  private loadCurrentFlight(): void {
    // This should load the current active flight for the pilot
    // For now, we'll check if there's an active flight
    this.flightService.getActiveFlights().subscribe({
      next: (activeFlights) => {
        const pilotId = this.currentUser?.id;
        // Note: The Flight model doesn't have pilotId field yet
        // This should be added to the backend model when implemented
        this.currentFlight = activeFlights.find(flight => 
          // TODO: Add pilotId field to Flight model
          // For now, we'll use a placeholder logic
          flight.crewCount && flight.crewCount > 0
        ) || null;
      },
      error: (error) => {
        console.error('Error loading current flight:', error);
      }
    });
  }

  private loadWeatherStatus(): void {
    // This should come from a weather API
    // For now, keeping the mock value
    this.weatherStatus = 'Ciel clair';
  }

  getAirportName(code: string): string {
    const airports: { [key: string]: string } = {
      'CDG': 'Paris Charles de Gaulle',
      'JFK': 'New York JFK',
      'LHR': 'London Heathrow',
      'DXB': 'Dubai International',
      'NRT': 'Tokyo Narita',
      'LAX': 'Los Angeles International'
    };
    return airports[code] || code;
  }

  getFlightStatusText(status: string): string {
    const statusTexts: { [key: string]: string } = {
      'PROGRAMME':       'Programmé',
      'EN_EMBARQUEMENT': 'Embarquement',
      'DECOLLAGE':       'Décollage',
      'EN_VOL':          'En vol',
      'EN_APPROCHE':     'En approche',
      'ARRIVE':          'Arrivé',
      'AU_SOL':          'Au sol',
      'RETARDE':         'Retardé',
      'ANNULE':          'Annulé',
      'DIVERTE':         'Dérouté'
    };
    return statusTexts[status] || status;
  }

  navigateTo(route: string): void {
    this.router.navigate([route]);
  }

  viewFlightDetails(flight: Flight): void {
    this.router.navigate(['/flights', flight.id]);
  }
}
