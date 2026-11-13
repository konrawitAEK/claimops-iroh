import type { Currency } from '../models';

const formatters = new Map<Currency, Intl.NumberFormat>();

/** "SGD 5,000.00"; JPY has no minor unit, so it shows no decimals. */
export function money(amount: number | null | undefined, currency: Currency): string {
  if (amount === null || amount === undefined) return '—';
  let f = formatters.get(currency);
  if (!f) {
    f = new Intl.NumberFormat('en', { style: 'currency', currency, currencyDisplay: 'code' });
    formatters.set(currency, f);
  }
  return f.format(amount);
}

/** Hours as a short duration: "< 1 min", "45 min", "3.5 h", "2.1 d". */
export function duration(hours: number | null | undefined): string {
  if (hours === null || hours === undefined) return '—';
  if (hours < 1 / 60) return '< 1 min';
  if (hours < 1) return `${Math.round(hours * 60)} min`;
  if (hours < 48) return `${hours.toFixed(1)} h`;
  return `${(hours / 24).toFixed(1)} d`;
}

export function dateTime(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : '—';
}
