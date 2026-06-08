import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserService } from '../../../services/user.service';
import { User } from '../../../models/user.model';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="user-management-container">
      <!-- Header Section -->
      <div class="header-section">
        <div class="header-content">
          <div class="header-text">
            <h1 class="page-title">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 00-3-3.87"/>
                <path d="M16 3.13a4 4 0 010 7.75"/>
              </svg>
              Gestion des Utilisateurs
            </h1>
            <p class="page-subtitle">Administration des agents et gestion des permissions</p>
          </div>
          <div class="header-actions">
            <button class="btn btn-secondary" (click)="refreshUsers()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="23 4 23 10 17 10"/>
                <path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/>
              </svg>
              Actualiser
            </button>
            <button class="btn btn-primary" (click)="openCreateModal()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              Nouvel Utilisateur
            </button>
          </div>
        </div>
      </div>

      <!-- Stats Cards -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 00-3-3.87"/>
              <path d="M16 3.13a4 4 0 010 7.75"/>
            </svg>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ users.length }}</div>
            <div class="stat-label">Total Utilisateurs</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon active">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ activeUsersCount }}</div>
            <div class="stat-label">Utilisateurs Actifs</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon admin">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
            </svg>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ adminUsersCount }}</div>
            <div class="stat-label">Administrateurs</div>
          </div>
        </div>
      </div>

      <!-- Search and Filter -->
      <div class="search-section">
        <div class="search-bar">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35"/>
          </svg>
          <input 
            type="text" 
            placeholder="Rechercher par nom, email ou badge..." 
            [(ngModel)]="searchTerm"
            (input)="filterUsers()"
          >
        </div>
        <div class="filter-dropdown">
          <select [(ngModel)]="statusFilter" (change)="filterUsers()">
            <option value="">Tous les statuts</option>
            <option value="active">Actifs</option>
            <option value="inactive">Inactifs</option>
          </select>
        </div>
      </div>

      <!-- Users Table -->
      <div class="table-container">
        <div class="table-card">
          <div class="table-header">
            <h3>Liste des Utilisateurs</h3>
            <div class="table-actions">
              <button class="icon-btn" title="Exporter" (click)="exportUsers()">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
                  <polyline points="7 10 12 15 17 10"/>
                  <line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
              </button>
            </div>
          </div>

          <div class="table-wrapper">
            <table class="users-table">
              <thead>
                <tr>
                  <th (click)="sortBy('name')" class="sortable">
                    Agent
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="18 15 12 9 6 15"/>
                    </svg>
                  </th>
                  <th>Contact</th>
                  <th>Badge</th>
                  <th>Rôles</th>
                  <th (click)="sortBy('status')" class="sortable">
                    Statut
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="18 15 12 9 6 15"/>
                    </svg>
                  </th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngIf="loading" class="loading-row">
                  <td colspan="6">
                    <div class="loading-content">
                      <div class="spinner"></div>
                      <span>Chargement des utilisateurs...</span>
                    </div>
                  </td>
                </tr>
                <tr *ngIf="!loading && filteredUsers.length === 0" class="empty-row">
                  <td colspan="6">
                    <div class="empty-content">
                      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="12" cy="12" r="10"/>
                        <line x1="12" y1="8" x2="12" y2="12"/>
                        <line x1="12" y1="16" x2="12.01" y2="16"/>
                      </svg>
                      <h4>Aucun utilisateur trouvé</h4>
                      <p>{{ searchTerm ? 'Essayez une autre recherche' : 'Commencez par ajouter un utilisateur' }}</p>
                      <button class="btn btn-primary" (click)="openCreateModal()" *ngIf="!searchTerm">
                        Ajouter un utilisateur
                      </button>
                    </div>
                  </td>
                </tr>
                <tr *ngFor="let user of filteredUsers" class="user-row" [class.inactive-row]="!user.active">
                  <td>
                    <div class="user-cell">
                      <div class="user-avatar">
                        <img *ngIf="user.avatar" [src]="user.avatar" [alt]="user.fullName">
                        <span *ngIf="!user.avatar">{{ user.firstName[0] }}{{ user.lastName[0] }}</span>
                      </div>
                      <div class="user-info">
                        <div class="user-name">{{ user.fullName }}</div>
                        <div class="user-meta">
                          <span class="created-date">Créé le {{ user.createdAt | date:'dd MMM yyyy' }}</span>
                          <span class="last-login" *ngIf="user.lastLogin">Dernière connexion: {{ user.lastLogin | date:'dd MMM HH:mm' }}</span>
                        </div>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div class="contact-info">
                      <div class="email">{{ user.email }}</div>
                      <div class="phone" *ngIf="user.phoneNumber">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07 19.5 19.5 0 01-6-6 19.79 19.79 0 01-3.07-8.67A2 2 0 014.11 2h3a2 2 0 012 1.72 12.84 12.84 0 00.7 2.81 2 2 0 01-.45 2.11L8.09 9.91a16 16 0 006 6l1.27-1.27a2 2 0 012.11-.45 12.84 12.84 0 002.81.7A2 2 0 0122 16.92z"/>
                        </svg>
                        {{ user.phoneNumber }}
                      </div>
                    </div>
                  </td>
                  <td>
                    <div class="badge-cell">
                      <span class="badge-number" *ngIf="user.badgeNumber">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                          <path d="M2 17l10 5 10-5"/>
                          <path d="M2 12l10 5 10-5"/>
                        </svg>
                        {{ user.badgeNumber }}
                      </span>
                      <span class="no-badge" *ngIf="!user.badgeNumber">Non assigné</span>
                    </div>
                  </td>
                  <td>
                    <div class="roles-container">
                      <span class="role-badge admin" *ngIf="hasRole(user, 'ADMIN')">Admin</span>
                      <span class="role-badge supervisor" *ngIf="hasRole(user, 'SUPERVISEUR')">Superviseur</span>
                      <span class="role-badge agent" *ngIf="hasRole(user, 'AGENT')">Agent</span>
                    </div>
                  </td>
                  <td>
                    <div class="status-cell">
                      <label class="status-toggle">
                        <input type="checkbox" [checked]="user.active" (change)="toggleStatus(user.id)">
                        <span class="toggle-slider" [class.active]="user.active"></span>
                        <span class="status-text" [class.active]="user.active">
                          {{ user.active ? 'Actif' : 'Inactif' }}
                        </span>
                      </label>
                    </div>
                  </td>
                  <td>
                    <div class="actions-cell">
                      <button class="action-btn view" title="Voir les détails" (click)="viewUser(user)">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                          <circle cx="12" cy="12" r="3"/>
                        </svg>
                      </button>
                      <button class="action-btn edit" title="Modifier" (click)="editUser(user)">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/>
                          <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-3z"/>
                        </svg>
                      </button>
                      <button class="action-btn delete" title="Supprimer" (click)="deleteUser(user)">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <polyline points="3 6 5 6 21 6"/>
                          <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- Create/Edit User Modal -->
      <div class="modal-overlay" *ngIf="showUserModal" (click)="closeModal()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>{{ editingUser ? 'Modifier l\'utilisateur' : 'Nouvel utilisateur' }}</h3>
            <button class="close-btn" (click)="closeModal()">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <form class="user-form">
              <div class="form-row">
                <div class="form-group">
                  <label>Prénom</label>
                  <input type="text" [(ngModel)]="userForm.firstName" name="firstName">
                </div>
                <div class="form-group">
                  <label>Nom</label>
                  <input type="text" [(ngModel)]="userForm.lastName" name="lastName">
                </div>
              </div>
              <div class="form-group">
                <label>Email</label>
                <input type="email" [(ngModel)]="userForm.email" name="email">
              </div>
              <div class="form-group">
                <label>Téléphone</label>
                <input type="tel" [(ngModel)]="userForm.phoneNumber" name="phoneNumber">
              </div>
              <div class="form-group">
                <label>Badge</label>
                <input type="text" [(ngModel)]="userForm.badgeNumber" name="badgeNumber" placeholder="Numéro de badge">
              </div>
              <div class="form-group">
                <label>Mot de passe</label>
                <input type="password" [(ngModel)]="userForm.password" name="password" placeholder="{{ editingUser ? 'Laisser vide pour ne pas modifier' : 'Mot de passe' }}">
              </div>
              <div class="form-group">
                <label>Rôles</label>
                <div class="roles-selection">
                  <label class="role-checkbox">
                    <input type="checkbox" [(ngModel)]="userForm.isAdmin" name="isAdmin">
                    <span class="checkmark"></span>
                    Administrateur
                  </label>
                  <label class="role-checkbox">
                    <input type="checkbox" [(ngModel)]="userForm.isSupervisor" name="isSupervisor">
                    <span class="checkmark"></span>
                    Superviseur
                  </label>
                  <label class="role-checkbox">
                    <input type="checkbox" [(ngModel)]="userForm.isAgent" name="isAgent">
                    <span class="checkmark"></span>
                    Agent
                  </label>
                </div>
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" (click)="closeModal()">Annuler</button>
            <button class="btn btn-primary" (click)="saveUser()" [disabled]="saving">
              <span *ngIf="saving" class="spinner"></span>
              {{ editingUser ? 'Mettre à jour' : 'Créer' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .user-management-container {
      padding: 1.5rem;
      max-width: 1400px;
      margin: 0 auto;
      animation: fadeIn 0.4s ease-out;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(10px); }
      to { opacity: 1; transform: translateY(0); }
    }

    /* Header Section */
    .header-section {
      margin-bottom: 2rem;
    }

    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 2rem;
    }

    .header-text {
      flex: 1;
    }

    .page-title {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      font-size: 2rem;
      font-weight: 800;
      color: #0f172a;
      margin-bottom: 0.5rem;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .page-title svg {
      color: #0066cc;
    }

    .page-subtitle {
      font-size: 1rem;
      color: #64748b;
      margin: 0;
    }

    .header-actions {
      display: flex;
      gap: 0.75rem;
    }

    /* Stats Cards */
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 1.5rem;
      margin-bottom: 2rem;
    }

    .stat-card {
      background: #ffffff;
      border-radius: 1rem;
      padding: 1.5rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      border: 1px solid #e2e8f0;
      display: flex;
      align-items: center;
      gap: 1rem;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .stat-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }

    .stat-icon {
      width: 48px;
      height: 48px;
      background: linear-gradient(135deg, #0066cc, #004c99);
      border-radius: 0.75rem;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
    }

    .stat-icon.active {
      background: linear-gradient(135deg, #10b981, #059669);
    }

    .stat-icon.admin {
      background: linear-gradient(135deg, #f59e0b, #d97706);
    }

    .stat-content {
      flex: 1;
    }

    .stat-value {
      font-size: 1.875rem;
      font-weight: 800;
      color: #0f172a;
      line-height: 1;
    }

    .stat-label {
      font-size: 0.875rem;
      color: #64748b;
      margin-top: 0.25rem;
    }

    /* Search Section */
    .search-section {
      display: flex;
      gap: 1rem;
      margin-bottom: 2rem;
    }

    .search-bar {
      flex: 1;
      position: relative;
      display: flex;
      align-items: center;
    }

    .search-bar svg {
      position: absolute;
      left: 1rem;
      color: #64748b;
      z-index: 1;
    }

    .search-bar input {
      width: 100%;
      padding: 0.75rem 1rem 0.75rem 2.75rem;
      border: 2px solid #e2e8f0;
      border-radius: 0.75rem;
      font-size: 0.875rem;
      transition: all 0.2s;
    }

    .search-bar input:focus {
      outline: none;
      border-color: #0066cc;
      box-shadow: 0 0 0 3px rgba(0, 102, 204, 0.1);
    }

    .filter-dropdown select {
      padding: 0.75rem 1rem;
      border: 2px solid #e2e8f0;
      border-radius: 0.75rem;
      font-size: 0.875rem;
      background: white;
      cursor: pointer;
    }

    /* Table */
    .table-container {
      background: #ffffff;
      border-radius: 1rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      border: 1px solid #e2e8f0;
      overflow: hidden;
    }

    .table-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1.5rem;
      border-bottom: 1px solid #e2e8f0;
    }

    .table-header h3 {
      font-size: 1.125rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
    }

    .table-wrapper {
      overflow-x: auto;
    }

    .users-table {
      width: 100%;
      border-collapse: collapse;
    }

    .users-table th {
      background: #f8fafc;
      padding: 1rem;
      text-align: left;
      font-weight: 600;
      font-size: 0.75rem;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      border-bottom: 1px solid #e2e8f0;
    }

    .users-table th.sortable {
      cursor: pointer;
      user-select: none;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .users-table th.sortable:hover {
      color: #0066cc;
    }

    .users-table td {
      padding: 1rem;
      border-bottom: 1px solid #e2e8f0;
      font-size: 0.875rem;
      color: #0f172a;
    }

    .users-table tr:hover {
      background: #f8fafc;
    }

    .user-row.inactive-row {
      opacity: 0.6;
    }

    /* User Cell */
    .user-cell {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .user-avatar {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: linear-gradient(135deg, #0066cc, #004c99);
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 0.875rem;
      overflow: hidden;
    }

    .user-avatar img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .user-info {
      flex: 1;
    }

    .user-name {
      font-weight: 600;
      color: #0f172a;
      margin-bottom: 0.125rem;
    }

    .user-meta {
      display: flex;
      flex-direction: column;
      gap: 0.125rem;
    }

    .created-date, .last-login {
      font-size: 0.75rem;
      color: #64748b;
    }

    /* Contact Info */
    .contact-info .email {
      font-weight: 500;
      margin-bottom: 0.25rem;
    }

    .contact-info .phone {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      font-size: 0.75rem;
      color: #64748b;
    }

    /* Badge */
    .badge-number {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      background: #f8fafc;
      padding: 0.25rem 0.625rem;
      border-radius: 0.375rem;
      font-weight: 600;
      color: #64748b;
      border: 1px solid #e2e8f0;
      font-size: 0.8125rem;
    }

    .no-badge {
      color: #94a3b8;
      font-size: 0.8125rem;
      font-style: italic;
    }

    /* Roles */
    .roles-container {
      display: flex;
      gap: 0.375rem;
      flex-wrap: wrap;
    }

    .role-badge {
      padding: 0.25rem 0.625rem;
      border-radius: 1rem;
      font-size: 0.6875rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }

    .role-badge.admin {
      background: rgba(245, 158, 11, 0.1);
      color: #d97706;
      border: 1px solid rgba(245, 158, 11, 0.3);
    }

    .role-badge.supervisor {
      background: rgba(139, 92, 246, 0.1);
      color: #8b5cf6;
      border: 1px solid rgba(139, 92, 246, 0.3);
    }

    .role-badge.agent {
      background: rgba(0, 102, 204, 0.1);
      color: #0066cc;
      border: 1px solid rgba(0, 102, 204, 0.3);
    }

    /* Status Toggle */
    .status-toggle {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      cursor: pointer;
    }

    .status-toggle input[type="checkbox"] {
      display: none;
    }

    .toggle-slider {
      width: 44px;
      height: 24px;
      background: #e2e8f0;
      border-radius: 12px;
      position: relative;
      transition: all 0.3s;
    }

    .toggle-slider::after {
      content: '';
      position: absolute;
      width: 18px;
      height: 18px;
      background: white;
      border-radius: 50%;
      top: 3px;
      left: 3px;
      transition: all 0.3s;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
    }

    .toggle-slider.active {
      background: #10b981;
    }

    .toggle-slider.active::after {
      left: 23px;
    }

    .status-text {
      font-size: 0.75rem;
      font-weight: 600;
      color: #64748b;
      transition: color 0.3s;
    }

    .status-text.active {
      color: #10b981;
    }

    /* Actions */
    .actions-cell {
      display: flex;
      gap: 0.5rem;
    }

    .action-btn {
      width: 32px;
      height: 32px;
      border: 1px solid #e2e8f0;
      background: white;
      border-radius: 0.375rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }

    .action-btn:hover {
      transform: translateY(-1px);
    }

    .action-btn.view:hover {
      background: #0066cc;
      border-color: #0066cc;
      color: white;
    }

    .action-btn.edit:hover {
      background: #f59e0b;
      border-color: #f59e0b;
      color: white;
    }

    .action-btn.delete:hover {
      background: #ef4444;
      border-color: #ef4444;
      color: white;
    }

    /* Loading & Empty States */
    .loading-row, .empty-row {
      background: #f8fafc;
    }

    .loading-content, .empty-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 3rem;
      gap: 1rem;
    }

    .spinner {
      width: 24px;
      height: 24px;
      border: 2px solid rgba(0, 102, 204, 0.3);
      border-top-color: #0066cc;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .empty-content h4 {
      margin: 0;
      color: #374151;
      font-size: 1rem;
    }

    .empty-content p {
      margin: 0.25rem 0;
      color: #64748b;
      font-size: 0.875rem;
    }

    /* Modal */
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
    }

    .modal-card {
      background: white;
      border-radius: 1rem;
      width: 100%;
      max-width: 600px;
      max-height: 90vh;
      overflow-y: auto;
      animation: modalSlideIn 0.3s ease-out;
    }

    @keyframes modalSlideIn {
      from {
        opacity: 0;
        transform: translateY(-20px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1.5rem;
      border-bottom: 1px solid #e2e8f0;
    }

    .modal-header h3 {
      margin: 0;
      font-size: 1.25rem;
      font-weight: 700;
      color: #0f172a;
    }

    .close-btn {
      background: none;
      border: none;
      color: #64748b;
      cursor: pointer;
      padding: 0.25rem;
      border-radius: 0.25rem;
      transition: color 0.2s;
    }

    .close-btn:hover {
      color: #374151;
    }

    .modal-body {
      padding: 1.5rem;
    }

    .user-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .form-group label {
      font-size: 0.875rem;
      font-weight: 600;
      color: #374151;
    }

    .form-group input {
      padding: 0.75rem;
      border: 2px solid #e2e8f0;
      border-radius: 0.5rem;
      font-size: 0.875rem;
      transition: all 0.2s;
    }

    .form-group input:focus {
      outline: none;
      border-color: #0066cc;
      box-shadow: 0 0 0 3px rgba(0, 102, 204, 0.1);
    }

    .roles-selection {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .role-checkbox {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      cursor: pointer;
    }

    .role-checkbox input[type="checkbox"] {
      display: none;
    }

    .checkmark {
      width: 20px;
      height: 20px;
      border: 2px solid #d1d5db;
      border-radius: 0.25rem;
      position: relative;
      transition: all 0.2s;
    }

    .role-checkbox input:checked + .checkmark {
      background: #0066cc;
      border-color: #0066cc;
    }

    .role-checkbox input:checked + .checkmark::after {
      content: '';
      position: absolute;
      left: 5px;
      top: 2px;
      width: 4px;
      height: 8px;
      border: solid white;
      border-width: 0 2px 2px 0;
      transform: rotate(45deg);
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      padding: 1.5rem;
      border-top: 1px solid #e2e8f0;
    }

    /* Buttons */
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.75rem 1.25rem;
      border-radius: 0.5rem;
      font-family: inherit;
      font-size: 0.875rem;
      font-weight: 600;
      line-height: 1;
      cursor: pointer;
      border: none;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      text-decoration: none;
      white-space: nowrap;
    }

    .btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .btn-primary {
      background: linear-gradient(135deg, #0066cc 0%, #004c99 100%);
      color: #ffffff;
      box-shadow: 0 4px 12px rgba(0, 102, 204, 0.3);
    }

    .btn-primary:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 6px 16px rgba(0, 102, 204, 0.4);
    }

    .btn-secondary {
      background: #f8fafc;
      color: #64748b;
      border: 1px solid #e2e8f0;
    }

    .btn-secondary:hover {
      background: #e2e8f0;
      color: #374151;
    }

    /* Responsive */
    @media (max-width: 1024px) {
      .header-content {
        flex-direction: column;
        align-items: stretch;
      }

      .header-actions {
        justify-content: flex-start;
      }

      .stats-grid {
        grid-template-columns: 1fr;
      }

      .search-section {
        flex-direction: column;
      }

      .form-row {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 768px) {
      .user-management-container {
        padding: 1rem;
      }

      .page-title {
        font-size: 1.5rem;
      }

      .users-table {
        font-size: 0.75rem;
      }

      .users-table th,
      .users-table td {
        padding: 0.75rem 0.5rem;
      }

      .user-avatar {
        width: 32px;
        height: 32px;
        font-size: 0.75rem;
      }

      .actions-cell {
        flex-direction: column;
        gap: 0.25rem;
      }

      .modal-card {
        margin: 1rem;
      }
    }
  `]
})
export class UserManagementComponent implements OnInit {
  users: User[] = [];
  filteredUsers: User[] = [];
  loading = true;
  searchTerm = '';
  statusFilter = '';
  sortField = '';
  sortDirection = 'asc';

  // Modal state
  showUserModal = false;
  editingUser: User | null = null;
  saving = false;

  // User form
  userForm = {
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: '',
    badgeNumber: '',
    password: '',
    isAdmin: false,
    isSupervisor: false,
    isAgent: false
  };

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  get activeUsersCount(): number {
    return this.users.filter(u => u.active).length;
  }

  get adminUsersCount(): number {
    return this.users.filter(u => u.roles.includes('ROLE_ADMIN')).length;
  }

  loadUsers(): void {
    this.loading = true;
    this.userService.getAllUsers().subscribe({
      next: (res) => {
        this.users = res;
        this.filteredUsers = [...res];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  refreshUsers(): void {
    this.loadUsers();
  }

  filterUsers(): void {
    let filtered = [...this.users];

    // Search filter
    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      filtered = filtered.filter(user => 
        user.fullName.toLowerCase().includes(term) ||
        user.email.toLowerCase().includes(term) ||
        (user.badgeNumber && user.badgeNumber.toLowerCase().includes(term))
      );
    }

    // Status filter
    if (this.statusFilter) {
      filtered = filtered.filter(user => 
        this.statusFilter === 'active' ? user.active : !user.active
      );
    }

    this.filteredUsers = filtered;
  }

  sortBy(field: string): void {
    if (this.sortField === field) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortField = field;
      this.sortDirection = 'asc';
    }

    this.filteredUsers.sort((a, b) => {
      let aValue: any, bValue: any;

      switch (field) {
        case 'name':
          aValue = a.fullName.toLowerCase();
          bValue = b.fullName.toLowerCase();
          break;
        case 'status':
          aValue = a.active;
          bValue = b.active;
          break;
        default:
          return 0;
      }

      if (aValue < bValue) return this.sortDirection === 'asc' ? -1 : 1;
      if (aValue > bValue) return this.sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }

  toggleStatus(id: number): void {
    this.userService.toggleUserStatus(id).subscribe(() => {
      this.loadUsers();
    });
  }

  openCreateModal(): void {
    this.editingUser = null;
    this.resetForm();
    this.showUserModal = true;
  }

  editUser(user: User): void {
    this.editingUser = user;
    this.userForm = {
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
      phoneNumber: user.phoneNumber || '',
      badgeNumber: user.badgeNumber || '',
      password: '',
      isAdmin: user.roles.includes('ROLE_ADMIN'),
      isSupervisor: user.roles.includes('ROLE_SUPERVISEUR'),
      isAgent: user.roles.includes('ROLE_AGENT')
    };
    this.showUserModal = true;
  }

  viewUser(user: User): void {
    console.log('View user:', user);
    // Implement user details view
  }

  deleteUser(user: User): void {
    if (confirm(`Êtes-vous sûr de vouloir supprimer ${user.fullName} ?`)) {
      this.userService.deleteUser(user.id).subscribe(() => {
        this.loadUsers();
      });
    }
  }

  saveUser(): void {
    this.saving = true;

    const roles: string[] = [];
    if (this.userForm.isAdmin) roles.push('ROLE_ADMIN');
    if (this.userForm.isSupervisor) roles.push('ROLE_SUPERVISEUR');
    if (this.userForm.isAgent) roles.push('ROLE_AGENT');

    const userData = {
      firstName: this.userForm.firstName,
      lastName: this.userForm.lastName,
      email: this.userForm.email,
      phoneNumber: this.userForm.phoneNumber,
      badgeNumber: this.userForm.badgeNumber,
      password: this.userForm.password || undefined,
      roles
    };

    if (this.editingUser) {
      this.userService.updateUser(this.editingUser.id, userData).subscribe(() => {
        this.closeModal();
        this.loadUsers();
      });
    } else {
      this.userService.createUser(userData).subscribe(() => {
        this.closeModal();
        this.loadUsers();
      });
    }
  }

  closeModal(): void {
    this.showUserModal = false;
    this.editingUser = null;
    this.resetForm();
  }

  resetForm(): void {
    this.userForm = {
      firstName: '',
      lastName: '',
      email: '',
      phoneNumber: '',
      badgeNumber: '',
      password: '',
      isAdmin: false,
      isSupervisor: false,
      isAgent: false
    };
    this.saving = false;
  }

  exportUsers(): void {
    console.log('Export users');
    // Implement export functionality
  }

  hasRole(user: User, role: string): boolean {
    return user.roles.includes(`ROLE_${role.toUpperCase()}`);
  }

  formatRole(role: string): string {
    return role.replace('ROLE_', '');
  }
}
