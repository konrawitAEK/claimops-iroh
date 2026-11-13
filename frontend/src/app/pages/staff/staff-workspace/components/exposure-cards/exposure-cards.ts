import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MARKETS } from '../../../../../core/labels';
import type { ClaimType, Currency, Exposure, Market } from '../../../../../models';
import { money } from '../../../../../utils/format.util';

interface Totals {
  openClaims: number;
  outstanding: number;
  settled: number;
}

/** One currency's figures: a breakdown (by claim type, or by market for USD) and the total. */
interface CurrencyBlock extends Totals {
  currency: Currency;
  /** Only rows with any activity. */
  rows: (Totals & { label: string })[];
}

interface MarketCard {
  code: Market;
  name: string;
  local: CurrencyBlock;
}

const TYPES: ClaimType[] = ['MOTOR', 'PROPERTY'];

function totals(rows: Exposure[]): Totals {
  return rows.reduce(
    (t, r) => ({
      openClaims: t.openClaims + r.openClaims,
      outstanding: t.outstanding + r.outstandingAmount,
      settled: t.settled + r.settledAmount,
    }),
    { openClaims: 0, outstanding: 0, settled: 0 },
  );
}

function hasActivity(t: Totals): boolean {
  return t.openClaims > 0 || t.outstanding !== 0 || t.settled !== 0;
}

/** Figures for `currency`, broken down by whatever `labelOf` groups the rows by. */
function block(rows: Exposure[], currency: Currency, groups: string[], labelOf: (e: Exposure) => string): CurrencyBlock {
  const inCurrency = rows.filter((r) => r.currency === currency);
  return {
    currency,
    ...totals(inCurrency),
    rows: groups
      .map((label) => ({ label, ...totals(inCurrency.filter((r) => labelOf(r) === label)) }))
      .filter(hasActivity),
  };
}

/**
 * Liability: outstanding (open claims) and settled (paid out).
 *
 * Amounts are only ever added within one currency. USD claims, which any market can file, get their own card totalled
 * across the region and broken down by market. Each market card shows only its local currency, by claim type, since
 * the backend does no FX conversion.
 */
@Component({
  selector: 'app-exposure-cards',
  imports: [MatCardModule, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './exposure-cards.html',
  styleUrl: './exposure-cards.scss',
})
export class ExposureCards {
  readonly exposures = input.required<Exposure[]>();
  protected readonly money = money;

  /** USD across every market, one row per market: one currency, so a true APAC total. */
  protected readonly usd = computed(() =>
    block(this.exposures(), 'USD', MARKETS.map((m) => m.code), (e) => e.market),
  );

  protected readonly byMarket = computed<MarketCard[]>(() =>
    MARKETS.map((m) => {
      const rows = this.exposures().filter((e) => e.market === m.code);
      return { code: m.code, name: m.name, local: block(rows, m.currency, TYPES, (e) => e.claimType) };
    }),
  );
}
