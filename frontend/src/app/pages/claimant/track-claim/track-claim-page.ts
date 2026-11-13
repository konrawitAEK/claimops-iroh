import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { Alert } from '../../../components/alert/alert';
import { StatusChip } from '../../../components/status-chip/status-chip';
import { errorText, statusOf } from '../../../core/api';
import { dateTime, money } from '../../../utils/format.util';
import type { ClaimDetail, ClaimSummary } from '../../../models';
import { ClaimantService } from '../../../services/claimant.service';
import { SessionService } from '../../../services/session.service';
import { humanizeKey } from '../../../utils/text.util';
import { isDecided, openQuestion } from '../../../utils/claim.util';
import { ClaimTimeline } from './components/claim-timeline/claim-timeline';

const POLL_MS = 10_000;

/**
 * Claimant: look up a claim by number, follow its progress, and reply when the officer asks for more information.
 *
 * The open claim is re-fetched every 10 s until it is decided, so a staff action shows up without a reload. Polling
 * was chosen over SSE or WebSockets to keep the time-boxed build simple.
 *
 * Switching claimant in the top bar clears the open claim, which belongs to the previous claimant, and reloads the
 * list of "your claims".
 */
@Component({
  selector: 'app-track-claim-page',
  imports: [FormsModule, KeyValuePipe, MatCardModule, MatInputModule, MatButtonModule, Alert, StatusChip, ClaimTimeline],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './track-claim-page.html',
  styleUrl: './track-claim-page.scss',
})
export class TrackClaimPage {
  /** ?number=CLM-… from the "Track it" link after submitting. */
  readonly number = input<string>();

  private readonly claims = inject(ClaimantService);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private firstLoad = true;

  protected readonly money = money;
  protected readonly when = dateTime;
  protected readonly openQuestion = openQuestion;
  protected readonly labelFor = humanizeKey;
  protected query = '';
  protected readonly myClaims = signal<ClaimSummary[]>([]);
  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected reply = '';
  protected readonly sending = signal(false);
  protected readonly replyError = signal<string | null>(null);

  constructor() {
    effect(() => {
      const { role } = this.session.identity();
      // A switch to the staff view navigates away; don't reset the URL and race that navigation.
      if (role === 'CLAIMANT') untracked(() => this.onIdentity());
    });
    const timer = setInterval(() => this.refresh(), POLL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  /** First run: open the claim from ?number=. Later runs (claimant switched): start clean for the new claimant. */
  private onIdentity(): void {
    this.claims.myClaims().subscribe({ next: (list) => this.myClaims.set(list), error: () => this.myClaims.set([]) });
    if (this.firstLoad) {
      this.firstLoad = false;
      const initial = this.number();
      if (initial) {
        this.query = initial;
        this.search();
      }
      return;
    }
    this.claim.set(null);
    this.error.set(null);
    this.replyError.set(null);
    this.query = '';
    this.reply = '';
    void this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }

  protected search(): void {
    const number = this.query.trim();
    if (!number) return;
    this.loading.set(true);
    this.error.set(null);
    void this.router.navigate([], { queryParams: { number }, replaceUrl: true });
    this.claims
      .byNumber(number)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (claim) => this.claim.set(claim),
        error: (err: unknown) => {
          this.claim.set(null);
          this.error.set(statusOf(err) === 404 ? `No claim ${number} found under your name.` : errorText(err));
        },
      });
  }

  protected open(claimNumber: string): void {
    this.query = claimNumber;
    this.search();
  }

  protected sendReply(claim: ClaimDetail): void {
    const answer = this.reply.trim();
    if (!answer) return;
    this.sending.set(true);
    this.replyError.set(null);
    this.claims
      .reply(claim.id, answer)
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: (updated) => {
          this.claim.set(updated);
          this.reply = '';
        },
        error: (err: unknown) => this.replyError.set(errorText(err, 'Could not send your reply')),
      });
  }

  private refresh(): void {
    const current = this.claim();
    if (!current || isDecided(current.status) || this.sending()) return;
    this.claims.byNumber(current.claimNumber).subscribe({ next: (claim) => this.claim.set(claim), error: () => {} });
  }
}
