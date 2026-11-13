import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators, type FormControl } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Alert } from '../../../components/alert/alert';
import { errorText } from '../../../core/api';
import { money } from '../../../utils/format.util';
import { demoPolicyNumber } from '../../../utils/policy.util';
import { CLAIM_TYPES, DAMAGE_CATEGORIES, MARKETS, localCurrency } from '../../../core/labels';
import type { ClaimDetail, ClaimType, Currency, Market } from '../../../models';
import { ClaimantService } from '../../../services/claimant.service';
import { SessionService } from '../../../services/session.service';

type DetailsGroup = FormGroup<Record<string, FormControl<string>>>;

/** Fields each claim type adds under incidentDetails. Keys match ClaimType.requiredDetails() on the backend. */
const DETAIL_FIELDS: Record<ClaimType, { key: string; label: string; options?: string[] }[]> = {
  MOTOR: [
    { key: 'licensePlate', label: 'Licence plate' },
    { key: 'vehicleModel', label: 'Vehicle model' },
  ],
  PROPERTY: [
    { key: 'propertyAddress', label: 'Property address' },
    { key: 'damageCategory', label: 'Damage category', options: DAMAGE_CATEGORIES },
  ],
};

/** Claimant: report a new incident. The detail fields switch with the claim type. */
@Component({
  selector: 'app-submit-claim-page',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatButtonToggleModule,
    RouterLink,
    Alert,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './submit-claim-page.html',
  styleUrl: './submit-claim-page.scss',
})
export class SubmitClaimPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly claims = inject(ClaimantService);
  private readonly session = inject(SessionService);

  protected readonly markets = MARKETS;
  protected readonly claimTypes = CLAIM_TYPES;
  protected readonly detailFields = DETAIL_FIELDS;
  protected readonly money = money;
  protected readonly today = new Date().toISOString().slice(0, 10);

  protected readonly form = this.fb.group({
    claimType: this.fb.control<ClaimType>('MOTOR'),
    market: this.fb.control<Market>(this.session.identity().market),
    currency: this.fb.control<Currency>(localCurrency(this.session.identity().market)),
    // Filled in from the claimant's policy for the chosen market and type (see syncPolicy); not typed by hand.
    policyNumber: [{ value: '', disabled: true }, Validators.required],
    incidentDate: [this.today, Validators.required],
    incidentDescription: ['', [Validators.required, Validators.maxLength(2000)]],
    claimedAmount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    incidentDetails: this.detailsFor('MOTOR'),
  });

  protected readonly claimType = toSignal(this.form.controls.claimType.valueChanges, { initialValue: 'MOTOR' });
  protected readonly market = toSignal(this.form.controls.market.valueChanges, {
    initialValue: this.form.controls.market.value,
  });
  protected readonly submitting = signal(false);
  protected readonly submitted = signal<ClaimDetail | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // The top-bar market is the default for a new claim; follow it when it changes. A claimant switch also clears
    // the previous claimant's confirmation.
    let first = true;
    effect(() => {
      const { role, market } = this.session.identity();
      if (role !== 'CLAIMANT') return;
      untracked(() => {
        if (first) {
          first = false;
          return;
        }
        this.form.controls.market.setValue(market);
        this.submitted.set(null);
        this.error.set(null);
        this.syncPolicy();
      });
    });
    const destroyRef = inject(DestroyRef);
    // A new claim type brings a new set of required detail fields.
    this.form.controls.claimType.valueChanges
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe((type) => {
        this.form.setControl('incidentDetails', this.detailsFor(type));
        this.syncPolicy();
      });
    // Changing market resets a local currency to the new market's; USD stays USD.
    this.form.controls.market.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe((market) => {
      if (this.form.controls.currency.value !== 'USD') this.form.controls.currency.setValue(localCurrency(market));
      this.syncPolicy();
    });
    this.syncPolicy();
  }

  protected currencies(): Currency[] {
    return [localCurrency(this.market()), 'USD'];
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.claims
      .submit({
        policyNumber: v.policyNumber.trim(),
        claimType: v.claimType,
        market: v.market,
        currency: v.currency,
        incidentDate: v.incidentDate,
        incidentDescription: v.incidentDescription.trim(),
        incidentDetails: v.incidentDetails,
        claimedAmount: v.claimedAmount ?? 0,
      })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (claim) => {
          this.submitted.set(claim);
          this.form.reset();
          this.syncPolicy();
        },
        error: (err: unknown) => this.error.set(errorText(err, 'Could not submit the claim')),
      });
  }

  protected newClaim(): void {
    this.submitted.set(null);
  }

  /** Looks up the claimant's policy for the current market and claim type. */
  private syncPolicy(): void {
    const { market, claimType } = this.form.getRawValue();
    this.form.controls.policyNumber.setValue(demoPolicyNumber(this.session.identity().name, market, claimType));
  }

  private detailsFor(type: ClaimType): DetailsGroup {
    const controls: Record<string, FormControl<string>> = {};
    for (const field of DETAIL_FIELDS[type]) {
      controls[field.key] = this.fb.control('', [Validators.required, Validators.maxLength(200)]);
    }
    return this.fb.group(controls);
  }
}
