import type { Routes } from '@angular/router';
import { AppLayout } from './components/app-layout/app-layout';
import { homeRedirect, roleGuard } from './core/guards';

/**
 * Top-level routes. Each feature owns its child routes in `pages/<feature>/<feature>.routes.ts`, loaded on demand,
 * and its role guard sits here at the mount point.
 */
export const routes: Routes = [
  {
    path: '',
    component: AppLayout,
    children: [
      { path: '', pathMatch: 'full', redirectTo: homeRedirect },
      {
        path: 'claim',
        canActivate: [roleGuard('CLAIMANT')],
        loadChildren: () => import('./pages/claimant/claimant.routes').then((m) => m.routes),
      },
      {
        path: 'staff',
        canActivate: [roleGuard('STAFF')],
        loadChildren: () => import('./pages/staff/staff.routes').then((m) => m.routes),
      },
    ],
  },
  { path: '**', redirectTo: homeRedirect },
];
