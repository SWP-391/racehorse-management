import type { Session } from '../api/types';
import { vi } from '../i18n/vi';
import { Chart } from './chart/Chart';
import { SimulatedBadge } from './StatusBadge';

const isSimulated = (s: Session) => s.avgHeartRateSource === 'SIMULATED' || s.maxSpeedSource === 'SIMULATED';

/**
 * Recorded indicators over time: the form rating as an area chart, and distance, duration and intensity in an
 * always-visible table, with sessions whose heart rate or speed is simulated marked in both. Only recorded values are
 * shown; there is no computed fitness score (BR-042, BR-051).
 */
export default function TrendChart({ sessions, compact = false }: { sessions: Session[]; compact?: boolean }) {
  if (sessions.length === 0) {
    // In a board row the label is hidden on phones, so the bare dash would float without a name.
    return compact ? <span>{vi.training.trend}: {vi.common.none}</span> : <span>{vi.common.none}</span>;
  }
  const indicators = (
    <div tabIndex={0} role="group" aria-label={vi.sim.trendDetails} className="overflow-x-auto rounded-sm text-meta">
      <table aria-label={vi.sim.trendDetails} className="tabular w-full border-collapse text-left">
        <thead>
          <tr className="border-b text-muted-foreground">
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">{vi.training.date}</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">{vi.sim.trendDistance}</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">{vi.sim.trendDuration}</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">{vi.training.intensity}</th>
            <th className="py-1 font-semibold whitespace-nowrap">{vi.training.form}</th>
          </tr>
        </thead>
        <tbody>
          {sessions.slice(-5).map((s) => (
            <tr key={s.id} className="border-b last:border-b-0">
              <td className="py-1 pr-2">{s.date}</td>
              <td className="py-1 pr-2">{s.distanceM ?? vi.common.none}</td>
              <td className="py-1 pr-2">{s.durationMin ?? vi.common.none}</td>
              <td className="py-1 pr-2">{s.intensity ? vi.training.intensities[s.intensity] : vi.common.none}</td>
              <td className="py-1">
                {s.form ?? vi.common.none}
                {isSimulated(s) && (
                  <>
                    {' '}
                    <SimulatedBadge>{vi.sim.simulatedBadge}</SimulatedBadge>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );

  return (
    <div className="grid w-full min-w-0 gap-2 md:w-72">
      <Chart
        kind="area"
        title={vi.training.trend}
        height={120}
        categories={sessions.map((s) => s.date)}
        series={[{ name: vi.training.form, data: sessions.map((s) => s.form), marked: sessions.map(isSimulated) }]}
        markedLabel={vi.sim.simulatedBadge}
        markedLegend={vi.sim.trendLegend}
      />
      {sessions.length < 3 && <p className="m-0 text-meta text-muted-foreground">{vi.sim.sessionsCount(sessions.length)}</p>}
      {/* Every recorded indicator stays reachable (AC-066); intensity is categorical, so it is a table, not a series.
          In a board row (compact) it sits behind a disclosure so the row stays one line of chart, not a nested table. */}
      {compact ? (
        <details className="text-meta">
          <summary className="cursor-pointer rounded-sm py-1 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-ring">
            {vi.sim.trendDetails}
          </summary>
          {indicators}
        </details>
      ) : (
        indicators
      )}
      <small className="text-muted-foreground">{vi.sim.trendNote}</small>
    </div>
  );
}
