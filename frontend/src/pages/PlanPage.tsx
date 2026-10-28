import { useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { CalendarCheck, CircleAlert, CircleX, Eye, Pencil } from 'lucide-react';
import { api } from '../api/api';
import {
  SESSION_TYPES,
  type Page,
  type Phase,
  type Plan,
  type PlanVersion,
  type Session,
  type SessionInput,
  type ShiftPreview,
} from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { useLoad } from '../hooks/useLoad';
import { usePagedList } from '../hooks/usePagedList';
import { SessionEnd } from '../components/SessionEnd';
import ResponsiblePicker from '../components/ResponsiblePicker';
import { TrainerBanner } from '../components/TrainerBanner';
import { Button } from '../components/ui/button';
import { ConfirmButton } from '../components/ui/confirm-button';
import { Checkbox } from '../components/ui/choice';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Badge, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input, Textarea } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { ListFooter } from '../components/ui/list-toolbar';
import { Select } from '../components/ui/select';
import { Callout, DetailSkeleton } from '../components/ui/states';
import { NoMatches, PanelSearch } from '../components/PanelSearch';
import { hhmm } from '../i18n/time';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';

const emptySession = (): SessionInput => ({
  date: '',
  startTime: '',
  type: '',
  plannedDurationMin: 60,
  targetDistanceM: '',
  targetSurface: '',
  responsiblePersonId: '',
  draft: false,
});

interface PlanBundle {
  plan: Plan;
  /** How many sessions wait for the Head Trainer's review after a lock (the list itself is paged). */
  awaitingReview: number;
  versions: Page<PlanVersion>;
  horseName: string | null;
  newHorse: boolean;
}

const toNumber = (v: string): number | '' => (v === '' ? '' : Number(v));

/** The planned end the form will give the session (start plus duration, BR-190), so the trainer sees it before saving. */
function endPreview(startTime: string, minutes: number | ''): string | undefined {
  if (!/^\d{2}:\d{2}/.test(startTime) || minutes === '' || minutes <= 0) return undefined;
  const total = Number(startTime.slice(0, 2)) * 60 + Number(startTime.slice(3, 5)) + minutes;
  const at = total % (24 * 60);
  const time = `${String(Math.floor(at / 60)).padStart(2, '0')}:${String(at % 60).padStart(2, '0')}`;
  return vi.training.plannedEndPreview(time, total >= 24 * 60);
}

