import type { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SessionService } from '../services/session.service';
import { API_URL } from './api';

/**
 * Sends the selected identity as X-User-Role / X-User-Name on API calls only.
 *
 * The backend uses these headers in place of a token (see HeaderAuthInterceptor). Swapping in OAuth later means
 * replacing this interceptor with one that attaches a bearer token; services and pages stay unchanged.
 */
export const identityInterceptor: HttpInterceptorFn = (req, next) => {
  const toApi = req.url === API_URL || req.url.startsWith(API_URL + '/');
  if (!toApi) return next(req);
  const identity = inject(SessionService).identity();
  return next(req.clone({ setHeaders: { 'X-User-Role': identity.role, 'X-User-Name': identity.name } }));
};
