// Claims, mirroring the backend DTOs in claim/web/dto.
import type { Currency, Market } from './common.model';

export type ClaimStatus = 'SUBMITTED' | 'IN_REVIEW' | 'INFO_REQUESTED' | 'SETTLED' | 'REJECTED';
export type ClaimType = 'MOTOR' | 'PROPERTY';

export interface InfoRequest {
  id: string;
  question: string;
  requestedBy: string;
  requestedAt: string;
  answer: string | null;
  answeredAt: string | null;
}

export interface ClaimSummary {
  id: string;
  claimNumber: string;
  claimantName: string;
  claimType: ClaimType;
  market: Market;
  currency: Currency;
  claimedAmount: number;
  status: ClaimStatus;
  assignedHandler: string | null;
  submittedAt: string;
  updatedAt: string;
}

export interface ClaimDetail extends ClaimSummary {
  policyNumber: string;
  incidentDate: string;
  incidentDescription: string;
  incidentDetails: Record<string, string>;
  settledAmount: number | null;
  decisionNote: string | null;
  decidedAt: string | null;
  version: number;
  infoRequests: InfoRequest[];
}

export interface SubmitClaimRequest {
  policyNumber: string;
  claimType: ClaimType;
  market: Market;
  currency: Currency;
  incidentDate: string;
  incidentDescription: string;
  incidentDetails: Record<string, string>;
  claimedAmount: number;
}

/** One entry of the staff audit trail, built by the Kafka consumer (exposure/AuditEntryView). */
export interface AuditEntry {
  eventId: string;
  eventType: 'CLAIM_SUBMITTED' | 'CLAIM_STATUS_CHANGED' | 'CLAIM_DECIDED';
  previousStatus: ClaimStatus | null;
  newStatus: ClaimStatus;
  actor: string;
  occurredAt: string;
}

export interface QueueFilter {
  market?: Market | null;
  status?: ClaimStatus | null;
  mine?: boolean;
}
