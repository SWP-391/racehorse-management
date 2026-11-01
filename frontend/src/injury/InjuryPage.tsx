import { Component, lazy, Suspense, useState, type FormEvent, type ReactNode } from 'react';
import { useParams } from 'react-router-dom';
import { Bandage, Check, CircleAlert, CircleCheck, Eye, Info, MapPin, ShieldOff, TriangleAlert } from 'lucide-react';
import { api } from '../api/api';
import type { BodyRegion, InjuryMark, MarkStatus, Page } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { RegionSelect, regionLabel, useBodyRegions } from '../components/BodyRegions';
import { Button } from '../components/ui/button';
import { DataView } from '../components/ui/data-view';
import { Badge, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { Select } from '../components/ui/select';
import { Callout, EmptyState, ErrorState, Skeleton } from '../components/ui/states';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { useLoad } from '../hooks/useLoad';
import { NoMatches, PanelSearch } from '../components/PanelSearch';
import { useLocalPage } from '../hooks/useLocalPage';
import { ListFooter } from '../components/ui/list-toolbar';
import { vi } from '../i18n/vi';
import { ALL_LAYERS, LAYERS, nextStatuses, supportsWebGl, type LayerState } from './injuryModel';
import type { ViewerProps } from './HorseViewer';
import { notify } from '../lib/notify';

const HorseViewer = lazy(() => import('./HorseViewer'));

/** Keeps the page usable when the 3D view cannot start (BR-102, AC-134). */
class ViewerBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? (
      <Callout tone="warning" icon={TriangleAlert}>
        {vi.injury.viewerFailed}
      </Callout>
    ) : (
      this.props.children
    );
  }
}

function Viewer(props: ViewerProps) {
  const [webGl] = useState(supportsWebGl);
  if (!webGl)
    return (
      <Callout tone="warning" icon={TriangleAlert}>
        {vi.injury.noWebGl}
      </Callout>
    );
  return (
    <ViewerBoundary>
      <Suspense fallback={<Skeleton className="h-[460px] w-full" />}>
        <HorseViewer {...props} />
      </Suspense>
    </ViewerBoundary>
  );
}

/** Recovery status as wash + ink + icon, so colour is never the only cue. */
const MARK_BADGE = {
  NEW: { tone: 'injured', icon: Bandage },
  RECOVERING: { tone: 'watch', icon: Eye },
  RECOVERED: { tone: 'eligible', icon: CircleCheck },
  CANCELLED: { tone: 'neutral', icon: CircleAlert },
} as const;

function MarkBadge({ status }: { status: MarkStatus }) {
  const b = MARK_BADGE[status];
  const Icon = b.icon;
  return (
    <Badge tone={b.tone} icon={<Icon aria-hidden />}>
      {vi.injury.statuses[status]}
    </Badge>
  );
}

export function LayerToggles({ layers, onChange }: { layers: LayerState; onChange: (l: LayerState) => void }) {
  return (
    <div role="group" aria-label={vi.injury.layers} className="flex flex-wrap gap-2">
      {LAYERS.map((layer) => (
        <Button
          key={layer}
          type="button"
          variant="outline"
          size="sm"
          aria-pressed={layers[layer]}
          className="aria-pressed:border-primary aria-pressed:bg-accent aria-pressed:text-accent-foreground"
          onClick={() => onChange({ ...layers, [layer]: !layers[layer] })}
        >
          {layers[layer] && <Check aria-hidden />}
          {vi.injury.layerNames[layer]}
        </Button>
      ))}
    </div>
  );
}

/**
 * Injury marks of one horse on the shared 3D model (REQ-014): the Veterinarian marks a region linked to an
 * examination and follows recovery; the Head Trainer reads everything read-only (BR-096 to BR-103).
 * The list is always visible and comes first below lg; from lg the viewer sits beside it (BR-102, NFR-013).
 */
