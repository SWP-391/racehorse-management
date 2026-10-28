import { useEffect, useRef, useState } from 'react';
import { TriangleAlert } from 'lucide-react';
import { api } from '../api/api';
import type { LiveFigures, LiveView, LiveWindow } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { messageOf } from './ErrorText';
import { SimulatedBadge } from './StatusBadge';
import { Button } from './ui/button';
import { Section } from './ui/display';
import { Callout } from './ui/states';
import { FilterTabs } from './ui/tabs';
import { vi } from '../i18n/vi';

const POLL_MS = 1000;

/**
 * Sparkline of the samples of the chosen window; the stroke is a token class, so the colour lives only in styles.css.
 * A threshold draws a dashed line and tints the safe zone below it; the scale always includes it (BR-189).
 */
function Line({
  values,
  tone,
  label,
  threshold,
}: {
  values: number[];
  tone: 'stroke-chart-1' | 'stroke-chart-4';
  label: string;
  threshold?: number;
}) {
  if (values.length < 2) return null;
  const w = 240;
  const h = 48;
  const min = Math.min(...values, ...(threshold !== undefined ? [threshold] : []));
  const max = Math.max(...values, ...(threshold !== undefined ? [threshold] : []));
  const span = max - min || 1;
  const y = (v: number) => (h - ((v - min) / span) * h).toFixed(1);
  const path = values
    .map((v, i) => `${i === 0 ? 'M' : 'L'}${((i / (values.length - 1)) * w).toFixed(1)},${y(v)}`)
    .join(' ');
  return (
    <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" role="img" aria-label={label} className="h-12 w-full">
      {threshold !== undefined && (
        <>
          <rect x={0} y={y(threshold)} width={w} height={h - Number(y(threshold))} className="fill-status-eligible" data-testid="safe-zone" />
          <line x1={0} x2={w} y1={y(threshold)} y2={y(threshold)} strokeWidth={1.5} strokeDasharray="4 3" vectorEffect="non-scaling-stroke" className="stroke-status-injured-ink" data-testid="threshold-line" />
        </>
      )}
      <path d={path} fill="none" strokeWidth={2} vectorEffect="non-scaling-stroke" className={tone} />
    </svg>
  );
}

const WINDOWS: LiveWindow[] = ['ONE_MINUTE', 'FIVE_MINUTES', 'SESSION'];

/** Current, lowest, highest and average of a measure over the window, with its unit. */
function Figures({ figures, unit, testId }: { figures: LiveFigures; unit: string; testId: string }) {
  const items: [string, number][] = [
    [vi.sim.current, figures.current],
    [vi.sim.min, figures.min],
    [vi.sim.max, figures.max],
    [vi.sim.avg, figures.avg],
  ];
  return (
    <dl className="m-0 grid grid-cols-4 gap-x-3 text-meta" data-testid={testId}>
      {items.map(([term, value]) => (
        <div key={term} className="grid gap-0.5">
          <dt className="text-muted-foreground">{term}</dt>
          <dd className="m-0 tabular-nums">
            {value} {unit}
          </dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * Live simulated heart rate and speed, polled every second while a horse is training (TECH-002, NFR-011).
 * Always labelled as simulated and never to be used for real decisions (BR-050, BR-053). Nothing animates per tick.
 */
export default function LivePanel({ horseId }: { horseId: number }) {
  const { can } = useAuth();
  const [live, setLive] = useState<LiveView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const [attempt, setAttempt] = useState(0);
  const [window, setWindow] = useState<LiveWindow>('ONE_MINUTE');

  useEffect(() => {
    if (!can('VIEW_LIVE')) return;
    let stopped = false;
    const tick = () =>
      api
        .live(horseId, window)
        .then((l) => !stopped && (setLive(l), setError(null)))
        .catch((e) => !stopped && setError(messageOf(e)));
    void tick();
    timer.current = setInterval(tick, POLL_MS);
    return () => {
      stopped = true;
      if (timer.current) clearInterval(timer.current);
    };
  }, [horseId, can, attempt, window]);

  if (!can('VIEW_LIVE')) return null;

  return (
    <Section id="live-panel" title={vi.sim.live}>
      {error && (
        <Callout tone="danger" icon={TriangleAlert} role="alert">
          <span className="flex flex-wrap items-center gap-x-3 gap-y-2">
            {error}
            {/* Polling already retries every second; this asks for an answer now and restarts the timer. */}
            <Button variant="outline" size="sm" onClick={() => setAttempt((n) => n + 1)}>
              {vi.common.retry}
            </Button>
          </span>
        </Callout>
      )}
      {live && !live.active && <p className="text-muted-foreground">{vi.sim.idle}</p>}
      {live?.active && live.current && (
        <>
          <Callout
            tone="info"
            role="status"
            className="items-center border-status-simulated-ink/30 bg-status-simulated text-status-simulated-ink"
          >
            <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <SimulatedBadge>{live.label ?? vi.sim.label}</SimulatedBadge>
              <span>{vi.sim.warning}</span>
            </span>
          </Callout>
          <FilterTabs
            label={vi.sim.window}
            value={window}
            onChange={setWindow}
            options={WINDOWS.map((w) => ({ value: w, label: vi.sim.windows[w] }))}
          />
          {/* Each figure carries its own sparkline in the same column, so a line is never separated from its label. */}
          <dl className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-3">
            {(
              [
                [vi.sim.elapsed, live.elapsedSeconds, undefined, null, null],
                [vi.sim.heartRate, live.current.heartRate, 'heart-rate', live.recent.map((s) => s.heartRate), 'stroke-chart-4'],
                [vi.sim.speed, live.current.speed, 'speed', live.recent.map((s) => s.speed), 'stroke-chart-1'],
              ] as const
            ).map(([term, value, testId, series, tone]) => {
              const measure = testId === 'heart-rate' ? 'HEART_RATE' : 'SPEED';
              const limit = series ? live.thresholds.find((t) => t.measure === measure)?.limit : undefined;
              const figures = !series || !live.summary ? null : testId === 'heart-rate' ? live.summary.heartRate : live.summary.speed;
              return (
              <div key={term} className="grid content-start gap-0.5 border-b pb-2.5">
                <dt className="text-meta text-muted-foreground">{term}</dt>
                <dd data-testid={testId} className="m-0 font-display text-3xl font-medium tabular-nums">
                  {value}
                  {/* Inside the <dd>: a <dl> group may hold only its term and description. */}
                  {series && tone && (
                    <span className="block pt-2">
                      <Line values={series} tone={tone} label={term} threshold={limit} />
                    </span>
                  )}
                  {figures && (
                    <div className="font-sans text-base font-normal">
                      <Figures figures={figures} unit={testId === 'heart-rate' ? 'bpm' : 'km/h'} testId={`${testId}-figures`} />
                    </div>
                  )}
                  {limit !== undefined && (
                    <span className="block font-sans text-meta font-normal text-muted-foreground">
                      {vi.sim.threshold}: {limit} {testId === 'heart-rate' ? 'bpm' : 'km/h'}
                    </span>
                  )}
                </dd>
              </div>
              );
            })}
          </dl>
          {live.alerts.length > 0 && (
            <ul aria-label={vi.sim.alerts} className="grid gap-2">
              {live.alerts.map((a) => (
                <li key={a.measure} className="flex items-start gap-2 text-sm text-status-injured-ink">
                  <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
                  <span>
                    {vi.sim.measures[a.measure]} {vi.sim.exceeded} {a.limit}: {a.value} ({a.label})
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </Section>
  );
}
