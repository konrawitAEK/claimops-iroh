import { HttpErrorResponse } from '@angular/common/http';
import type { ProblemDetail } from '../../models';

function isProblem(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null && typeof (value as ProblemDetail).title === 'string';
}

/** The backend's Problem Details behind `err`, or null (network failure, or a body that is not one). */
export function problemOf(err: unknown): ProblemDetail | null {
  const body: unknown = err instanceof HttpErrorResponse ? err.error : null;
  return isProblem(body) ? body : null;
}

/** HTTP status of a failed call, 0 when the API could not be reached. */
export function statusOf(err: unknown): number {
  return err instanceof HttpErrorResponse ? err.status : 0;
}

/** Human-readable text for a failed call: the server's detail and field errors, or a network message. */
export function errorText(err: unknown, fallback = 'Something went wrong'): string {
  if (statusOf(err) === 0) return 'Cannot reach the API. Is the backend running?';
  const problem = problemOf(err);
  if (!problem) return fallback;
  const fields = problem.errors ? Object.entries(problem.errors).map(([f, m]) => `${f} ${m}`) : [];
  return fields.length ? `${problem.detail} ${fields.join('; ')}` : problem.detail;
}
