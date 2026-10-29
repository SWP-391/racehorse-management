import { useState } from 'react';
import { api } from '../api/api';
import { useAuth } from '../auth/AuthContext';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { clubMonth } from '../lib/utils';
import { StatementView } from './StatementView';
import { CategoryBreakdown, MonthlyTrend } from './CostCharts';
import { Skeleton } from './ui/states';
import { DataView } from './ui/data-view';
import { Section } from './ui/display';
import { Select } from './ui/select';

const MONTHS_BACK = 12;

/** The cost of one horse for a month: its owner (also a former one for their period) and the Club Manager (BR-182). */
export default function CostPanel({ horseId }: { horseId: number }) {
  const { can } = useAuth();
  const allowed = can('VIEW_BILLING');
  const [month, setMonth] = useState(clubMonth());
  const costs = useLoad(() => api.horseCosts(horseId, month), [horseId, month], allowed);
  if (!allowed) return null;
  return (
    <Section
      title={vi.billing.horsePanel}
      id="costs-section"
      actions={
        <Select aria-label={vi.billing.month} value={month} onChange={(e) => setMonth(e.target.value)} className="w-40">
          {Array.from({ length: MONTHS_BACK }, (_, i) => clubMonth(-i)).map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </Select>
      }
    >
      <div className="mb-4 grid gap-4 md:grid-cols-2">
        <MonthlyTrend title={vi.billing.charts.trendOwn} fetchTotal={(m) => api.horseCosts(horseId, m).then((s) => s.total)} deps={[horseId]} />
        <DataView load={costs} skeleton={<Skeleton className="h-72" />}>
          {(statement) => <CategoryBreakdown statement={statement} />}
        </DataView>
      </div>
      <DataView load={costs}>{(statement) => <StatementView statement={statement} />}</DataView>
    </Section>
  );
}
