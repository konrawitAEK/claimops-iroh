import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { finalize, type Observable } from 'rxjs';
import { Alert, type Severity } from '../../../components/alert/alert';
import { StatusChip } from '../../../components/status-chip/status-chip';
import { errorText, problemOf, statusOf } from '../../../core/api';
import { dateTime, duration, money } from '../../../utils/format.util';
import { MARKETS, STATUSES, STATUS_LABELS } from '../../../core/labels';
import type { ClaimDetail, ClaimStatus, ClaimSummary, Dashboard, Market } from '../../../models';
import { SessionService } from '../../../services/session.service';
import { StaffService } from '../../../services/staff.service';
import { openClaimDrawer } from './components/claim-drawer/claim-drawer';
import { DecisionDialog, type DecisionDialogData, type DecisionMode, type DecisionResult } from './components/decision-dialog/decision-dialog';
import { ExposureCards } from './components/exposure-cards/exposure-cards';

const POLL_MS = 10_000;

/**
 * Staff: liability exposure, team workload and the claims queue with its actions.
 *
 * Exposure comes from the Kafka-fed read model, so it can trail an action by a moment. The page re-fetches every 10 s
 * and right after each action. A 409 (someone else picked the claim up first, or its status moved on) is shown as a
 * warning and the queue is reloaded, so the officer sees the claim's real state instead of a generic error.
 *
 * Switching officer or market in the top bar reloads everything: "only mine" and the action buttons depend on who
 * the officer is, and the market selection becomes the queue's market filter.
 */
@Component({
  selector: 'app-staff-workspace-page',
  imports: [
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatProgressBarModule,
    Alert,
    StatusChip,
    ExposureCards,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff-workspace-page.html',
  styleUrl: './staff-workspace-page.scss',
})
export class StaffWorkspacePage {
  private readonly staff = inject(StaffService);
  private readonly dialog = inject(MatDialog);
  protected readonly session = inject(SessionService);

  protected readonly markets = MARKETS;
  protected readonly statuses = STATUSES;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly money = money;
  protected readonly when = dateTime;
  protected readonly duration = duration;

  protected readonly dashboard = signal<Dashboard | null>(null);
  protected readonly queue = signal<ClaimSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly busyId = signal<string | null>(null);
  protected readonly notice = signal<{ severity: Severity; text: string } | null>(null);

  protected marketFilter: Market | null = this.session.identity().market;
  protected statusFilter: ClaimStatus | null = null;
  protected mineOnly = false;

  constructor() {
    // Runs once on load and again whenever the identity changes. untracked() keeps the signals read during the
    // reload from becoming dependencies of this effect.
    effect(() => {
      const { role, market } = this.session.identity();
      // A switch to the claimant view navigates away; don't call staff APIs with claimant headers meanwhile.
      if (role !== 'STAFF') return;
      untracked(() => {
        this.marketFilter = market;
        this.notice.set(null);
        this.reload(true);
      });
    });
    const timer = setInterval(() => this.reload(false), POLL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected reload(showProgress = true): void {
    if (showProgress) this.loading.set(true);
    this.staff.dashboard().subscribe({ next: (d) => this.dashboard.set(d), error: () => {} });
    this.staff
      .queue({ market: this.marketFilter, status: this.statusFilter, mine: this.mineOnly })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (list) => this.queue.set(list),
        error: (err: unknown) => this.notice.set({ severity: 'error', text: errorText(err) }),
      });
  }

  protected isMine(claim: ClaimSummary): boolean {
    return claim.assignedHandler === this.session.identity().name;
  }

  /** Shows the claim's full details in the side drawer. */
  protected view(claim: ClaimSummary): void {
    openClaimDrawer(this.dialog, claim.id);
  }

  protected pickUp(claim: ClaimSummary): void {
    this.run(claim, this.staff.pickUp(claim.id), `You picked up ${claim.claimNumber}.`);
  }

  protected decide(claim: ClaimSummary, mode: DecisionMode): void {
    this.dialog
      .open<DecisionDialog, DecisionDialogData, DecisionResult>(DecisionDialog, { data: { mode, claim } })
      .afterClosed()
      .subscribe((result) => {
        if (!result) return;
        const call =
          mode === 'info'
            ? this.staff.requestInfo(claim.id, result.text)
            : mode === 'settle'
              ? this.staff.settle(claim.id, result.amount ?? 0, result.text)
              : this.staff.reject(claim.id, result.text);
        const done = { info: 'Information requested for', settle: 'Settled', reject: 'Rejected' }[mode];
        this.run(claim, call, `${done} ${claim.claimNumber}.`);
      });
  }

  /** Runs one claim action; on 409 explains the conflict and reloads, since the row on screen is stale. */
  private run(claim: ClaimSummary, call: Observable<ClaimDetail>, success: string): void {
    this.busyId.set(claim.id);
    this.notice.set(null);
    call.pipe(finalize(() => this.busyId.set(null))).subscribe({
      next: () => {
        this.notice.set({ severity: 'success', text: success });
        this.reload(false);
      },
      error: (err: unknown) => {
        if (statusOf(err) === 409) {
          const detail = problemOf(err)?.detail ?? '';
          this.notice.set({
            severity: 'warning',
            text: `${claim.claimNumber} was changed by someone else before your action went through. ${detail} The queue has been refreshed.`,
          });
          this.reload(false);
        } else {
          this.notice.set({ severity: 'error', text: errorText(err) });
        }
      },
    });
  }
}
