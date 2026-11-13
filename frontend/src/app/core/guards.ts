import { inject } from '@angular/core';
import { Router, type CanActivateFn, type UrlTree } from '@angular/router';
import type { Role } from '../models';
import { SessionService } from '../services/session.service';
import { homePath } from './labels';

/** Lets the route through only for `role`; the other role is sent to its own home page. */
export function roleGuard(role: Role): CanActivateFn {
  return (): boolean | UrlTree => {
    const current = inject(SessionService).identity().role;
    return current === role ? true : inject(Router).parseUrl(homePath(current));
  };
}

/** Unknown paths and "/" go to the current role's home page. */
export function homeRedirect(): string {
  return homePath(inject(SessionService).identity().role);
}
