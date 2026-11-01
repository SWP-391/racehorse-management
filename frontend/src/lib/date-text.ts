/** A day as the person reads it, dd/mm/yyyy, and back to the `yyyy-MM-dd` the service takes (BR-206). */

const pad = (n: number, w = 2) => String(n).padStart(w, '0');

function realDay(y: number, m: number, d: number): boolean {
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/** `yyyy-MM-dd` for typed "5/3/2026", "05-03-2026" or "2026-03-05"; null for anything that is not a real day. */
export function parseDateText(text: string): string | null {
  const t = text.trim();
  let y: number, m: number, d: number;
  let hit = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(t);
  if (hit) [y, m, d] = [Number(hit[1]), Number(hit[2]), Number(hit[3])];
  else if ((hit = /^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})$/.exec(t))) [d, m, y] = [Number(hit[1]), Number(hit[2]), Number(hit[3])];
  else return null;
  return realDay(y, m, d) ? `${pad(y, 4)}-${pad(m)}-${pad(d)}` : null;
}

/** "05/03/2026" for `2026-03-05`; empty or unreadable gives ''. */
export function formatDateText(iso: string): string {
  const hit = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  return hit ? `${hit[3]}/${hit[2]}/${hit[1]}` : '';
}

/** A local-midnight Date for a calendar, from `yyyy-MM-dd`; undefined when not a day. */
export function isoToDate(iso: string): Date | undefined {
  const hit = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  return hit && realDay(Number(hit[1]), Number(hit[2]), Number(hit[3])) ? new Date(Number(hit[1]), Number(hit[2]) - 1, Number(hit[3])) : undefined;
}

export function dateToIso(date: Date): string {
  return `${pad(date.getFullYear(), 4)}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
