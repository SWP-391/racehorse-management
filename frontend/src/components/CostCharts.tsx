import { useMemo } from 'react';
import type { Statement, StatementOwnerRow } from '../api/types';
import { useLoad } from '../hooks/useLoad';
import { vi } from '../i18n/vi';
import { clubMonth, formatVnd } from '../lib/utils';
import { Chart } from './chart/Chart';
import { Skeleton } from './ui/states';

const TREND_MONTHS = 6;

/** Chart values are in millions of dong, one decimal: "46,3 triệu ₫" reads faster than "46.312.000". */
const million = (vnd: number | null) => (vnd === null ? null : Math.round(vnd / 100_000) / 10);

/** The last months up to now, oldest first: the x axis of every trend. */
const lastMonths = (count: number) => Array.from({ length: count }, (_, i) => clubMonth(i - (count - 1)));

/** "2026-09" as "09/26" so six labels fit under a narrow chart. */
const shortMonth = (month: string) => `${month.slice(5)}/${month.slice(2, 4)}`;

function ChartCard({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return (
    <section className="grid content-start gap-2 rounded-lg border bg-card p-4">
      <h3 className="text-sm font-semibold">{title}</h3>
      {hint && <p className="text-meta text-muted-foreground">{hint}</p>}
      {children}
    </section>
  );
}

/**
 * The total of each of the last six months, as the service computed it (one call per month): one bar per month, the
 * month in view is not special, so the person sees whether the cost is rising (BR-180: nothing is summed here).
 */
export function MonthlyTrend({ title, fetchTotal, deps }: { title: string; fetchTotal: (month: string) => Promise<number>; deps: unknown[] }) {
  const months = useMemo(() => lastMonths(TREND_MONTHS), []);
  const totals = useLoad(() => Promise.all(months.map((m) => fetchTotal(m).catch(() => null))), deps);
  return (
    <ChartCard title={title} hint={vi.billing.charts.trendHint}>
      {totals.state.data ? (
        <Chart kind="bar" title={title} categories={months.map(shortMonth)} series={[{ name: vi.billing.total, data: totals.state.data.map(million) }]} height={220} valueSuffix=" triệu ₫" />
      ) : (
        <Skeleton className="h-[220px]" />
      )}
    </ChartCard>
  );
}

/** Where the money of one statement goes: the category totals the service gives, one slice each. */
export function CategoryBreakdown({ statement }: { statement: Statement }) {
  const byCategory = new Map<string, number>();
  for (const h of statement.horses) for (const c of h.categories) byCategory.set(c.category, (byCategory.get(c.category) ?? 0) + c.total);
  const entries = [...byCategory].filter(([, total]) => total > 0);
  const title = vi.billing.charts.breakdown;
  return (
    <ChartCard title={title} hint={statement.month}>
      {entries.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">{vi.billing.empty}</p>
      ) : (
        <>
          <Chart kind="donut" title={title} categories={entries.map(([c]) => vi.billing.categories[c] ?? c)} series={[{ name: vi.billing.total, data: entries.map(([, t]) => million(t)) }]} height={220} valueSuffix=" triệu ₫" />
          <ul className="grid gap-1 text-sm">
            {entries.map(([c, total]) => (
              <li key={c} className="flex justify-between gap-3">
                <span>{vi.billing.categories[c] ?? c}</span>
                <span className="tabular font-medium">{formatVnd(total)}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </ChartCard>
  );
}

/** The Club Manager's month: one bar per owner, the largest first, so the biggest bills are seen at once. */
export function OwnerShare({ rows, month }: { rows: StatementOwnerRow[]; month: string }) {
  const sorted = [...rows].sort((a, b) => b.total - a.total);
  const title = vi.billing.charts.byOwner;
  return (
    <ChartCard title={title} hint={month}>
      <Chart kind="hbar" title={title} categories={sorted.map((r) => r.ownerName)} series={[{ name: vi.billing.total, data: sorted.map((r) => million(r.total)) }]} height={Math.max(160, sorted.length * 44)} valueSuffix=" triệu ₫" />
    </ChartCard>
  );
}

export { shortMonth };
