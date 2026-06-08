// ═══════════════════════════════════════════════════════════════
// POLYFILL GLOBAL — requis pour SockJS et librairies Node.js
// SockJS/STOMP utilisent la variable `global` qui n'existe que
// dans Node.js. Ce polyfill la mappe sur `window` pour le browser.
// ═══════════════════════════════════════════════════════════════
(window as any).global = window;

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
