// Staff dashboard, mirroring exposure/DashboardView.
import type { ClaimStatus, ClaimType } from './claim.model';
import type { Currency, Market } from './common.model';

export interface Exposure {
  market: Market;
  claimType: ClaimType;
  currency: Currency;
  openClaims: number;
  outstandingAmount: number;
  settledAmount: number;
  updatedAt: string;
}

/** One officer's track record; avgHoursToDecision is null until they've decided a claim. */
export interface HandlerPerformance {
  handler: string;
  openClaims: number;
  settled: number;
  rejected: number;
  avgHoursToDecision: number | null;
}

export interface Workload {
  byStatus: Record<ClaimStatus, number>;
  openByHandler: { handler: string; openClaims: number }[];
  performance: HandlerPerformance[];
}

export interface Dashboard {
  exposures: Exposure[];
  workload: Workload;
}
