import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserService } from '../../../services/user.service';
import { RoleService } from '../../../services/role.service';
import { User, CreateUserRequest, UpdateUserRequest, UserRole } from '../../../models/user.model';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, DatePipe, DecimalPipe],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss']
})
export class UserManagementComponent implements OnInit {
  
  users: User[] = [];
  filteredUsers: User[] = [];
  searchTerm = '';
  roleFilter = '';
  statusFilter = '';
  
  showCreateModal = false;
  editingUser: User | null = null;
  
  userForm!: FormGroup;

  // Statistics
  totalUsers = 0;
  activeUsers = 0;
  adminUsers = 0;
  pilotUsers = 0;
  userStats$!: Observable<any>;
  stats$!: Observable<any>;
  stats: any = {
    total: 0,
    active: 0,
    inactive: 0,
    admins: 0,
    pilots: 0
  };

  // Advanced features
  selectedUsers: number[] = [];
  showBulkActions = false;
  bulkAction = '';
  loading = false;

  constructor(
    private userService: UserService,
    private roleService: RoleService,
    private fb: FormBuilder,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.initializeForm();
    this.loadUsers();
    this.initializeStats();
  }

  private initializeStats(): void {
    this.stats$ = new Observable(observer => {
      observer.next({
        total: this.totalUsers,
        active: this.activeUsers,
        inactive: this.totalUsers - this.activeUsers
      });
    });
  }

  private initializeForm(): void {
    this.userForm = this.fb.group({
      firstName:   ['', Validators.required],
      lastName:    ['', Validators.required],
      email:       ['', [Validators.required, Validators.email]],
      password:    [''],
      phoneNumber: [''],
      badgeNumber: [''],
      matricule:   [''],   // UML
      grade:       [''],   // UML
      serviceNom:  [''],   // UML
      roles:       ['', Validators.required],
      active:      [true]
    });
  }

  loadUsers(): void {
    this.userService.getAllUsers().subscribe((users: User[]) => {
      this.users = users;
      this.filteredUsers = [...users];
      this.calculateStatistics();
    });
  }

  calculateStatistics(): void {
    this.stats = {
      total:    this.users.length,
      active:   this.users.filter(u => u.active).length,
      inactive: this.users.filter(u => !u.active).length,
      admins:   this.users.filter(u => u.roles.includes(UserRole.ADMIN)).length,
      pilots:   this.users.filter(u => u.roles.includes(UserRole.ATC)).length
    };
  }

  get activePct(): number {
    return this.stats.total ? Math.round(this.stats.active   / this.stats.total * 100) : 0;
  }
  get inactivePct(): number {
    return this.stats.total ? Math.round(this.stats.inactive / this.stats.total * 100) : 0;
  }

  filterUsers(): void {
    let filtered = [...this.users];

    // Search filter
    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      filtered = filtered.filter(user => 
        user.fullName.toLowerCase().includes(term) ||
        user.email.toLowerCase().includes(term) ||
        (user.badgeNumber?.toLowerCase() || '').includes(term)
      );
    }

    // Role filter
    if (this.roleFilter) {
      filtered = filtered.filter(user => user.roles.includes(this.roleFilter as UserRole));
    }

    // Status filter
    if (this.statusFilter) {
      const isActive = this.statusFilter === 'active';
      filtered = filtered.filter(user => user.active === isActive);
    }

