import { Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../../environments/environment';
import { AppValidators } from '../../../../shared/validation/app-validators';
import { FieldErrorComponent } from '../../../../shared/validation/field-error.component';

type Step = 'email' | 'code' | 'password' | 'done';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FieldErrorComponent],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['../login/login.component.scss', './forgot-password.component.scss']
})
export class ForgotPasswordComponent implements OnInit, OnDestroy {

  @ViewChildren('digit') digitInputs!: QueryList<ElementRef<HTMLInputElement>>;

  readonly API = `${environment.apiUrl}/auth`;
  step: Step = 'email';
  digits: string[] = ['', '', '', '', '', ''];
  emailForm: FormGroup;
  passwordForm: FormGroup;
  loading = false;
  error = '';
  info = '';
  resendIn = 0;
  showPassword = false;
  private timer: any;

  constructor(private fb: FormBuilder, private http: HttpClient, private route: ActivatedRoute, private router: Router) {
    this.emailForm = this.fb.group({
      email: ['', [AppValidators.requiredTrimmed, AppValidators.email, Validators.maxLength(100)]]
    });
    this.passwordForm = this.fb.group({
      newPassword: ['', [Validators.required, AppValidators.strongPassword, Validators.maxLength(100)]],
      confirmPassword: ['', Validators.required]
    }, { validators: AppValidators.match('newPassword', 'confirmPassword', 'mismatch') });
  }

  ngOnInit(): void {
    // Lien reçu par e-mail : ?email=… → directement à la saisie du code
    const email = this.route.snapshot.queryParamMap.get('email');
    if (email) {
      this.emailForm.patchValue({ email });
      this.step = 'code';
      this.info = `Saisissez le code à 6 chiffres envoyé à ${email}.`;
      setTimeout(() => this.focusDigit(0), 50);
    }
  }

  ngOnDestroy(): void { clearInterval(this.timer); }

  get email(): string { return (this.emailForm.value.email || '').trim().toLowerCase(); }
  get code(): string { return this.digits.join(''); }
  get codeComplete(): boolean { return /^\d{6}$/.test(this.code); }

  ef(n: string) { return this.emailForm.get(n); }
  pf(n: string) { return this.passwordForm.get(n); }

  get strength(): { score: number; label: string } {
    const v: string = this.passwordForm.value.newPassword || '';
    let s = 0;
    if (v.length >= 8) s++;
    if (v.length >= 12) s++;
    if (/[a-z]/.test(v) && /[A-Z]/.test(v)) s++;
    if (/\d/.test(v)) s++;
    if (/[^A-Za-z0-9]/.test(v)) s++;
    const labels = ['Très faible', 'Faible', 'Moyen', 'Bon', 'Fort', 'Excellent'];
    return { score: s, label: v ? labels[s] : '' };
  }

  // ── Étape 1 : e-mail ──────────────────────────────
  sendCode(): void {
    this.error = '';
    if (this.emailForm.invalid) { this.emailForm.markAllAsTouched(); return; }
    this.loading = true;
    this.http.post<{ message: string }>(`${this.API}/forgot-password`, { email: this.email }).subscribe({
      next: r => {
        this.loading = false;
        this.info = r.message;
        this.step = 'code';
        this.digits = ['', '', '', '', '', ''];
        this.startCooldown();
        setTimeout(() => this.focusDigit(0), 50);
      },
      error: err => { this.loading = false; this.error = this.msg(err); }
    });
  }

  resend(): void {
    if (this.resendIn > 0) return;
    this.sendCode();
  }

  private startCooldown(): void {
    this.resendIn = 60;
    clearInterval(this.timer);
    this.timer = setInterval(() => {
      this.resendIn--;
      if (this.resendIn <= 0) clearInterval(this.timer);
    }, 1000);
  }

  // ── Étape 2 : code ────────────────────────────────
  onDigitInput(i: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const v = input.value.replace(/\D/g, '');
    if (v.length > 1) {
      // Collage de plusieurs chiffres
      this.fillFrom(i, v);
      return;
    }
    this.digits[i] = v;
    input.value = v;
    this.error = '';
    if (v && i < 5) this.focusDigit(i + 1);
    if (this.codeComplete) this.verifyCode();
  }

  onDigitKeydown(i: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace' && !this.digits[i] && i > 0) {
      this.digits[i - 1] = '';
      this.focusDigit(i - 1);
      event.preventDefault();
    } else if (event.key === 'ArrowLeft' && i > 0) {
      this.focusDigit(i - 1);
    } else if (event.key === 'ArrowRight' && i < 5) {
      this.focusDigit(i + 1);
    }
  }

  onPaste(event: ClipboardEvent): void {
    const text = (event.clipboardData?.getData('text') || '').replace(/\D/g, '');
    if (!text) return;
    event.preventDefault();
    this.fillFrom(0, text);
  }

  private fillFrom(start: number, value: string): void {
    for (let k = 0; k < value.length && start + k < 6; k++) this.digits[start + k] = value[k];
    const inputs = this.digitInputs.toArray();
    this.digits.forEach((d, idx) => { if (inputs[idx]) inputs[idx].nativeElement.value = d; });
    this.focusDigit(Math.min(start + value.length, 5));
    if (this.codeComplete) this.verifyCode();
  }

  private focusDigit(i: number): void {
    const el = this.digitInputs?.toArray()[i]?.nativeElement;
    el?.focus();
    el?.select();
  }

  verifyCode(): void {
    if (!this.codeComplete) { this.error = 'Saisissez les 6 chiffres du code.'; return; }
    if (this.loading) return;
    this.loading = true;
    this.error = '';
    this.http.post<{ message: string }>(`${this.API}/verify-reset-code`, { email: this.email, code: this.code }).subscribe({
      next: () => {
        this.loading = false;
        this.step = 'password';
        this.info = '';
      },
      error: err => {
        this.loading = false;
        this.error = this.msg(err);
        this.digits = ['', '', '', '', '', ''];
        this.digitInputs.forEach(d => d.nativeElement.value = '');
        setTimeout(() => this.focusDigit(0));
      }
    });
  }

  // ── Étape 3 : nouveau mot de passe ────────────────
  resetPassword(): void {
    this.error = '';
    if (this.passwordForm.invalid) { this.passwordForm.markAllAsTouched(); return; }
    this.loading = true;
    this.http.post<{ message: string }>(`${this.API}/reset-password`, {
      email: this.email, code: this.code, newPassword: this.passwordForm.value.newPassword
    }).subscribe({
      next: r => { this.loading = false; this.info = r.message; this.step = 'done'; },
      error: err => {
        this.loading = false;
        this.error = this.msg(err);
        // Code expiré / bloqué entre-temps → retour à la saisie du code
        if (/code|tentative|expir/i.test(this.error)) this.step = 'code';
      }
    });
  }

  goLogin(): void { this.router.navigate(['/login']); }

  backToEmail(): void {
    this.step = 'email';
    this.error = '';
    this.info = '';
  }

  private msg(err: any): string {
    if (err?.status === 0) return 'Serveur injoignable. Vérifiez que le backend est démarré.';
    return err?.error?.message || 'Une erreur est survenue. Réessayez.';
  }
}
