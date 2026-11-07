import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/**
 * Validateurs métier partagés par tous les formulaires de la plateforme.
 * Les mêmes règles sont appliquées côté backend (Bean Validation) : le front
 * guide l'utilisateur, le serveur reste la référence.
 */
export const PATTERNS = {
  /** Lettres (accents inclus), espaces, tiret, apostrophe */
  personName: /^[\p{L}][\p{L} '\-]*$/u,
  /** +216 20 123 456, 20123456, +41 79 123 45 67 … */
  phone: /^\+?[0-9][0-9 ]{6,18}[0-9]$/,
  /** Numéro de vol OACI/IATA : 2-3 caractères compagnie + 1-4 chiffres + suffixe optionnel (TU712, BJ210A, AF1234) */
  flightNumber: /^[A-Z0-9]{2,3}\s?\d{1,4}[A-Z]?$/,
  /** Code aéroport IATA (3 lettres) ou OACI (4 lettres) */
  airportCode: /^[A-Z]{3,4}$/,
  /** Nom de ville/aéroport en clair */
  placeName: /^[\p{L}0-9][\p{L}0-9 '().\-/]*$/u,
  /** Immatriculation : TS-IME, F-GKXA, N12345, 9H-ABC */
  registration: /^[A-Z0-9]{1,2}-?[A-Z0-9]{1,5}$/,
  /** Piste : 01 à 36 + L/C/R optionnel, ex 09, 27L, 01R/19L */
  runway: /^(0[1-9]|[12][0-9]|3[0-6])[LCR]?(\/(0[1-9]|[12][0-9]|3[0-6])[LCR]?)?$/,
  /** Porte : A12, 7, B3C */
  gate: /^[A-Z]{0,2}\d{1,3}[A-Z]?$/,
  /** Badge / matricule : lettres, chiffres, tirets */
  code: /^[A-Za-z0-9][A-Za-z0-9\-_/]*$/,
  /** Texte libre « sûr » (pas de balises) */
  safeText: /^[^<>]*$/
};

export class AppValidators {

  /** Obligatoire et pas uniquement des espaces */
  static requiredTrimmed(control: AbstractControl): ValidationErrors | null {
    const v = control.value;
    if (v === null || v === undefined) return { required: true };
    if (typeof v === 'string' && v.trim().length === 0) return { required: true };
    if (Array.isArray(v) && v.length === 0) return { required: true };
    return null;
  }

  static pattern(regex: RegExp, errorKey: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const v = control.value;
      if (v === null || v === undefined || v === '') return null;
      return regex.test(String(v).trim()) ? null : { [errorKey]: true };
    };
  }

  static personName = AppValidators.pattern(PATTERNS.personName, 'personName');
  static phone = AppValidators.pattern(PATTERNS.phone, 'phone');
  static flightNumber = AppValidators.pattern(PATTERNS.flightNumber, 'flightNumber');
  static airportCode = AppValidators.pattern(PATTERNS.airportCode, 'airportCode');
  static placeName = AppValidators.pattern(PATTERNS.placeName, 'placeName');
  static registration = AppValidators.pattern(PATTERNS.registration, 'registration');
  static runway = AppValidators.pattern(PATTERNS.runway, 'runway');
  static gate = AppValidators.pattern(PATTERNS.gate, 'gate');
  static code = AppValidators.pattern(PATTERNS.code, 'code');
  static safeText = AppValidators.pattern(PATTERNS.safeText, 'safeText');

  /** E-mail plus strict que Validators.email (exige un domaine avec extension) */
  static email(control: AbstractControl): ValidationErrors | null {
    const v = (control.value || '').trim();
    if (!v) return null;
    return /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/.test(v) ? null : { email: true };
  }

  /** Mot de passe robuste : 8+ caractères, minuscule, majuscule, chiffre, caractère spécial */
  static strongPassword(control: AbstractControl): ValidationErrors | null {
    const v: string = control.value || '';
    if (!v) return null;
    const missing: string[] = [];
    if (v.length < 8) missing.push('8 caractères');
    if (!/[a-z]/.test(v)) missing.push('une minuscule');
    if (!/[A-Z]/.test(v)) missing.push('une majuscule');
    if (!/\d/.test(v)) missing.push('un chiffre');
    if (!/[^A-Za-z0-9]/.test(v)) missing.push('un caractère spécial');
    if (/\s/.test(v)) missing.push('aucun espace');
    return missing.length ? { strongPassword: { missing } } : null;
  }

  /** Date/heure dans une plage raisonnable autour d'aujourd'hui (évite les fautes de frappe d'année) */
  static dateWithinDays(pastDays: number, futureDays: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const d = new Date(control.value);
      if (isNaN(d.getTime())) return { dateInvalid: true };
      const now = Date.now();
      if (d.getTime() < now - pastDays * 86400000) return { dateTooOld: { days: pastDays } };
      if (d.getTime() > now + futureDays * 86400000) return { dateTooFar: { days: futureDays } };
      return null;
    };
  }

  /** Groupe : deux champs doivent être identiques (confirmation mot de passe) */
  static match(field: string, confirmField: string, errorKey = 'mismatch'): ValidatorFn {
    return (group: AbstractControl): ValidationErrors | null => {
      const a = group.get(field)?.value;
      const b = group.get(confirmField)?.value;
      return a && b && a !== b ? { [errorKey]: true } : null;
    };
  }

  /** Groupe : deux champs doivent être différents (origine ≠ destination, ancien ≠ nouveau mot de passe) */
  static different(field: string, otherField: string, errorKey = 'sameValue'): ValidatorFn {
    return (group: AbstractControl): ValidationErrors | null => {
      const a = String(group.get(field)?.value ?? '').trim().toUpperCase();
      const b = String(group.get(otherField)?.value ?? '').trim().toUpperCase();
      return a && b && a === b ? { [errorKey]: true } : null;
    };
  }
}