export default function InjuryPage() {
  const id = Number(useParams().id);
  const desktop = useMediaQuery('(min-width: 768px)');
  const { can } = useAuth();
  const allowed = can('VIEW_INJURY_MARKS');
  const manage = can('MANAGE_INJURY_MARKS');
  const horse = useLoad(() => api.horse(id), [id], allowed);
  // The model and the list below it need every mark still standing together (BR-099, BR-102), so they take the largest
  // page the service gives; the cancelled marks are history and page by 20.
  const marks = useLoad(() => api.injuryMarks(id, { cancelled: false, size: 100 }).then((p) => p.items), [id], allowed);
  const cancelledMarks = useLocalPage((query) => api.injuryMarks(id, { ...query, cancelled: true }), [id], allowed);
  // The mark links to one of the horse's most recent examinations (the service pages at 100 at most, BR-098).
  const exams = useLoad(() => api.examinations(id, 0, 100).then((p) => p.items), [id], allowed);
  const regions = useBodyRegions(allowed);
  const [layers, setLayers] = useState<LayerState>(ALL_LAYERS);
  const [selected, setSelected] = useState<string | null>(null);
  const [examinationId, setExaminationId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  const horseData = horse.state.data;
  const writable = manage && horseData !== null && !horseData.archived;
  // A cancelled mark leaves the model and the active list but stays in history (BR-112).
  const active = marks.state.data ?? [];
  const refreshMarks = async () => {
    await Promise.all([marks.reload(), cancelledMarks.load.reload()]);
  };
  const loadError = horse.state.data === null && horse.state.status === 'error' ? horse.state.error : exams.state.status === 'error' && exams.state.data === null ? exams.state.error : null;

  async function create(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setPending(true);
    try {
      await api.createInjuryMark(id, selected ?? '', examinationId ? Number(examinationId) : null);
      notify.success(vi.common.saved);
      setExaminationId('');
      await refreshMarks();
    } catch (err) {
      setError(messageOf(err));
      setFieldErrors(fieldsOf(err));
    } finally {
      setPending(false);
    }
  }

  const crumbs = [
    { label: vi.horses.title, to: '/horses' },
    ...(horseData ? [{ label: horseData.name, to: `/horses/${id}` }] : []),
    { label: vi.injury.title },
  ];

  return (
    <>
      <PageHeader title={`${vi.injury.title}${horseData ? ` — ${horseData.name}` : ''}`} crumbs={crumbs} />
      <div className="grid gap-6">
        {horseData?.archived && (
          <Callout tone="info" icon={CircleAlert}>
            {vi.injury.archivedNotice}
          </Callout>
        )}
        {loadError && <ErrorState message={loadError} onRetry={() => void Promise.all([horse.reload(), exams.reload()])} />}
        {error && (
          <Callout tone="danger" icon={CircleAlert} role="alert">
            {error}
          </Callout>
        )}
        {/* grid-cols-1 and min-w-0: a long option in the examination select must not widen the column past the screen. */}
        <div className="grid grid-cols-1 items-start gap-8 xl:grid-cols-2">
          <div aria-label={vi.injury.viewerLabel} className="order-2 grid min-w-0 gap-3 xl:sticky xl:top-6 xl:order-1">
            {desktop ? (
              <>
                <LayerToggles layers={layers} onChange={setLayers} />
                <Viewer
                  layers={layers}
                  selected={selected}
                  marks={active.map((m) => ({ id: m.id, regionCode: m.regionCode, status: m.status }))}
                  onSelect={setSelected}
                />
                <p className="text-meta text-muted-foreground">{vi.injury.hint}</p>
              </>
            ) : (
              // NFR-013, BR-102: the 3D view is for desktop browsers; the marks list is complete on any device.
              <Callout tone="info" icon={Info}>
                {vi.injury.desktopOnly}
              </Callout>
            )}
          </div>
          <div className="order-1 grid min-w-0 gap-6 lg:order-2">
            <div className="grid grid-cols-1 gap-4">
              <RegionSelect
                regions={regions}
                value={selected ?? ''}
                onChange={(code) => setSelected(code || null)}
                error={fieldErrors.regionCode}
              />
              {writable && (
                <form onSubmit={create} noValidate aria-label={vi.injury.add} className="grid grid-cols-1 gap-4">
                  <FormField label={vi.injury.examination} error={fieldErrors.examinationId}>
                    <Select value={examinationId} onChange={(e) => setExaminationId(e.target.value)}>
                      <option value="">{vi.injury.chooseExamination}</option>
                      {(exams.state.data ?? []).map((x) => (
                        <option key={x.id} value={x.id}>
                          {x.examDate} — {x.diagnosis}
                        </option>
                      ))}
                    </Select>
                  </FormField>
                  <div>
                    <Button type="submit" loading={pending}>
                      {vi.injury.add}
                    </Button>
                  </div>
                </form>
              )}
            </div>
            <Section title={vi.injury.list} id="injury-list">
              <DataView
                load={marks}
                isEmpty={() => active.length === 0}
                empty={<EmptyState icon={MapPin} title={vi.injury.noMarks} />}
              >
                {() => (
                  <ul className="grid">
                    {active.map((m) => (
                      <InjuryItem key={m.id} mark={m} regions={regions} writable={writable} onSelect={setSelected} onChanged={refreshMarks} />
                    ))}
                  </ul>
                )}
              </DataView>
            </Section>
            <CancelledList page={cancelledMarks.load.state.data} onPage={cancelledMarks.setPage} q={cancelledMarks.q} onSearch={cancelledMarks.setQ} regions={regions} />
          </div>
        </div>
      </div>
    </>
  );
}

/** One mark of the always-visible list with its history (BR-102); status changes for the Veterinarian only. */
function InjuryItem({
  mark: m,
  regions,
  writable,
  onSelect,
  onChanged,
}: {
  mark: InjuryMark;
  regions: BodyRegion[];
  writable: boolean;
  onSelect: (code: string) => void;
  onChanged: () => Promise<void>;
}) {
  return (
    <li aria-label={regionLabel(regions, m.regionCode)} className="grid grid-cols-1 gap-2 border-b py-4 first:pt-0">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <button
          type="button"
          className="min-h-10 rounded-sm font-display text-[1.0625rem] font-medium text-foreground hover:text-primary hover:underline lg:min-h-0"
          onClick={() => onSelect(m.regionCode)}
        >
          {regionLabel(regions, m.regionCode)}
        </button>
        <MarkBadge status={m.status} />
      </div>
      <p className="tabular text-sm text-muted-foreground">
        {m.history[0]?.date ?? ''} — {vi.injury.examination}: {m.examinationDate} {m.examinationDiagnosis}
      </p>
      <History mark={m} />
      {writable && <StatusForm mark={m} onChanged={onChanged} />}
      {writable && m.status === 'NEW' && <CancelForm mark={m} onChanged={onChanged} />}
    </li>
  );
}

function History({ mark }: { mark: InjuryMark }) {
  return (
    <details className="text-sm">
      <summary className="flex min-h-10 cursor-pointer items-center rounded-sm text-muted-foreground hover:text-foreground lg:[@media(pointer:fine)]:min-h-8">
        {vi.injury.history}
      </summary>
      <ol className="grid gap-1 pt-1 pl-4">
        {mark.history.map((h, i) => (
          <li key={i}>
            {h.date}: {vi.injury.statuses[h.to]} — {vi.injury.by} {h.by}
            {h.note && ` — ${h.note}`}
          </li>
        ))}
      </ol>
    </details>
  );
}

/** Marks cancelled as entered by mistake: kept with their reason, never shown on the model (BR-112). */
function CancelledList({ page, onPage, q, onSearch, regions }: { page: Page<InjuryMark> | null; onPage: (page: number) => void; q: string; onSearch: (q: string) => void; regions: BodyRegion[] }) {
  if (!page || (page.total === 0 && !q)) return null;
  const marks = page.items;
  return (
    <section aria-labelledby="injury-cancelled" className="grid gap-2 border-t pt-4">
      <h3 id="injury-cancelled" className="font-display text-lg font-medium">
        {vi.injury.cancelled}
      </h3>
      <PanelSearch data={page} q={q} onSearch={onSearch} />
      {page.total === 0 && <NoMatches />}
      <ul className="grid text-sm">
        {marks.map((m) => (
          <li key={m.id} className="grid gap-1 border-b py-2">
            <span>
              {regionLabel(regions, m.regionCode)} — <strong>{vi.injury.statuses[m.status]}</strong> —{' '}
              {m.history[m.history.length - 1]?.note}
            </span>
            <History mark={m} />
          </li>
        ))}
      </ul>
      <ListFooter data={page} onPage={onPage} />
    </section>
  );
}

/** Cancel a mark entered by mistake: reason required, only while Mới; the date is always today (BR-112, BR-113). */
function CancelForm({ mark, onChanged }: { mark: InjuryMark; onChanged: () => Promise<void> }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [pending, setPending] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldError(undefined);
    setPending(true);
    try {
      await api.cancelMark(mark.id, reason);
      notify.success(vi.common.saved);
      setReason('');
      await onChanged();
    } catch (err) {
      setError(messageOf(err));
      setFieldError(fieldsOf(err).reason);
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} aria-label={vi.injury.cancel} noValidate className="grid items-end gap-3 sm:grid-cols-[1fr_auto]">
      <FormField label={vi.injury.cancelReason} error={fieldError}>
        <Input value={reason} onChange={(e) => setReason(e.target.value)} />
      </FormField>
      <Button type="submit" variant="destructive-outline" loading={pending}>
        {vi.injury.cancel}
      </Button>
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert" className="sm:col-span-2">
          {error}
        </Callout>
      )}
    </form>
  );
}

