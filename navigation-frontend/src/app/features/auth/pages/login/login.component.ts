import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../../services/auth.service';
import { AppValidators } from '../../../../shared/validation/app-validators';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  loginForm: FormGroup;
  isLoading = false;
  errorMessage = '';
  showPassword = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.loginForm = this.fb.group({
      email: ['', [AppValidators.requiredTrimmed, AppValidators.email, Validators.maxLength(100)]],
      password: ['', [Validators.required, Validators.maxLength(100)]]
    });
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    const email = (this.loginForm.value.email || '').trim().toLowerCase();
    const password = this.loginForm.value.password;

    this.authService.login({ email, password }).subscribe({
      next: (res) => {
        this.isLoading = false;
        if (res.twoFactorRequired) {
          // Mot de passe correct : code envoyé par e-mail
          this.twoFactorEmail = res.email || email;
          this.twoFactorCode = '';
          setTimeout(() => document.getElementById('tfa-code')?.focus(), 50);
          return;
        }
        this.router.navigate(['/dashboard']);
      },
      error: (err: any) => {
        this.isLoading = false;
        this.errorMessage = err?.status === 0
          ? 'Serveur injoignable. Vérifiez que le backend est démarré.'
          : (err.error?.message || (err?.status === 401 ? 'Adresse e-mail ou mot de passe incorrect.' : 'Échec de la connexion. Veuillez réessayer.'));
      }
    });
  }

  // ── Double authentification ────────────────────────
  twoFactorEmail = '';
  twoFactorCode = '';

  onCodeInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.twoFactorCode = input.value.replace(/\D/g, '').slice(0, 6);
    input.value = this.twoFactorCode;
    this.errorMessage = '';
    if (this.twoFactorCode.length === 6) this.verifyCode();
  }

  verifyCode(): void {
    if (this.twoFactorCode.length !== 6 || this.isLoading) return;
    this.isLoading = true;
    this.authService.verifyTwoFactor(this.twoFactorEmail, this.twoFactorCode).subscribe({
      next: () => { this.isLoading = false; this.router.navigate(['/dashboard']); },
      error: (err: any) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Code invalide.';
        this.twoFactorCode = '';
        const el = document.getElementById('tfa-code') as HTMLInputElement | null;
        if (el) { el.value = ''; el.focus(); }
      }
    });
  }

  /** Renvoyer un code = refaire l'étape mot de passe (le formulaire est conservé) */
  resendCode(): void {
    this.twoFactorEmail = '';
    this.onSubmit();
  }

  cancelTwoFactor(): void {
    this.twoFactorEmail = '';
    this.twoFactorCode = '';
    this.errorMessage = '';
  }
}
