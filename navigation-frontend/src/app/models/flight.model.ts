// Valeurs exactes du backend (FlightStatus.java)
export enum FlightStatus {
  PROGRAMME       = 'PROGRAMME',
  EN_EMBARQUEMENT = 'EN_EMBARQUEMENT',
  DECOLLAGE       = 'DECOLLAGE',
  EN_VOL          = 'EN_VOL',
  EN_APPROCHE     = 'EN_APPROCHE',
  ARRIVE          = 'ARRIVE',
  AU_SOL          = 'AU_SOL',
  RETARDE         = 'RETARDE',
  ANNULE          = 'ANNULE',
  DIVERTE         = 'DIVERTE'
}

// Valeurs exactes du backend: DEPART, ARRIVEE
export enum FlightType {
  DEPART  = 'DEPART',
  ARRIVEE = 'ARRIVEE'
}

export interface Airport {
  code: string;
  name: string;
  city: string;
  country: string;
  iataCode?: string;
  coordinates?: {
    latitude: number;
    longitude: number;
  };
}

export interface Aircraft {
  type: string;
  registration: string;
  model?: string;
  manufacturer?: string;
  capacity?: number;
  cargoCapacity?: number;
}

export interface Flight {
  id: number;
  flightNumber: string;
  airline: string;
  // Champs aircraft aplatis (correspondent à FlightResponseDto backend)
  aircraftType: string;
  aircraftRegistration?: string;
  // Origin/Destination : strings (code IATA ou nom) côté backend
  origin: string;
  destination: string;
  originIata?: string;
  destinationIata?: string;
  scheduledTime: string;
  actualTime?: string;
  estimatedTime?: string;
  status: FlightStatus;
  flightType: FlightType;
  // Alias pour compatibilité HTML existant
  type?: FlightType;
  runway?: string;
  gate?: string;
  terminal?: string;
  remarks?: string;
  passengersCount?: number;
  crewCount?: number;
  createdAt: string;
  updatedAt: string;
}


export interface FlightPlan {
  id: number;
  flightId: number;
  route: Waypoint[];
  estimatedDuration: number;
  fuelRequired: number;
  alternateAirport?: string;
  cruiseAltitude: number;
  cruiseSpeed: number;
  createdAt: string;
  approvedBy?: number;
  approvedAt?: string;
}

export interface Waypoint {
  sequence: number;
  name: string;
  coordinates: {
    latitude: number;
    longitude: number;
  };
  altitude?: number;
  speed?: number;
  estimatedTimeOver?: string;
}

export interface WeatherInfo {
  visibility: number;
  windSpeed: number;
  windDirection: number;
  temperature: number;
  pressure: number;
  humidity: number;
  conditions: string;
  updatedAt: string;
}

export interface DelayInfo {
  id: number;
  reason: string;
  duration: number;
  startTime: string;
  endTime?: string;
  reportedBy: number;
  resolvedBy?: number;
}

export interface FlightRequest {
  flightNumber: string;
  airline: string;
  type: FlightType;
  // Champs aircraft plats (correspond à FlightRequestDto backend)
  aircraftType?: string;
  aircraftRegistration?: string;
  origin: string;
  destination: string;
  scheduledTime: string;
  runway?: string;
  gate?: string;
  terminal?: string;
  remarks?: string;
  passengersCount?: number;
  crewCount?: number;
}


export interface FlightStatusUpdate {
  status: FlightStatus;
  remarks?: string;
  runway?: string;
  gate?: string;
  actualTime?: string;
  estimatedTime?: string;
  altitude?: number;
  speed?: number;
  heading?: number;
  fuelLevel?: number;
  delayReason?: string;
}

export interface FlightStatistics {
  total: number;
  byStatus: Record<FlightStatus, number>;
  byType: Record<FlightType, number>;
  byAirline: Record<string, number>;
  delays: {
    total: number;
    averageDelay: number;
    totalDelayMinutes: number;
  };
  onTimePerformance: number;
  cancellations: number;
  diversions: number;
}

export interface FlightFilter {
  status?: FlightStatus[];
  type?: FlightType[];
  airline?: string[];
  origin?: string[];
  destination?: string[];
  dateFrom?: string;
  dateTo?: string;
  delayed?: boolean;
  cancelled?: boolean;
}

export interface FlightSearchResult {
  flights: Flight[];
  total: number;
  page: number;
  pageSize: number;
  filters: FlightFilter;
}
