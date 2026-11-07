import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl } from '@angular/forms';
import { validationMessage } from './app-validators';

/**
 * Affiche le message d'erreur d'un champ une fois touché / modifié.
 * Usage : <app-field-error [control]="form.get('email')" label="L'e-mail"></app-field-error>
 */
@Component({
  selector: 'app-field-error',
  standalone: true,
  imports: [CommonModule],
  template: `
    <small class="app-field-error" role="alert" *ngIf="message">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true">
        <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      {{ message }}
    </small>
  `,
  styles: [`
    .app-field-error {
      display: flex;
      align-items: center;
      gap: 0.3rem;
      margin-top: 0.3rem;
      font-size: 0.75rem;
      line-height: 1.3;
      color: #dc2626;
    }
    svg { flex-shrink: 0; }
  `]
})
export class FieldErrorComponent {
  @Input() control: AbstractControl | null | undefined;
  @Input() label = 'Ce champ';
  /** Erreur portée par le groupe parent (ex. 'sameValue' pour origine = destination) */
  @Input() groupError?: string;
  @Input() group: AbstractControl | null | undefined;
  @Input() groupMessage?: string;

  get message(): string {
    const c = this.control;
    if (!c || !(c.touched || c.dirty)) return '';
    if (c.errors) return validationMessage(c.errors, this.label);
    if (this.groupError && this.group?.hasError(this.groupError)) {
      return this.groupMessage || validationMessage({ [this.groupError]: true }, this.label);
    }
    return '';
  }
}
