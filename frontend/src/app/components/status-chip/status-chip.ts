import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { STATUS_LABELS } from '../../core/labels';
import type { ClaimStatus } from '../../models';

/** Small coloured pill for a claim status. */
@Component({
  selector: 'app-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './status-chip.html',
  styleUrl: './status-chip.scss',
})
export class StatusChip {
  readonly status = input.required<ClaimStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
}
