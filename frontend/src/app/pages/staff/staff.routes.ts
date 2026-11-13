import type { Routes } from '@angular/router';
import { StaffWorkspacePage } from './staff-workspace/staff-workspace-page';

/** Staff feature, mounted at /staff and lazy-loaded from app.routes.ts. */
export const routes: Routes = [{ path: '', component: StaffWorkspacePage }];
