import type { ClaimStatus, ClaimType, Currency, Market, Role } from '../models';

export const MARKETS: { code: Market; name: string; currency: Currency }[] = [
  { code: 'SG', name: 'Singapore', currency: 'SGD' },
  { code: 'TH', name: 'Thailand', currency: 'THB' },
  { code: 'MY', name: 'Malaysia', currency: 'MYR' },
  { code: 'HK', name: 'Hong Kong', currency: 'HKD' },
  { code: 'AU', name: 'Australia', currency: 'AUD' },
  { code: 'JP', name: 'Japan', currency: 'JPY' },
];

export function localCurrency(market: Market): Currency {
  return MARKETS.find((m) => m.code === market)?.currency ?? 'USD';
}

export const CLAIM_TYPES: { code: ClaimType; label: string }[] = [
  { code: 'MOTOR', label: 'Motor' },
  { code: 'PROPERTY', label: 'Property' },
];

export const STATUS_LABELS: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  IN_REVIEW: 'In review',
  INFO_REQUESTED: 'Info requested',
  SETTLED: 'Settled',
  REJECTED: 'Rejected',
};

export const STATUSES = Object.keys(STATUS_LABELS) as ClaimStatus[];

/** Demo users for the role switcher; the backend trusts whatever name is sent. */
export const DEMO_USERS: Record<Role, string[]> = {
  CLAIMANT: ['alice', 'ben', 'chloe'],
  STAFF: ['bob', 'carol', 'dan'],
};

export const DAMAGE_CATEGORIES = ['Fire', 'Water', 'Storm', 'Theft', 'Impact', 'Other'];

/** Where each role lands. */
export function homePath(role: Role): string {
  return role === 'STAFF' ? '/staff' : '/claim/new';
}
