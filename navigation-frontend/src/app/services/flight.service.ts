import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Flight, FlightRequest, FlightStatistics, FlightStatusUpdate, FlightType
} from '../models/flight.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class FlightService {

  private readonly API_URL = `${environment.apiUrl}/flights`;

  constructor(private http: HttpClient) {}

  getAllFlights(): Observable<Flight[]> {
    return this.http.get<Flight[]>(this.API_URL);
  }

  getActiveFlights(): Observable<Flight[]> {
    return this.http.get<Flight[]>(`${this.API_URL}/active`);
  }

  getTodaysFlights(): Observable<Flight[]> {
    return this.http.get<Flight[]>(`${this.API_URL}/today`);
  }

  getFlightsByType(type: FlightType): Observable<Flight[]> {
    return this.http.get<Flight[]>(`${this.API_URL}/type/${type}`);
  }

  getFlightById(id: number): Observable<Flight> {
    return this.http.get<Flight>(`${this.API_URL}/${id}`);
  }

  getFlightByNumber(flightNumber: string): Observable<Flight> {
    return this.http.get<Flight>(`${this.API_URL}/number/${flightNumber}`);
  }

  getStatistics(): Observable<FlightStatistics> {
    return this.http.get<FlightStatistics>(`${this.API_URL}/statistics`);
  }

  createFlight(flight: FlightRequest): Observable<Flight> {
    return this.http.post<Flight>(this.API_URL, flight);
  }

  updateFlight(id: number, flight: FlightRequest): Observable<Flight> {
    return this.http.put<Flight>(`${this.API_URL}/${id}`, flight);
  }

  updateFlightStatus(id: number, update: FlightStatusUpdate): Observable<Flight> {
    return this.http.patch<Flight>(`${this.API_URL}/${id}/status`, update);
  }

  deleteFlight(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
