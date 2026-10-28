import { useCallback, useEffect, useState } from 'react';
import { BellOff, CircleAlert } from 'lucide-react';
import { api } from '../api/api';
import type { Measure, Page, TelemetryAlert, Threshold } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from './ErrorText';
import { vi } from '../i18n/vi';
import { Button } from './ui/button';
import { Section } from './ui/display';
import { FormField } from './ui/form-field';
import { Input } from './ui/input';
import { ListFooter } from './ui/list-toolbar';
import { Select } from './ui/select';
import { Callout, ErrorState, Skeleton } from './ui/states';
import { useDelayedFlag } from '../hooks/useDelayedFlag';
import { notify } from '../lib/notify';
import { NoMatches, PanelSearch } from './PanelSearch';

const MEASURES: Measure[] = ['HEART_RATE', 'SPEED'];

/**
 * Per-horse alert thresholds set by the Head Trainer, with no default (BR-052), and the alerts raised. A Veterinarian
 * sees the alerts only (PERM-010). Alerts never change status or lock.
 */
export default function ThresholdsPanel({ horseId, canWrite = true }: { horseId: number; canWrite?: boolean }) {
  const { can } = useAuth();
  const [thresholds, setThresholds] = useState<Threshold[]>([]);
  const [alertPage, setAlertPage] = useState<Page<TelemetryAlert> | null>(null);
  const [alertQ, setAlertQ] = useState('');
  const alerts = alertPage?.items ?? [];
  const [measure, setMeasure] = useState<Measure>('HEART_RATE');
  const [limit, setLimit] = useState<number | ''>('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [loadState, setLoadState] = useState<'loading' | 'error' | 'ready'>('loading');
  const [loadError, setLoadError] = useState('');
  const edit = can('SET_THRESHOLDS');
  const watch = can('RECEIVE_ALERTS');

  // Unknown is not "none": until the first answer the panel shows a skeleton, never "no threshold" (ui-kit contract 7).
  const load = useCallback(async () => {
    setLoadState('loading');
    try {
      if (edit) setThresholds(await api.thresholds(horseId));
      if (watch) setAlertPage(await api.alerts(horseId));
      setLoadState('ready');
    } catch (e) {
      setLoadError(messageOf(e));
      setLoadState('error');
    }
  }, [edit, watch, horseId]);
  const showSkeleton = useDelayedFlag(loadState === 'loading');

  useEffect(() => {
    void load();
  }, [load]);

  if (!edit && !watch) return null;

  if (loadState !== 'ready') {
    return (
      <Section title={vi.sim.thresholds} id="thresholds-section">
        {loadState === 'error' ? (
          <ErrorState message={loadError} onRetry={() => void load()} />
        ) : (
          showSkeleton && <Skeleton className="h-24 md:max-w-xl" />
        )}
      </Section>
    );
  }

  async function save(value: number | null, m: Measure) {
    setError(null);
    setFieldErrors({});
    setPending(true);
    try {
      setThresholds(await api.setThreshold(horseId, m, value));
      notify.success(vi.common.saved);
      setLimit('');
    } catch (e) {
      setError(messageOf(e));
      setFieldErrors(fieldsOf(e));
    } finally {
      setPending(false);
    }
  }

  return (
    <Section title={vi.sim.thresholds} id="thresholds-section">
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert">
          {error}
        </Callout>
      )}
      {edit && (
        <div className="grid gap-4 md:max-w-xl">
          {thresholds.length === 0 ? (
            <Callout tone="info" icon={BellOff}>
              {vi.sim.noThreshold}
            </Callout>
          ) : (
            <ul className="grid text-sm">
              {thresholds.map((t) => (
                <li key={t.measure} className="flex items-center justify-between gap-3 border-b py-1.5">
                  <span className="tabular">
                    {vi.sim.measures[t.measure]}: {t.limit}
                  </span>
                  {canWrite && (
                    <Button type="button" variant="ghost" size="sm" onClick={() => void save(null, t.measure)}>
                      {vi.sim.clear}
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
          {canWrite && (
          <>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label={vi.sim.measure}>
              <Select value={measure} onChange={(e) => setMeasure(e.target.value as Measure)}>
                {MEASURES.map((m) => (
                  <option key={m} value={m}>
                    {vi.sim.measures[m]}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField label={vi.sim.limit} error={fieldErrors.limit}>
              <Input type="number" value={limit} onChange={(e) => setLimit(e.target.value === '' ? '' : Number(e.target.value))} />
            </FormField>
          </div>
          <div>
            <Button type="button" variant={can('REQUEST_LOCK_REVIEW') ? 'outline' : 'default'} loading={pending} onClick={() => void save(limit === '' ? null : limit, measure)}>
              {vi.sim.set}
            </Button>
          </div>
          </>
          )}
        </div>
      )}
      {watch && (
        <div className="grid gap-2">
          {/* A lone heading over "no alerts" is noise for a Veterinarian, who has no thresholds section above it. */}
          {(edit || alerts.length > 0 || alertQ) && <h3 className="font-display text-lg font-medium">{vi.sim.alerts}</h3>}
          <PanelSearch
            data={alertPage}
            q={alertQ}
            onSearch={(q) => {
              setAlertQ(q);
              api
                .alerts(horseId, { q: q || undefined })
                .then(setAlertPage)
                .catch((e) => setError(messageOf(e)));
            }}
          />
          {alerts.length === 0 ? (
            alertQ ? <NoMatches /> : <p className="text-sm text-muted-foreground">{vi.sim.noAlerts}</p>
          ) : (
            <ul className="grid text-sm">
              {alerts.map((a, i) => (
                <li key={i} className="border-b py-1.5">
                  {vi.sim.measures[a.measure]} {vi.sim.exceeded} {a.limit}: {a.value} ({a.label})
                </li>
              ))}
            </ul>
          )}
          {alertPage && (
            <ListFooter
              data={alertPage}
              onPage={(page) =>
                api
                  .alerts(horseId, { page, q: alertQ || undefined })
                  .then(setAlertPage)
                  .catch((e) => setError(messageOf(e)))
              }
            />
          )}
        </div>
      )}
    </Section>
  );
}
