import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators, ValidationErrors } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../services/user.service';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { User, UpdateUserRequest, JwtResponse } from '../../models/user.model';
import { NotificationType } from '../../models/notification.model';
import { environment } from '../../../environments/environment';

interface ActivityItem {
  type: 'login' | 'update' | 'password' | 'security';
  title: string;
  description: string;
  timestamp: Date;
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, DatePipe],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {
  currentUser: User | null = null;
  profileForm!: FormGroup;
  passwordForm!: FormGroup;
  
  editMode = false;
  showPasswordModal = false;
  isChangingPassword = false;
  loading = false;

  // Alerte inline dans la modale mot de passe
  passwordAlert: { type: 'success' | 'error'; message: string } | null = null;

  passwordStrength = 'weak';
  recentActivities: ActivityItem[] = [];

  constructor(
    private authService: AuthService,
    private notificationService: NotificationService,
    private userService: UserService,
    private http: HttpClient,
    private fb: FormBuilder
  ) { }

  ngOnInit(): void {
    this.loadCurrentUser();
    this.initializeForms();
    this.loadRecentActivities();
  }

  loadCurrentUser(): void {
    const jwtResponse = this.authService.getCurrentUser();
    if (jwtResponse) {
      // Load full user data from UserService
      this.userService.getUserById(jwtResponse.id).subscribe({
        next: (user: User) => {
          this.currentUser = user;
          this.profileForm.patchValue({
            firstName: user.firstName,
            lastName: user.lastName,
            email: user.email,
            phoneNumber: user.phoneNumber,
            badgeNumber: user.badgeNumber
          });
        },
        error: (error: any) => {
          console.error('Error loading user data:', error);
        }
      });
    }
  }

  loadRecentActivities(): void {
    // Mock activities - should come from backend
    this.recentActivities = [
      {
        type: 'login',
        title: 'Connexion réussie',
        description: 'Connexion depuis l\'adresse IP 192.168.1.100',
        timestamp: new Date(Date.now() - 3600000) // 1 hour ago
      },
      {
        type: 'update',
        title: 'Profil mis à jour',
        description: 'Modification des informations personnelles',
        timestamp: new Date(Date.now() - 86400000) // 1 day ago
      },
      {
        type: 'password',
        title: 'Mot de passe changé',
        description: 'Votre mot de passe a été modifié avec succès',
        timestamp: new Date(Date.now() - 604800000) // 1 week ago
      }
    ];
  }

  initializeForms(): void {
    this.profileForm = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phoneNumber: [''],
      badgeNumber: [{ value: '', disabled: true }]
    });

    this.passwordForm = this.fb.group({
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required]
    }, { validators: this.passwordMatchValidator });

    // Listen to password changes for strength indicator
    this.passwordForm.get('newPassword')?.valueChanges.subscribe(password => {
      this.calculatePasswordStrength(password);
    });
  }

  passwordMatchValidator(form: FormGroup): ValidationErrors | null {
    const newPassword = form.get('newPassword')?.value;
    const confirmPassword = form.get('confirmPassword')?.value;

    if (newPassword !== confirmPassword) {
      return { passwordMismatch: true };
    }
    return null;
  }

  calculatePasswordStrength(password: string): void {
    if (!password) {
      this.passwordStrength = 'weak';
      return;
    }

    let strength = 0;
    if (password.length >= 8) strength++;
    if (password.length >= 12) strength++;
    if (/[a-z]/.test(password)) strength++;
    if (/[A-Z]/.test(password)) strength++;
    if (/[0-9]/.test(password)) strength++;
    if (/[^a-zA-Z0-9]/.test(password)) strength++;

    if (strength <= 2) this.passwordStrength = 'weak';
    else if (strength <= 4) this.passwordStrength = 'medium';
    else this.passwordStrength = 'strong';
  }

  getStrengthText(): string {
    switch (this.passwordStrength) {
      case 'weak': return 'Faible';
      case 'medium': return 'Moyen';
      case 'strong': return 'Fort';
      default: return '';
    }
  }

  toggleEditMode(): void {
    this.editMode = !this.editMode;
    if (this.editMode) {
      this.profileForm.enable();
    } else {
      this.profileForm.disable();
      this.cancelEdit();
    }
  }

  cancelEdit(): void {
    this.editMode = false;
    if (this.currentUser) {
      this.profileForm.patchValue({
        firstName: this.currentUser.firstName,
        lastName: this.currentUser.lastName,
        email: this.currentUser.email,
        phoneNumber: this.currentUser.phoneNumber || ''
      });
    }
  }

  saveProfile(): void {
    if (this.profileForm.valid && this.currentUser) {
      this.loading = true;

      const updateData = {
        firstName: this.profileForm.value.firstName,
        lastName: this.profileForm.value.lastName,
        email: this.profileForm.value.email,
        phoneNumber: this.profileForm.value.phoneNumber
      };

      this.userService.updateUser(this.currentUser.id, updateData).subscribe({
        next: (updatedUser: User) => {
          this.currentUser = updatedUser;
          this.loading = false;
          this.editMode = false;
          
          // Update auth service with new user data
          this.authService.setCurrentUser(updatedUser);
          
          this.notificationService.addNotification(
            'Profil mis à jour',
            'Vos informations personnelles ont été mises à jour avec succès',
            NotificationType.SYSTEM
          );
        },
        error: (error: any) => {
          this.loading = false;
          this.notificationService.addNotification(
            'Erreur',
            'Impossible de mettre à jour votre profil',
            NotificationType.ALERT
          );
        }
      });
    }
  }

  changePassword(): void {
    if (this.passwordForm.invalid) return;
    this.isChangingPassword = true;
    this.passwordAlert = null;

    const payload = {
      currentPassword: this.passwordForm.value.currentPassword,
      newPassword:     this.passwordForm.value.newPassword
    };

    this.http.post<{ message: string }>(
      `${environment.apiUrl}/auth/change-password`, payload
    ).subscribe({
      next: () => {
        this.isChangingPassword = false;
        this.passwordAlert = {
          type: 'success',
          message: '✅ Mot de passe modifié avec succès !'
        };
        this.passwordForm.reset();
        // Fermer la modale après 2s
        setTimeout(() => {
          this.showPasswordModal = false;
          this.passwordAlert = null;
        }, 2000);
      },
      error: (err) => {
        this.isChangingPassword = false;
        const msg = err?.error?.message || 'Erreur lors du changement de mot de passe';
        this.passwordAlert = { type: 'error', message: '❌ ' + msg };
      }
    });
  }

  getRoleDisplayName(role: string): string {
    const roleMap: { [key: string]: string } = {
      'ADMIN': 'Administrateur',
      'SUPERVISEUR': 'Superviseur',
      'ATC': 'Contrôleur Aérien',
      'PISTE': 'Personnel Piste',
      'AIS': 'Information Aéronautique',
      'RH_FORMATION': 'RH & Formation',
      'QUALITE': 'Qualité',
      'CONSULTATION': 'Consultant'
    };
    return roleMap[role] || role;
  }
}
