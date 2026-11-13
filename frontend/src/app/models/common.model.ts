// Identity and error shapes shared by every page.

/** Who the app is acting as. Sent to the backend as X-User-Role / X-User-Name. */
export type Role = 'CLAIMANT' | 'STAFF';

export interface Identity {
  role: Role;
  name: string;
  /** Claimant: default market for new claims. Staff: default market filter for the queue. */
  market: Market;
}

/** RFC 9457 Problem Details, as returned by the backend's GlobalExceptionHandler. */
export interface ProblemDetail {
  title: string;
  status: number;
  detail: string;
  /** Field errors on 400. */
  errors?: Record<string, string>;
}

export type Market = 'SG' | 'TH' | 'MY' | 'HK' | 'AU' | 'JP';
export type Currency = 'SGD' | 'THB' | 'MYR' | 'HKD' | 'AUD' | 'JPY' | 'USD';
