import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Joins class names and lets the last Tailwind utility win (kit `cn()` helper). */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

const CLUB_ZONE = 'Asia/Ho_Chi_Minh';
const clubTimeFormat = new Intl.DateTimeFormat('vi-VN', { timeZone: CLUB_ZONE, hour: '2-digit', minute: '2-digit', hour12: false });
const clubDateTimeFormat = new Intl.DateTimeFormat('vi-VN', {
  timeZone: CLUB_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const clubDateFormat = new Intl.DateTimeFormat('en-CA', { timeZone: CLUB_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' });

/** An ISO instant from the service as the club-local date `yyyy-mm-dd`; unreadable input gives ''. */
export function clubDate(instant: string | null | undefined): string {
  if (!instant) return '';
  const d = new Date(instant);
  return Number.isNaN(d.getTime()) ? '' : clubDateFormat.format(d);
}

/** An ISO instant from the service as club-local `HH:mm`; unreadable input is shown as it came. */
export function clubTime(instant: string | null | undefined): string {
  if (!instant) return '';
  const d = new Date(instant);
  return Number.isNaN(d.getTime()) ? instant : clubTimeFormat.format(d);
}

/** An ISO instant from the service as club-local `dd/mm/yyyy HH:mm`; unreadable input is shown as it came. */
export function clubDateTime(instant: string | null | undefined): string {
  if (!instant) return '';
  const d = new Date(instant);
  if (Number.isNaN(d.getTime())) return instant;
  const p = Object.fromEntries(clubDateTimeFormat.formatToParts(d).map((x) => [x.type, x.value]));
  return `${p.day}/${p.month}/${p.year} ${p.hour}:${p.minute}`;
}

const vndFormat = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });

/** Money in whole dong, "150.000 ₫"; the client only shows the server's amounts and never sums them (BR-180). */
export function formatVnd(amount: number): string {
  return vndFormat.format(amount);
}

const clubMonthFormat = new Intl.DateTimeFormat('en-CA', { timeZone: CLUB_ZONE, year: 'numeric', month: '2-digit' });

/** The club-local month as `yyyy-MM`, shifted by whole months (0 = now, -1 = last month). */
export function clubMonth(shift = 0, now: Date = new Date()): string {
  const p = Object.fromEntries(clubMonthFormat.formatToParts(now).map((x) => [x.type, x.value]));
  const index = Number(p.year) * 12 + (Number(p.month) - 1) + shift;
  const year = Math.floor(index / 12);
  return `${year}-${String((index % 12) + 1).padStart(2, '0')}`;
}
