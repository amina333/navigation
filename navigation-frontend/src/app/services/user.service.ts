import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';
import { User, CreateUserRequest, UpdateUserRequest } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly API_URL = `${environment.apiUrl}/users`;

  constructor(private http: HttpClient) {}

  // Get all users
  getAllUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.API_URL);
  }

  // Get user by ID
  getUserById(id: number): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/${id}`);
  }

  // Create new user
  createUser(user: CreateUserRequest): Observable<User> {
    return this.http.post<User>(this.API_URL, user);
  }

  // Update user
  updateUser(id: number, user: UpdateUserRequest): Observable<User> {
    return this.http.put<User>(`${this.API_URL}/${id}`, user);
  }

  // Delete user
  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  // Toggle user status (activate/deactivate)
  toggleUserStatus(id: number): Observable<User> {
    return this.http.patch<User>(`${this.API_URL}/${id}/toggle-status`, {});
  }

  // Reset user password
  resetPassword(id: number): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.API_URL}/${id}/reset-password`, {});
  }

  // Get users by role
  getUsersByRole(role: string): Observable<User[]> {
    return this.http.get<User[]>(`${this.API_URL}/role/${role}`);
  }

  // Search users
  searchUsers(query: string): Observable<User[]> {
    return this.http.get<User[]>(`${this.API_URL}/search?q=${query}`);
  }

  // Get user statistics
  getUserStatistics(): Observable<any> {
    return this.http.get<any>(`${this.API_URL}/statistics`);
  }

  // Bulk operations
  bulkUpdateUsers(userIds: number[], updates: Partial<User>): Observable<User[]> {
    return this.http.patch<User[]>(`${this.API_URL}/bulk-update`, { userIds, updates });
  }

  bulkDeleteUsers(userIds: number[]): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/bulk-delete`, { userIds });
  }

  // Export users
  exportUsers(format: 'csv' | 'excel' | 'pdf'): Observable<Blob> {
    return this.http.get(`${this.API_URL}/export?format=${format}`, { responseType: 'blob' });
  }
}
