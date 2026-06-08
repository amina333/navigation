import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/pages/login/login.component').then(m => m.LoginComponent),
    canActivate: [guestGuard]
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./layout/main-layout/main-layout.component').then(m => m.MainLayoutComponent),
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./components/dashboard/role-based-dashboard/role-based-dashboard.component').then(m => m.RoleBasedDashboardComponent)
      },
      {
        path: 'flights',
        loadComponent: () => import('./components/flights/flight-dashboard/flight-dashboard.component').then(m => m.FlightDashboardComponent)
      },
      {
        path: 'users',
        loadComponent: () => import('./features/users/user-management/user-management.component').then(m => m.UserManagementComponent)
      },
      {
        path: 'chat',
        loadComponent: () => import('./components/chat/chat-room/chat-room.component').then(m => m.ChatRoomComponent)
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent)
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent)
      },
      {
        path: 'notifications',
        loadComponent: () => import('./features/notifications/notifications.component').then(m => m.NotificationsComponent)
      },
      {
        path: 'activities',
        loadComponent: () => import('./features/activities/activities.component').then(m => m.ActivitiesComponent)
      }

    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