    this.filteredUsers = filtered;
  }

  getRoleDisplayName(role: UserRole): string {
    const roleMap: { [key in UserRole]: string } = {
      [UserRole.ADMIN]: 'Administrateur',
      [UserRole.SUPERVISEUR]: 'Superviseur',
      [UserRole.ATC]: 'Contrôleur Aérien',
      [UserRole.PISTE]: 'Agent Piste',
      [UserRole.AIS]: 'Agent AIS',
      [UserRole.RH_FORMATION]: 'RH Formation',
      [UserRole.QUALITE]: 'Qualité',
      [UserRole.CONSULTATION]: 'Consultation'
    };
    return roleMap[role] || role;
  }

  getRoleClass(role: string): string {
    return role.toLowerCase();
  }

  formatDate(date: string | Date | undefined): string {
    if (!date) return 'N/A';
    const d = new Date(date);
    return d.toLocaleDateString('fr-FR', { 
      day: '2-digit', 
      month: '2-digit', 
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  editUser(user: User): void {
    this.editingUser = user;
    this.userForm.patchValue({
      firstName:   user.firstName,
      lastName:    user.lastName,
      email:       user.email,
      password:    '',
      phoneNumber: user.phoneNumber  || '',
      badgeNumber: user.badgeNumber  || '',
      matricule:   (user as any).matricule  || '',
      grade:       (user as any).grade      || '',
      serviceNom:  (user as any).serviceNom || '',
      roles:  user.roles[0] || '',
      active: user.active
    });
  }

  saveUser(): void {
    if (!this.isFormValid()) return;

    const formValue = this.userForm.value;
    
    // Convert single role to array for backend
    const userData = {
      ...formValue,
      roles: [formValue.roles] // Convert to array
    };

    if (this.editingUser) {
      // Update existing user
      this.userService.updateUser(this.editingUser.id, userData).subscribe({
        next: () => {
          this.loadUsers();
          this.closeModal();
        },
        error: (err: any) => {
          console.error('Error updating user:', err);
        }
      });
    } else {
      // Create new user
      this.userService.createUser(userData).subscribe({
        next: () => {
          this.loadUsers();
          this.closeModal();
        },
        error: (err: any) => {
          console.error('Error creating user:', err);
        }
      });
    }
  }

  toggleUserStatus(user: User): void {
    this.userService.toggleUserStatus(user.id).subscribe({
      next: () => {
        this.loadUsers();
      },
      error: (err: any) => {
        console.error('Error toggling user status:', err);
      }
    });
  }

  deleteUser(user: User): void {
    if (confirm(`Êtes-vous sûr de vouloir supprimer ${user.fullName} ?`)) {
      this.userService.deleteUser(user.id).subscribe({
        next: () => {
          this.loadUsers();
        },
        error: (err: any) => {
          console.error('Error deleting user:', err);
        }
      });
    }
  }

  resetUserPassword(user: User): void {
    if (confirm(`Réinitialiser le mot de passe de ${user.fullName} ?`)) {
      this.userService.resetPassword(user.id).subscribe({
        next: (response: any) => {
          alert(response.message || 'Mot de passe réinitialisé avec succès');
        },
        error: (err: any) => {
          console.error('Error resetting password:', err);
        }
      });
    }
  }

  // Selection methods
  toggleUserSelection(userId: number): void {
    const index = this.selectedUsers.indexOf(userId);
    if (index > -1) {
      this.selectedUsers.splice(index, 1);
    } else {
      this.selectedUsers.push(userId);
    }
    this.updateBulkActionsVisibility();
  }

  isUserSelected(userId: number): boolean {
    return this.selectedUsers.includes(userId);
  }

  toggleSelectAll(): void {
    if (this.isAllSelected()) {
      this.selectedUsers = [];
    } else {
      this.selectedUsers = this.filteredUsers.map(u => u.id);
    }
    this.updateBulkActionsVisibility();
  }

  isAllSelected(): boolean {
    return this.filteredUsers.length > 0 && 
           this.selectedUsers.length === this.filteredUsers.length;
  }

  clearSelection(): void {
    this.selectedUsers = [];
    this.bulkAction = '';
    this.updateBulkActionsVisibility();
  }

  updateBulkActionsVisibility(): void {
    this.showBulkActions = this.selectedUsers.length > 0;
  }

  isIndeterminate(): boolean {
    return this.filteredUsers.length > 0 && 
           this.selectedUsers.length > 0 && 
           this.selectedUsers.length < this.filteredUsers.length;
  }

  // Bulk actions
  executeBulkAction(): void {
    if (!this.bulkAction || this.selectedUsers.length === 0) return;

    let confirmMessage = '';
    switch (this.bulkAction) {
      case 'activate':
        confirmMessage = `Activer ${this.selectedUsers.length} utilisateur(s) ?`;
        break;
      case 'deactivate':
        confirmMessage = `Désactiver ${this.selectedUsers.length} utilisateur(s) ?`;
        break;
      case 'delete':
        confirmMessage = `Supprimer ${this.selectedUsers.length} utilisateur(s) ? Cette action est irréversible.`;
        break;
    }

    if (confirm(confirmMessage)) {
      this.loading = true;
      
      switch (this.bulkAction) {
        case 'activate':
          this.userService.bulkUpdateUsers(this.selectedUsers, { active: true }).subscribe({
            next: () => {
              this.loadUsers();
              this.clearSelection();
              this.loading = false;
            },
            error: () => {
              this.loading = false;
            }
          });
          break;
        case 'deactivate':
          this.userService.bulkUpdateUsers(this.selectedUsers, { active: false }).subscribe({
            next: () => {
              this.loadUsers();
              this.clearSelection();
              this.loading = false;
            },
            error: () => {
              this.loading = false;
            }
          });
          break;
        case 'delete':
          this.userService.bulkDeleteUsers(this.selectedUsers).subscribe({
            next: () => {
              this.loadUsers();
              this.clearSelection();
              this.loading = false;
            },
            error: () => {
              this.loading = false;
            }
          });
          break;
      }
    }
  }

  // ── Export Methods (100% client-side) ────────────────────────────

  exportUsers(format: 'csv' | 'excel'): void {
    const data = this.filteredUsers.length > 0 ? this.filteredUsers : this.users;

    if (data.length === 0) {
      alert('Aucun utilisateur à exporter.');
      return;
    }

    if (format === 'csv') {
      this.exportAsCsv(data);
    } else {
      this.exportAsExcel(data);
    }
  }

  private getExportRows(users: User[]): string[][] {
    return users.map(u => [
      u.fullName || `${u.firstName} ${u.lastName}`,
      u.email,
      u.roles?.map(r => this.getRoleDisplayName(r as any)).join(', ') || '',
      u.active ? 'Actif' : 'Inactif',
      u.badgeNumber || '',
      u.phoneNumber || '',
      this.formatDate(u.lastLogin)
    ]);
  }

  private exportAsCsv(users: User[]): void {
    const headers = ['Nom complet', 'Email', 'Rôle', 'Statut', 'Badge', 'Téléphone', 'Dernière connexion'];
    const rows = this.getExportRows(users);

    // BOM UTF-8 pour Excel
    const BOM = '\uFEFF';
    const csvContent = BOM + [headers, ...rows]
      .map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(';'))
      .join('\r\n');

    this.downloadFile(
      new Blob([csvContent], { type: 'text/csv;charset=utf-8;' }),
      `utilisateurs_${this.todayStr()}.csv`
    );
  }

  private exportAsExcel(users: User[]): void {
    const headers = ['Nom complet', 'Email', 'Rôle', 'Statut', 'Badge', 'Téléphone', 'Dernière connexion'];
    const rows = this.getExportRows(users);

    // Génère un fichier HTML que Excel peut ouvrir (.xls)
    const tableRows = [headers, ...rows]
      .map((row, i) => {
        const tag = i === 0 ? 'th' : 'td';
        const style = i === 0
          ? 'background:#1a56db;color:#fff;font-weight:bold;padding:8px;'
          : (i % 2 === 0 ? 'background:#f0f4f8;padding:6px;' : 'background:#fff;padding:6px;');
        return '<tr>' + row.map(c => `<${tag} style="${style}">${c}</${tag}>`).join('') + '</tr>';
      }).join('');

    const html = `
      <html xmlns:o="urn:schemas-microsoft-com:office:office"
            xmlns:x="urn:schemas-microsoft-com:office:excel"
            xmlns="http://www.w3.org/TR/REC-html40">
      <head><meta charset="UTF-8">
        <!--[if gte mso 9]><xml><x:ExcelWorkbook><x:ExcelWorksheets>
          <x:ExcelWorksheet><x:Name>Utilisateurs</x:Name>
          <x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions>
          </x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml><![endif]-->
      </head>
      <body>
        <h2 style="font-family:Arial;color:#0f172a;">Gestion des Utilisateurs — SkyGuide</h2>
        <p style="font-family:Arial;color:#64748b;">Exporté le ${new Date().toLocaleString('fr-FR')}</p>
        <table border="1" cellpadding="6" cellspacing="0" style="font-family:Arial;font-size:12px;border-collapse:collapse;">
          ${tableRows}
        </table>
      </body></html>`;

    this.downloadFile(
      new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8;' }),
      `utilisateurs_${this.todayStr()}.xls`
    );
  }

  private downloadFile(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

  private todayStr(): string {
    return new Date().toISOString().slice(0, 10);
  }



  closeModal(): void {
    this.showCreateModal = false;
    this.editingUser = null;
    this.userForm.reset({
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      phoneNumber: '',
      badgeNumber: '',
      roles: []
    });
  }

  isFormValid(): boolean {
    if (this.editingUser) {
      return this.userForm.valid && (this.userForm.get('email')?.valid ?? false);
    }
    return this.userForm.valid && (this.userForm.get('password')?.valid ?? false);
  }

  openAddUserModal(): void {
    this.editingUser = null;
    this.userForm.reset({
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      phoneNumber: '',
      badgeNumber: '',
      roles: []
    });
    this.showCreateModal = true;
  }
}
