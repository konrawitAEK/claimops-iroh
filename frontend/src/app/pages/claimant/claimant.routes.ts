import type { Routes } from '@angular/router';
import { SubmitClaimPage } from './submit-claim/submit-claim-page';
import { TrackClaimPage } from './track-claim/track-claim-page';

/** Claimant feature, mounted at /claim and lazy-loaded from app.routes.ts. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'new' },
  { path: 'new', component: SubmitClaimPage },
  { path: 'track', component: TrackClaimPage },
];