/** Messages d'erreur en français, par clé de validateur */
export function validationMessage(errors: ValidationErrors | null | undefined, label = 'Ce champ'): string {
  if (!errors) return '';
  const k = Object.keys(errors)[0];
  const e = errors[k];
  switch (k) {
    case 'required':       return `${label} est obligatoire.`;
    case 'minlength':      return `Minimum ${e.requiredLength} caractères (actuellement ${e.actualLength}).`;
    case 'maxlength':      return `Maximum ${e.requiredLength} caractères.`;
    case 'min':            return `La valeur minimale est ${e.min}.`;
    case 'max':            return `La valeur maximale est ${e.max}.`;
    case 'email':          return 'Adresse e-mail invalide (ex. prenom.nom@domaine.tn).';
    case 'personName':     return 'Lettres, espaces, tirets et apostrophes uniquement.';
    case 'phone':          return 'Numéro invalide (ex. +216 20 123 456).';
    case 'flightNumber':   return 'Format attendu : code compagnie + numéro (ex. TU712).';
    case 'airportCode':    return 'Code IATA (3 lettres) ou OACI (4 lettres), ex. TUN ou DTTA.';
    case 'placeName':      return 'Caractères non autorisés.';
    case 'registration':   return 'Immatriculation invalide (ex. TS-IME).';
    case 'runway':         return 'Piste invalide : 01 à 36 + L/C/R (ex. 09, 27L).';
    case 'gate':           return 'Porte invalide (ex. A12).';
    case 'code':           return 'Lettres, chiffres et tirets uniquement.';
    case 'safeText':       return 'Les caractères < et > ne sont pas autorisés.';
    case 'strongPassword': return `Le mot de passe doit contenir : ${e.missing.join(', ')}.`;
    case 'dateInvalid':    return 'Date invalide.';
    case 'dateTooOld':     return `La date ne peut pas être antérieure de plus de ${e.days} jours.`;
    case 'dateTooFar':     return `La date ne peut pas dépasser ${e.days} jours dans le futur.`;
    case 'mismatch':       return 'Les valeurs ne correspondent pas.';
    case 'sameValue':      return 'Les deux valeurs doivent être différentes.';
    case 'pattern':        return 'Format invalide.';
    case 'server':         return typeof e === 'string' ? e : 'Valeur refusée par le serveur.';
    default:               return 'Valeur invalide.';
  }
}

/** Marque tous les champs comme touchés et place le focus sur le premier champ invalide */
export function focusFirstInvalid(form: FormGroup, root: ParentNode = document): void {
  form.markAllAsTouched();
  setTimeout(() => {
    const el = root.querySelector<HTMLElement>('form .ng-invalid[formControlName], form .ng-invalid [formControlName].ng-invalid');
    el?.focus();
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  });
}

/** Normalise une saisie en majuscules sans espaces superflus */
export function toUpperTrim(v: unknown): string {
  return String(v ?? '').trim().toUpperCase();
}

export { Validators };