function StatusForm({ mark, onChanged }: { mark: InjuryMark; onChanged: () => Promise<void> }) {
  const options = nextStatuses(mark.status);
  const [status, setStatus] = useState<MarkStatus | ''>('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  if (options.length === 0) return <p className="text-sm text-muted-foreground">{vi.injury.recoveredFinal}</p>;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!status) return;
    setPending(true);
    try {
      await api.changeMarkStatus(mark.id, status, note);
      notify.success(vi.common.saved);
      setStatus('');
      setNote('');
      await onChanged();
    } catch (err) {
      setError(messageOf(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} aria-label={vi.injury.changeStatus} className="grid items-end gap-3 sm:grid-cols-[1fr_1fr_auto]">
      <FormField label={vi.injury.newStatus}>
        <Select value={status} onChange={(e) => setStatus(e.target.value as MarkStatus | '')}>
          <option value="">—</option>
          {options.map((s) => (
            <option key={s} value={s}>
              {vi.injury.statuses[s]}
            </option>
          ))}
        </Select>
      </FormField>
      <FormField label={vi.injury.note}>
        <Input value={note} onChange={(e) => setNote(e.target.value)} />
      </FormField>
      <Button type="submit" loading={pending}>
        {vi.injury.changeStatus}
      </Button>
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert" className="sm:col-span-3">
          {error}
        </Callout>
      )}
    </form>
  );
}
