/** Units an amount of money is typed in, as the factor to whole dong (BR-207). */
export const MONEY_UNITS = [1000, 1_000_000, 1] as const;
export type MoneyUnit = (typeof MONEY_UNITS)[number];
export const DEFAULT_MONEY_UNIT: MoneyUnit = 1000;

const zerosOf = (unit: number) => String(unit).length - 1;

/**
 * The typed number times the unit, as a decimal string in dong ("2.5" x 1,000,000 = "2500000"). Integer digit
 * arithmetic, never floating point. A result with a fraction of a dong ("1500.5") is returned as typed so that the
 * form's own whole-number check names the field; empty or unreadable text gives ''.
 */
export function toDong(text: string, unit: number): string {
  const t = text.trim().replace(',', '.');
  const m = /^(-?)(\d+)(?:\.(\d+))?$/.exec(t);
  if (!m) return '';
  const [, sign, int, frac = ''] = m;
  const z = zerosOf(unit);
  const digits = int + frac.padEnd(z, '0');
  const point = int.length + z;
  const whole = digits.slice(0, point).replace(/^0+(?=\d)/, '') || '0';
  const rest = digits.slice(point).replace(/0+$/, '');
  const out = whole + (rest ? `.${rest}` : '');
  return out === '0' ? '0' : sign + out;
}

/** The unit and text to show for a saved whole-dong amount: the largest of triệu and nghìn that divides it evenly. */
export function fromDong(value: string): { text: string; unit: MoneyUnit } {
  if (!/^-?\d+$/.test(value) || BigInt(value) === 0n) return { text: value === '0' ? '0' : '', unit: DEFAULT_MONEY_UNIT };
  const n = BigInt(value);
  const abs = n < 0n ? -n : n;
  const unit: MoneyUnit = abs % 1_000_000n === 0n ? 1_000_000 : abs % 1000n === 0n ? 1000 : 1;
  return { text: String(n / BigInt(unit)), unit };
}

const groups = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 });

/** "1.500.000" for a whole-dong decimal string; '' when it is not a whole number. */
export function groupDong(dong: string): string {
  return /^-?\d+$/.test(dong) ? groups.format(BigInt(dong)) : '';
}