/** A plan: phases, lifecycle, sessions, post-lock review and versions (BR-030 to BR-033, BR-022, BR-023). */
export default function PlanPage() {
  const id = Number(useParams().id);
  const { can } = useAuth();
  const [form, setForm] = useState<SessionInput>(emptySession());
  const [days, setDays] = useState<number | ''>('');
  const [preview, setPreview] = useState<ShiftPreview[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [reasonFor, setReasonFor] = useState<((reason: string) => Promise<unknown>) | null>(null);
  const [reason, setReason] = useState('');
  const [reasonTitle, setReasonTitle] = useState('');

  const [versionsQ, setVersionsQ] = useState('');
  const versionsQRef = useRef('');
  const bundle = useLoad<PlanBundle>(async () => {
    const plan = await api.plan(id);
    const awaitingReview = (await api.sessionsOf(id, { state: 'AWAITING_REVIEW', size: 1 })).total;
    const versions = await api.planVersions(id, { q: versionsQRef.current || undefined });
    let horseName: string | null = null;
    let newHorse = false;
    if (can('VIEW_HEALTH')) {
      const h = await api.horseHealth(plan.horseId);
      newHorse = h.newUnexamined;
      horseName = h.horseName;
    }
    return { plan, awaitingReview, versions, horseName, newHorse };
  }, [id, can]);
  const sessionList = usePagedList((query) => api.sessionsOf(id, query), { prefix: 's', filters: ['state'] });

  async function act(action: () => Promise<unknown>) {
    setError(null);
    setFieldErrors({});
    setPending(true);
    try {
      await action();
      await Promise.all([bundle.reload(), sessionList.load.reload()]);
    } catch (e) {
      setError(messageOf(e));
      setFieldErrors(fieldsOf(e));
    } finally {
      setPending(false);
    }
  }

  /** The picker only offers Grooms who are free, so a refusal here means the data changed since (BR-161, BR-162). */
  function save() {
    void act(async () => {
      await api.createSession(id, form);
      notify.success(vi.common.saved);
      setForm(emptySession());
    });
  }

  function addSession(e: FormEvent) {
    e.preventDefault();
    save();
  }

  /** Cancelling asks for a reason in a dialog (was window.prompt); the service decides whether it may be empty. */
  function askReason(title: string, run: (reason: string) => Promise<unknown>) {
    setReason('');
    setReasonTitle(title);
    setReasonFor(() => run);
  }

  function confirmReason(e: FormEvent) {
    e.preventDefault();
    const run = reasonFor;
    setReasonFor(null);
    if (run) void act(() => run(reason));
  }


  const phaseColumns: DataColumn<Phase>[] = [
    // The phase's name is the card title below xl; its number alone would be a bare "1" row above it.
    { id: 'ordinal', header: '#', className: 'tabular hidden xl:table-cell', cell: (p) => p.ordinal },
    { id: 'name', header: vi.training.phaseName, primary: true, cell: (p) => <span className="font-display text-[1.0625rem] font-medium">{p.name}</span> },
    { id: 'start', header: vi.training.start, cell: (p) => <span className="tabular">{p.startDate}</span> },
    { id: 'end', header: vi.training.end, cell: (p) => <span className="tabular">{p.endDate}</span> },
    { id: 'goal', header: vi.training.goal, cell: (p) => p.goal },
    { id: 'distance', header: vi.training.distance, cell: (p) => <span className="tabular">{p.distanceM}</span> },
    { id: 'volume', header: vi.training.weeklyVolume, cell: (p) => <span className="tabular">{p.weeklyVolumeKm}</span> },
    { id: 'surface', header: vi.training.surface, cell: (p) => p.surface },
  ];

  const sessionColumnsFor = (writes: boolean): DataColumn<Session>[] => [
    {
      id: 'date',
      header: vi.training.date,
      primary: true,
      cell: (s) => (
        <Link to={`/sessions/${s.id}`} className="tabular font-medium text-primary hover:underline">
          {s.date}
        </Link>
      ),
    },
    { id: 'time', header: vi.training.startTime, cell: (s) => <span className="tabular">{hhmm(s.startTime)}</span> },
    { id: 'type', header: vi.training.type, cell: (s) => vi.training.types[s.type] },
    { id: 'duration', header: vi.training.plannedDuration, cell: (s) => <span className="tabular">{s.plannedDurationMin}</span> },
    { id: 'end', header: vi.training.endTime, cell: (s) => <SessionEnd plannedEnd={s.plannedEnd} plannedEndNextDay={s.plannedEndNextDay} endedAt={s.endedAt} date={s.date} /> },
    {
      id: 'responsible',
      header: vi.training.responsible,
      cell: (s) => (
        <>
          {s.responsiblePerson?.displayName ?? vi.common.none}
          {s.assignmentAck && <span className="text-muted-foreground"> ({vi.training.assignmentAck[s.assignmentAck]})</span>}
          {s.needsResponsible && (
            <Badge tone="watch" className="ml-2">
              {vi.assign.needsResponsible}
            </Badge>
          )}
          {s.needsResponsible && s.declinedBy && s.declineReason && (
            <span className="block text-meta text-muted-foreground">{vi.assign.lastDecline(s.declinedBy.displayName, s.declineReason)}</span>
          )}
        </>
      ),
    },
    { id: 'state', header: vi.mySessions.state, cell: (s) => <Badge>{vi.training.sessionStates[s.state]}</Badge> },
    {
      id: 'actions',
      header: vi.common.rowActions,
      actions: true,
      cell: (s) => (
        <>
          {writes && s.state === 'DRAFT' && (
            <ConfirmButton
              title={vi.training.schedule}
              description={vi.training.scheduleText}
              confirmLabel={vi.training.schedule}
              onConfirm={() => act(() => api.scheduleSession(s.id))}
            >
              <CalendarCheck aria-hidden />
              {vi.training.schedule}
            </ConfirmButton>
          )}
          {writes && ['DRAFT', 'SCHEDULED', 'BLOCKED_BY_LOCK', 'AWAITING_REVIEW'].includes(s.state) && (
            <Button variant="destructive-outline" size="sm" onClick={() => askReason(vi.training.cancelSession, (reason) => api.cancelSession(s.id, reason))}>
              <CircleX aria-hidden />
              {vi.training.cancelSession}
            </Button>
          )}
        </>
      ),
    },
  ];
  // Row actions exist only for the assigned trainer, who may schedule or cancel; others get no empty column (BR-153).
  const sessionColumnsOf = (writes: boolean) => sessionColumnsFor(writes).filter((c) => !c.actions || writes);

  return (
    <DataView load={bundle} skeleton={<DetailSkeleton />}>
      {({ plan, awaitingReview, versions, horseName, newHorse }) => {
        // Every Head Trainer reads the plan; only the assigned one writes (BR-153), the service decides.
        const manage = can('MANAGE_PLANS') && plan.canWrite;
        const schedules = can('SCHEDULE_SESSIONS') && plan.canWrite;
        const sessionColumns = sessionColumnsOf(schedules);
        const awaiting = awaitingReview > 0;
        const editable = plan.state === 'DRAFT' || plan.state === 'ACTIVE';
        const title = `${plan.title ?? vi.training.plan} — ${vi.training.planStates[plan.state]}`;
        const crumbs = [
          { label: vi.horses.title, to: '/horses' },
          ...(horseName ? [{ label: horseName, to: `/horses/${plan.horseId}` }] : []),
          { label: plan.title ?? vi.training.plan },
        ];
        return (
          <>
            <PageHeader
              title={title}
              crumbs={crumbs}
              actions={
                manage && (
                  <>
                    {editable && (
                      <Button
                        variant="destructive-outline"
                        onClick={() => askReason(vi.training.cancelPlan, (reason) => api.cancelPlan(id, reason))}
                        disabled={pending}
                      >
                        {vi.training.cancelPlan}
                      </Button>
                    )}
                    {editable && (
                      <Button variant="outline" asChild>
                        <Link to={`/plans/${id}/edit`}>
                          <Pencil aria-hidden />
                          {vi.training.editPlan}
                        </Link>
                      </Button>
                    )}
                    {plan.state === 'DRAFT' && (
                      <Button onClick={() => void act(() => api.activatePlan(id))} loading={pending}>
                        {vi.training.activate}
                      </Button>
                    )}
                    {plan.state === 'ACTIVE' && (
                      <Button variant="outline" onClick={() => void act(() => api.completePlan(id))} loading={pending}>
                        {vi.training.complete}
                      </Button>
                    )}
                  </>
                )
              }
            />
            <div className="grid gap-8">
              {(newHorse || plan.completionSuggested || error || (can('MANAGE_PLANS') && !plan.canWrite)) && (
                <div className="grid gap-3">
                  {can('MANAGE_PLANS') && !plan.canWrite && <TrainerBanner trainer={plan.assignedTrainer} />}
                  {newHorse && (
                    <Callout tone="warning" icon={Eye}>
                      {vi.health.newHorse}
                    </Callout>
                  )}
                  {plan.completionSuggested && (
                    <Callout tone="info" icon={CircleAlert} role="status">
                      {vi.training.completionSuggested}
                    </Callout>
                  )}
                  {error && (
                    <Callout tone="danger" icon={CircleAlert} role="alert">
                      {error}
                    </Callout>
                  )}
                </div>
              )}

              <Section id="plan-phases" title={vi.training.phases}>
                <DataTable caption={vi.training.phases} data={plan.phases} columns={phaseColumns} rowKey={(p) => p.id ?? p.ordinal ?? p.name} />
              </Section>

              <Section id="plan-sessions" title={vi.training.sessions}>
                <DataView
                  load={sessionList.load}
                  isEmpty={(data) => data.items.length === 0}
                  empty={
                    <p className="text-sm text-muted-foreground">
                      {vi.training.noSessions} {schedules && editable && vi.training.noSessionsHint}
                    </p>
                  }
                >
                  {(data) => (
                    <div className="grid gap-3">
                      <DataTable caption={vi.training.sessions} data={data.items} columns={sessionColumns} rowKey={(s) => s.id} />
                      <ListFooter data={data} onPage={sessionList.setPage} onSize={sessionList.setSize} />
                    </div>
                  )}
                </DataView>
              </Section>

              {schedules && editable && (
                <Section id="plan-new-session" title={vi.training.newSession}>
                  <form onSubmit={addSession} aria-label={vi.training.newSession} noValidate className="grid max-w-4xl gap-5">
                    <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
                      <FormField label={vi.training.date} error={fieldErrors.date}>
                        <Input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} />
                      </FormField>
                      <FormField label={vi.training.startTime} error={fieldErrors.startTime}>
                        <Input type="time" required value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
                      </FormField>
                      <FormField label={vi.training.type} error={fieldErrors.type}>
                        <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as SessionInput['type'] })}>
                          <option value="">—</option>
                          {SESSION_TYPES.map((t) => (
                            <option key={t} value={t}>
                              {vi.training.types[t]}
                            </option>
                          ))}
                        </Select>
                      </FormField>
                      <FormField label={vi.training.plannedDuration} description={endPreview(form.startTime, form.plannedDurationMin)}>
                        <Input
                          type="number"
                          value={form.plannedDurationMin}
                          onChange={(e) => setForm({ ...form, plannedDurationMin: toNumber(e.target.value) })}
                        />
                      </FormField>
                      <FormField label={vi.training.targetDistance}>
                        <Input
                          type="number"
                          value={form.targetDistanceM}
                          onChange={(e) => setForm({ ...form, targetDistanceM: toNumber(e.target.value) })}
                        />
                      </FormField>
                      <FormField label={vi.training.targetSurface}>
                        <Input value={form.targetSurface} onChange={(e) => setForm({ ...form, targetSurface: e.target.value })} />
                      </FormField>
                      <ResponsiblePicker
                        horseId={plan.horseId}
                        date={form.date}
                        startTime={form.startTime}
                        durationMin={form.plannedDurationMin}
                        value={form.responsiblePersonId}
                        onChange={(next) => setForm((f) => ({ ...f, responsiblePersonId: next }))}
                        optional={form.draft || plan.state === 'DRAFT'}
                        error={fieldErrors.responsiblePersonId}
                      />
                    </div>
                    <div className="flex min-h-10 items-center gap-2">
                      <Checkbox
                        id="session-draft"
                        checked={form.draft}
                        onCheckedChange={(c) => setForm({ ...form, draft: c === true })}
                      />
                      <Label htmlFor="session-draft">{vi.training.draft}</Label>
                    </div>
                    <div className="flex sm:justify-end">
                      <Button type="submit" loading={pending} className="w-full sm:w-auto">
                        {vi.training.newSession}
                      </Button>
                    </div>
                  </form>
                </Section>
              )}

              {can('REVIEW_BLOCKED_SESSIONS') && plan.canWrite && plan.state === 'ACTIVE' && (
                <Section id="plan-review" title={vi.training.review}>
                  <div className="grid max-w-xl gap-4">
                    {awaiting && (
                      <div>
                        <Button variant="outline" onClick={() => void act(() => api.keepAfterLock(id))}>
                          {vi.training.keep}
                        </Button>
                      </div>
                    )}
                    <div className="flex flex-wrap items-end gap-3">
                      <FormField label={vi.training.shiftDays} className="w-full sm:w-56">
                        <Input type="number" value={days} onChange={(e) => setDays(toNumber(e.target.value))} />
                      </FormField>
                      <Button
                        variant="outline"
                        onClick={() =>
                          void act(async () => {
                            setPreview(await api.shiftPlan(id, Number(days), true));
                          })
                        }
                      >
                        <Eye aria-hidden />
                        {vi.training.preview}
                      </Button>
                    </div>
                    {preview && (
                      <>
                        <ul aria-label={vi.training.preview} className="m-0 grid list-none divide-y rounded-lg border bg-card p-0">
                          {preview.map((p) => (
                            <li key={p.sessionId} className="tabular px-4 py-2.5">
                              {p.from} → {p.to}
                            </li>
                          ))}
                        </ul>
                        <div>
                          <Button
                            onClick={() =>
                              void act(async () => {
                                await api.shiftPlan(id, Number(days), false);
                                notify.success(vi.common.saved);
                                setPreview(null);
                              })
                            }
                          >
                            {vi.training.applyShift}
                          </Button>
                        </div>
                      </>
                    )}
                  </div>
                </Section>
              )}

              {(versions.total > 0 || versionsQ) && (
                <Section id="plan-versions" title={vi.training.versions}>
                  <PanelSearch
                    data={versions}
                    q={versionsQ}
                    onSearch={(q) => {
                      setVersionsQ(q);
                      versionsQRef.current = q;
                      api
                        .planVersions(id, { q: q || undefined })
                        .then((next) => bundle.set({ plan, awaitingReview, versions: next, horseName, newHorse }))
                        .catch((e) => setError(messageOf(e)));
                    }}
                  />
                  {versions.total === 0 && <NoMatches />}
                  <ul className="m-0 grid list-none divide-y rounded-lg border bg-card p-0">
                    {versions.items.map((v) => (
                      <li key={v.versionNo} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
                        <span>
                          v{v.versionNo}: {v.reason} ({v.createdBy})
                        </span>
                        {manage && plan.state === 'ACTIVE' && (
                          <Button variant="outline" size="sm" onClick={() => void act(() => api.restoreVersion(id, v.versionNo))}>
                            {vi.training.restore} v{v.versionNo}
                          </Button>
                        )}
                      </li>
                    ))}
                  </ul>
                  <ListFooter
                    data={versions}
                    onPage={(page) =>
                      api
                        .planVersions(id, { page, q: versionsQ || undefined })
                        .then((next) => bundle.set({ plan, awaitingReview, versions: next, horseName, newHorse }))
                        .catch((e) => setError(messageOf(e)))
                    }
                  />
                </Section>
              )}
            </div>
            <Dialog open={reasonFor !== null} onOpenChange={(open) => !open && setReasonFor(null)}>
              <DialogContent>
                <form onSubmit={confirmReason} className="grid gap-4">
                  <DialogHeader>
                    <DialogTitle>{reasonTitle}</DialogTitle>
                    <DialogDescription>{plan.title ?? vi.training.plan}</DialogDescription>
                  </DialogHeader>
                  <FormField label={vi.training.reason}>
                    <Textarea value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
                  </FormField>
                  <DialogFooter>
                    <Button variant="outline" onClick={() => setReasonFor(null)}>
                      {vi.common.close}
                    </Button>
                    <Button type="submit" variant="destructive">
                      {reasonTitle}
                    </Button>
                  </DialogFooter>
                </form>
              </DialogContent>
            </Dialog>
          </>
        );
      }}
    </DataView>
  );
}
