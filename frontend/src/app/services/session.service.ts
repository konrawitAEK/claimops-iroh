import { Injectable, signal } from '@angular/core';
import { DEMO_USERS } from '../core/labels';
import type { Identity, Market, Role } from '../models';

const STORAGE_KEY = 'claimops.identity';

function load(): Identity {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) return JSON.parse(saved) as Identity;
  } catch {
    // Storage blocked or corrupt: fall back to the default identity.
  }
  return { role: 'CLAIMANT', name: DEMO_USERS.CLAIMANT[0], market: 'SG' };
}

/**
 * The identity the app is acting as, chosen in the top-bar role switcher.
 *
 * Stands in for a login session in this demo: it is remembered per browser so a refresh keeps the same view.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly state = signal<Identity>(load());
  readonly identity = this.state.asReadonly();

  /** Switches role and picks that role's first demo user, since names don't carry across roles. */
  switchRole(role: Role): void {
    if (role !== this.state().role) this.update({ role, name: DEMO_USERS[role][0] });
  }

  setName(name: string): void {
    this.update({ name });
  }

  setMarket(market: Market): void {
    this.update({ market });
  }

  private update(change: Partial<Identity>): void {
    this.state.update((current) => ({ ...current, ...change }));
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.state()));
    } catch {
      // Not persisted; the in-memory identity still works.
    }
  }
}
