import { STATUS_LABELS } from '../core/labels';
import type { AuditEntry, ClaimDetail, ClaimStatus } from '../models';

/** SETTLED and REJECTED are final: nothing can change the claim afterwards. */
export function isDecided(status: ClaimStatus): boolean {
  return status === 'SETTLED' || status === 'REJECTED';
}

/** The question the claimant still has to answer, if any. */
export function openQuestion(claim: ClaimDetail): string | undefined {
  return claim.infoRequests.find((r) => !r.answeredAt)?.question;
}

/** One audit entry as text, e.g. "In review → Settled". */
export function describeAuditEntry(entry: AuditEntry): string {
  const to = STATUS_LABELS[entry.newStatus];
  return entry.previousStatus ? `${STATUS_LABELS[entry.previousStatus]} → ${to}` : `Submitted (${to})`;
}
