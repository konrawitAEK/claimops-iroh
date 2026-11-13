import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { dateTime, money } from '../../../../../utils/format.util';
import type { ClaimDetail } from '../../../../../models';

interface TimelineEntry {
  at: string | null;
  title: string;
  text?: string;
  tone: 'info' | 'warning' | 'success' | 'error' | 'muted';
}

/**
 * Claim activity, oldest first, built from the claim itself (submission, information requests and answers, decision).
 *
 * Built on the client from the claim detail rather than from the staff audit trail, so claimants never see internal
 * events or officer names beyond the assigned handler.
 */
@Component({
  selector: 'app-claim-timeline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './claim-timeline.html',
  styleUrl: './claim-timeline.scss',
})
export class ClaimTimeline {
  readonly claim = input.required<ClaimDetail>();
  protected readonly when = dateTime;

  protected readonly entries = computed<TimelineEntry[]>(() => {
    const c = this.claim();
    const list: TimelineEntry[] = [
      { at: c.submittedAt, title: 'Claim submitted', text: c.incidentDescription, tone: 'info' },
    ];
    if (c.assignedHandler) {
      list.push({ at: null, title: `Under review by ${c.assignedHandler}`, tone: 'muted' });
    }
    for (const r of c.infoRequests) {
      list.push({ at: r.requestedAt, title: 'More information requested', text: r.question, tone: 'warning' });
      if (r.answeredAt) list.push({ at: r.answeredAt, title: 'You replied', text: r.answer ?? '', tone: 'info' });
    }
    if (c.status === 'SETTLED') {
      list.push({
        at: c.decidedAt,
        title: `Settled for ${money(c.settledAmount, c.currency)}`,
        text: c.decisionNote ?? undefined,
        tone: 'success',
      });
    } else if (c.status === 'REJECTED') {
      list.push({ at: c.decidedAt, title: 'Claim rejected', text: c.decisionNote ?? undefined, tone: 'error' });
    }
    return list;
  });
}
