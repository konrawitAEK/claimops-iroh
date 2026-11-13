import type { ClaimType, Market } from '../models';

/**
 * The claimant's policy for a market and line of business, e.g. "POL-SG-MOT-482915".
 *
 * Stands in for a lookup against a policy administration system, which this demo doesn't have. It is deterministic:
 * the same claimant, market and type always give the same number, so it behaves like an existing policy rather than
 * a new one per claim.
 */
export function demoPolicyNumber(claimant: string, market: Market, type: ClaimType): string {
  let hash = 0;
  for (const ch of `${claimant}|${market}|${type}`) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return `POL-${market}-${type.slice(0, 3)}-${String(hash % 1_000_000).padStart(6, '0')}`;
}
