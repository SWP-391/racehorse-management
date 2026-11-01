import { vi } from '../i18n/vi';
import { clubDate } from './utils';

/** A check answers the Vietnamese message of the rule it breaks, or `null` when the value passes (input-validation). */
export type Check = string | null;

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** The club's calendar day as `yyyy-MM-dd` (Asia/Ho_Chi_Minh), moved by whole days. */
export function today(offsetDays = 0, now: Date = new Date()): string {
  return clubDate(new Date(now.getTime() + offsetDays * 864e5).toISOString());
}

export const blank = (v: unknown): boolean => v === null || v === undefined || (typeof v === 'string' && v.trim() === '');

export function required(v: unknown): Check {
  return blank(v) ? vi.validation.required : null;
}

/** At most `n` characters; the same limit the service applies to the field. */
export function maxLength(v: string | null | undefined, n: number): Check {
  return v && v.length > n ? vi.validation.tooLong(n) : null;
}

export interface NumberRule {
  min?: number;
  max?: number;
  /** Whole numbers only. */
  integer?: boolean;
  /** Strictly above zero (a distance, a duration, a quantity, an interval). */
  positive?: boolean;
  /** An empty value is refused; without it, empty passes (the field is optional). */
  required?: boolean;
}

export function number(raw: string | number | null | undefined, rule: NumberRule = {}): Check {
  if (blank(raw)) return rule.required ? vi.validation.required : null;
  const text = typeof raw === 'number' ? String(raw) : String(raw).trim().replace(',', '.');
  // Digits, one optional point and one optional sign only: "1e3", "0x10" and "abc" are text, not numbers (BR-197).
  // A number given as a JS number may be written with an exponent by String(), which is a real number and passes.
  if (typeof raw !== 'number' && !/^[+-]?\d+(\.\d+)?$/.test(text)) return vi.validation.notNumber;
  const value = Number(text);
  if (!Number.isFinite(value)) return vi.validation.notNumber;
  if (rule.integer && (!Number.isInteger(value) || (typeof raw !== 'number' && text.includes('.')))) return vi.validation.notInteger;
  if (rule.positive && value <= 0) return vi.validation.positive;
  if (rule.min !== undefined && value < rule.min) return rule.min === 0 ? vi.validation.notNegative : vi.validation.min(rule.min);
  if (rule.max !== undefined && value > rule.max) return vi.validation.max(rule.max);
  return null;
}

/** The service's own rule for the name of a unit of supplies: NFC, 1 to 20 characters, a letter, letters digits space "/" (BR-195). */
export const UNIT_NAME_MAX = 20;
export function normaliseUnitName(raw: string): string {
  return raw.normalize('NFC').replace(/\s+/g, ' ').trim();
}
export function unitName(raw: string | null | undefined): Check {
  const name = normaliseUnitName(raw ?? '');
  if (!name) return vi.validation.unitRequired;
  if (name.length > UNIT_NAME_MAX) return vi.validation.tooLong(UNIT_NAME_MAX);
  if (!/^[\p{L}\p{Nd} /]+$/u.test(name)) return vi.validation.unitChars;
  if (!/\p{L}/u.test(name)) return vi.validation.unitNoLetter;
  return null;
}

export interface DateRule {
  /** Earliest and latest allowed day, `yyyy-MM-dd`. */
  min?: string;
  max?: string;
  /** Shortcuts: not after today, not before today. */
  notFuture?: boolean;
  notPast?: boolean;
  required?: boolean;
}

/** `yyyy-MM-dd` shown the way people read it, `dd/MM/yyyy`; anything else is returned as it came. */
export function showDate(iso: string): string {
  const m = ISO_DATE.exec(iso);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : iso;
}

export function date(raw: string | null | undefined, rule: DateRule = {}, now: Date = new Date()): Check {
  if (blank(raw)) return rule.required ? vi.validation.required : null;
  const m = ISO_DATE.exec(raw as string);
  if (!m) return vi.validation.dateInvalid;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const real = new Date(Date.UTC(y, mo - 1, d));
  if (real.getUTCFullYear() !== y || real.getUTCMonth() !== mo - 1 || real.getUTCDate() !== d) return vi.validation.dateInvalid;
  const day = raw as string;
  const todayIso = today(0, now);
  if (rule.notFuture && day > todayIso) return vi.validation.dateFuture;
  if (rule.notPast && day < todayIso) return vi.validation.datePast;
  if (rule.min && day < rule.min) return vi.validation.dateBefore(showDate(rule.min));
  if (rule.max && day > rule.max) return vi.validation.dateAfter(showDate(rule.max));
  return null;
}

/** Whole days from `from` to `to`, both `yyyy-MM-dd`. */
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 864e5);
}

/** The message belongs to the end field; either side may be empty (an open range passes). */
export function dateRange(from: string, to: string, maxDays?: number): Check {
  if (blank(from) || blank(to) || date(from) || date(to)) return null;
  if (to < from) return vi.validation.rangeInverted;
  if (maxDays !== undefined && daysBetween(from, to) + 1 > maxDays) return vi.validation.rangeTooLong(maxDays);
  return null;
}

/** The field must hold one of the offered codes. */
export function oneOf(v: string | number | null | undefined, allowed: readonly (string | number)[]): Check {
  if (blank(v)) return vi.validation.required;
  return allowed.includes(v as string | number) ? null : vi.validation.chooseOne;
}

/** Keeps only the refused fields: `{name: 'Bắt buộc nhập.'}`. */
export function collect(checks: Record<string, Check>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [field, message] of Object.entries(checks)) if (message) out[field] = message;
  return out;
}

/** Moves focus to the first field the page marked invalid, once the errors are on screen. */
export function focusFirstInvalid(scope: ParentNode | null | undefined = document): void {
  window.setTimeout(() => {
    scope?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, 0);
}

/**
 * Plausibility bounds where the business rules give no limit (design D6, a TECH item, not a business rule). They only
 * stop an obvious slip such as a heart rate of 1500; the Veterinarian or Head Trainer confirms the values.
 */
export const BOUNDS = {
  heartRateBpm: { min: 20, max: 300 },
  speedKmh: { min: 0, max: 100 },
  weightKg: { min: 100, max: 1500 },
  distanceM: { min: 1, max: 50_000 },
  durationMin: { min: 1, max: 600 },
  volumeKmWeek: { min: 1, max: 1000 },
  intervalDays: { min: 1, max: 3650 },
  quantity: { min: 1, max: 1_000_000 },
  stock: { min: 0, max: 1_000_000 },
  price: { min: 0, max: 1_000_000_000 },
  form: { min: 1, max: 5 },
  settingDays: { min: 1, max: 365 },
  settingHours: { min: 1, max: 168 },
  settingMinutes: { min: 1, max: 1440 },
} as const;

/** Dates have no business limit either, but a typo in the year (0202, 20255) is never meant (design D6). */
export const DATE_LIMITS = { min: '1990-01-01', max: '2100-12-31' } as const;
