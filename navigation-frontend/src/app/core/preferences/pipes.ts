import { Pipe, PipeTransform } from '@angular/core';
import { PreferencesService } from './preferences.service';

/** Traduction : {{ 'nav.flights' | t }} — se met à jour au changement de langue */
@Pipe({ name: 't', standalone: true, pure: false })
export class TranslatePipe implements PipeTransform {
  constructor(private prefs: PreferencesService) {}
  transform(key: string, params?: Record<string, string | number>): string {
    return this.prefs.t(key, params);
  }
}

/** Date dans le fuseau horaire et la langue choisis : {{ msg.sentAt | appDate:'time' }} */
@Pipe({ name: 'appDate', standalone: true, pure: false })
export class AppDatePipe implements PipeTransform {
  constructor(private prefs: PreferencesService) {}
  transform(value: string | number | Date | null | undefined, style: 'time' | 'datetime' | 'date' | 'short' = 'datetime'): string {
    return this.prefs.formatDate(value, style);
  }
}

/** « il y a 5 min » : {{ a.timestamp | ago }} */
@Pipe({ name: 'ago', standalone: true, pure: false })
export class AgoPipe implements PipeTransform {
  constructor(private prefs: PreferencesService) {}
  transform(value: string | Date | null | undefined): string {
    return value ? this.prefs.relative(value) : '';
  }
}
