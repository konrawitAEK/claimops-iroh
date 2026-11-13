import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type Severity = 'error' | 'warning' | 'info' | 'success';

const ICONS: Record<Severity, string> = {
  error: 'error_outline',
  warning: 'report_problem',
  info: 'info_outline',
  success: 'check_circle_outline',
};

/** Alert banner. Project an element with [alertAction] for a trailing action. */
@Component({
  selector: 'app-alert',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'alert', '[class]': '"sev-" + severity()' },
  templateUrl: './alert.html',
  styleUrl: './alert.scss',
})
export class Alert {
  readonly severity = input<Severity>('info');
  /** Overrides the severity's default icon. */
  readonly icon = input<string>();
  protected readonly iconName = computed(() => this.icon() ?? ICONS[this.severity()]);
}
