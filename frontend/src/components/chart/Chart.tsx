import { lazy, Suspense, type ReactNode } from 'react';
import { Skeleton } from '../ui/states';

const EChart = lazy(() => import('./echarts'));

export interface ChartSeries {
  name: string;
  /** One value per category; null = no recorded value (a gap, never 0). */
  data: (number | null)[];
  /** Per point: marked (e.g. simulated data, BR-051) — drawn as a diamond and named in the hidden table. */
  marked?: boolean[];
}

export interface ChartProps {
  kind: 'area' | 'bar' | 'hbar' | 'donut';
  /** The chart's title: the hidden table's caption, which tests and screen readers read. */
  title: string;
  categories: string[];
  series: ChartSeries[];
  height?: number;
  valueSuffix?: string;
  /** Whole-number data (counts): the value axis ticks at integers only. */
  integer?: boolean;
  /** Column header and cell text for marked points in the hidden table. */
  markedLabel?: string;
  /** Legend line under the chart explaining the marker. */
  markedLegend?: ReactNode;
}

/**
 * The kit's only chart (ui-kit § Charts): a lazily loaded ECharts canvas, hidden from assistive tech,
 * plus a visually hidden table with the same values. Never invents data: every value comes from the API.
 */
export function Chart(props: ChartProps) {
  const { title, categories, series, height = 240, markedLabel, markedLegend } = props;
  const anyMarked = Boolean(markedLabel) && series.some((s) => s.marked?.some(Boolean));
  return (
    <figure className="m-0 grid gap-2">
      <div aria-hidden style={{ minHeight: height }}>
        <Suspense fallback={<Skeleton style={{ height }} />}>
          <EChart {...props} height={height} />
        </Suspense>
      </div>
      {markedLegend && anyMarked && <figcaption className="text-meta text-muted-foreground">{markedLegend}</figcaption>}
      <table className="sr-only">
        <caption>{title}</caption>
        <thead>
          <tr>
            <th scope="col" />
            {series.map((s) => (
              <th key={s.name} scope="col">
                {s.name}
              </th>
            ))}
            {anyMarked && <th scope="col">{markedLabel}</th>}
          </tr>
        </thead>
        <tbody>
          {categories.map((c, i) => (
            <tr key={`${c}-${i}`}>
              <th scope="row">{c}</th>
              {series.map((s) => (
                <td key={s.name}>{s.data[i] ?? '—'}</td>
              ))}
              {anyMarked && <td>{series.some((s) => s.marked?.[i]) ? markedLabel : ''}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
