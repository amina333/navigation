import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { JwtResponse, LoginRequest, User, UserRole } from '../models/user.model';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly TOKEN_KEY = 'nav_jwt_token';
  private readonly USER_KEY = 'nav_current_user';

  private currentUserSubject = new BehaviorSubject<JwtResponse | null>(this.loadUserFromStorage());
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {}

  login(request: LoginRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.API_URL}/signin`, request).pipe(
      tap(response => {
        localStorage.setItem(this.TOKEN_KEY, response.token);
        localStorage.setItem(this.USER_KEY, JSON.stringify(response));
        this.currentUserSubject.next(response);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getCurrentUser(): JwtResponse | null {
    return this.currentUserSubject.value;
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  hasRole(role: UserRole): boolean {
    return this.getCurrentUser()?.roles?.includes(role) ?? false;
  }

  isAdmin(): boolean {
    return this.hasRole('ADMIN' as UserRole);
  }

  isSuperviseur(): boolean {
    return this.hasRole('SUPERVISEUR' as UserRole);
  }

  generateResetToken(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.API_URL}/generate-reset-token/${email}`, {});
  }

  resetPassword(email: string, resetToken: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.API_URL}/reset-password`, { email, resetToken, newPassword });
  }

  setCurrentUser(user: User): void {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    // Convert User to JwtResponse format for compatibility
    const jwtResponse: JwtResponse = {
      token: user.id.toString(), // This would normally be a real JWT token
      type: 'Bearer',
      id: user.id,
      email: user.email,
      fullName: user.fullName,
      roles: user.roles
    };
    this.currentUserSubject.next(jwtResponse);
  }

  private loadUserFromStorage(): JwtResponse | null {
    const stored = localStorage.getItem(this.USER_KEY);
    return stored ? JSON.parse(stored) : null;
  }
}
