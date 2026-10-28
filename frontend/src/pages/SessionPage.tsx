import { useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { CircleAlert, Play } from 'lucide-react';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { Indicators, Session } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import LivePanel from '../components/LivePanel';
import ResponsiblePicker from '../components/ResponsiblePicker';
import { TrainerBanner } from '../components/TrainerBanner';
import { LockBadge, SimulatedBadge } from '../components/StatusBadge';
import TrialVideosPanel from '../components/TrialVideosPanel';
import { useLoad } from '../hooks/useLoad';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogTitle,
} from '../components/ui/alert-dialog';
import { Button } from '../components/ui/button';
import { DataView } from '../components/ui/data-view';
import { Badge, DefinitionList, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input, Textarea } from '../components/ui/input';
import { Select } from '../components/ui/select';
import { SearchSelect, type Chosen } from '../components/ui/search-select';
import { SessionEnd } from '../components/SessionEnd';
import { Callout, DetailSkeleton } from '../components/ui/states';
import { hhmm } from '../i18n/time';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';

const emptyIndicators = (): Indicators => ({
  distanceM: '',
  durationMin: '',
  surface: '',
  intensity: '',
  form: '',
  avgHeartRate: '',
  maxSpeed: '',
  weightKg: '',
  comment: '',
  earlyEndReason: '',
});

/** States in which the responsible person may still change: not started (BR-115). */
const NOT_STARTED = ['DRAFT', 'SCHEDULED', 'BLOCKED_BY_LOCK', 'AWAITING_REVIEW'];

const STATE_TONE: Record<string, 'primary' | 'eligible' | 'watch' | 'locked' | 'neutral'> = {
  IN_PROGRESS: 'primary',
  COMPLETED: 'eligible',
  AUTO_CLOSED: 'eligible',
  AWAITING_REVIEW: 'watch',
  BLOCKED_BY_LOCK: 'locked',
};

/** A warning the server wants confirmed before the action goes ahead (BR-117, start with Cần theo dõi). */
function WarningDialog({
  warning,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onClose,
}: {
  warning: string | null;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <AlertDialog open={warning !== null} onOpenChange={(open) => !open && onClose()}>
      <AlertDialogContent>
        <AlertDialogTitle>{vi.training.warningTitle}</AlertDialogTitle>
        <AlertDialogDescription>{warning}</AlertDialogDescription>
        <AlertDialogFooter>
          <AlertDialogCancel>{cancelLabel}</AlertDialogCancel>
          <AlertDialogAction onClick={onConfirm}>{confirmLabel}</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

/**
 * The Head Trainer changes who is responsible for a session not yet started (PERM-006, BR-115); a change on the
 * session's own date asks to confirm the warning first, cancelling it changes nothing (BR-117).
 */
function ResponsibleForm({ session, onChanged }: { session: Session; onChanged: (s: Session) => void }) {
  // The current person keeps showing even when they are not among the active Grooms any more (BR-135).
  const [person, setPerson] = useState<Chosen | null>(
    session.responsiblePerson ? { id: session.responsiblePerson.id, label: session.responsiblePerson.displayName } : null,
  );
  const [picked, setPicked] = useState<number | ''>('');
  const [warning, setWarning] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  // A session with a start time offers only the Grooms who are free for it (BR-162); an older one has no range to check.
  const timed = Boolean(session.startTime);
  const chosenId = timed ? picked : (person?.id ?? '');

  async function save(acknowledgeWarning: boolean) {
    setError(null);
    if (chosenId === '') return;
    setPending(true);
    try {
      onChanged(await api.assignSession(session.id, chosenId, acknowledgeWarning));
      notify.success(vi.common.saved);
      setWarning(null);
      setPicked('');
    } catch (err) {
      if (err instanceof ApiError && err.code === 'WARNING_CONFIRMATION_REQUIRED') {
        setWarning(err.message);
      } else {
        setError(messageOf(err));
      }
    } finally {
      setPending(false);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    void save(false);
  }

  return (
    <form onSubmit={submit} aria-label={vi.training.changeResponsible} className="grid max-w-xl gap-3">
      <div className="flex flex-wrap items-start gap-3">
        {timed ? (
          <div className="w-full sm:w-72">
            <ResponsiblePicker
              horseId={session.horseId}
              date={session.date}
              startTime={session.startTime ?? ''}
              durationMin={session.plannedDurationMin}
              sessionId={session.id}
              value={picked}
              onChange={setPicked}
            />
          </div>
        ) : (
          <FormField label={vi.training.responsible} className="w-full sm:w-64">
            <SearchSelect
              value={person}
              placeholder={vi.training.chooseResponsible}
              load={(q) => api.options('grooms', { q })}
              onChange={setPerson}
            />
          </FormField>
        )}
        <Button type="submit" variant="outline" loading={pending} className="mt-[1.625rem]">
          {vi.training.changeResponsible}
        </Button>
      </div>
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert">
          {error}
        </Callout>
      )}
      <WarningDialog
        warning={warning}
        confirmLabel={vi.training.confirmChange}
        cancelLabel={vi.training.keepResponsible}
        onConfirm={() => void save(true)}
        onClose={() => setWarning(null)}
      />
    </form>
  );
}

/** Run one session: start (with the Cần theo dõi warning), end with indicators, or complete after auto-close. */
export default function SessionPage() {
  const id = Number(useParams().id);
  const { can } = useAuth();
  const load = useLoad(() => api.session(id), [id]);
  const [warning, setWarning] = useState<string | null>(null);
  const [form, setForm] = useState<Indicators>(emptyIndicators());
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  const current = load.state.data;
  const setSession = (session: Session) => load.set({ session, lockPendingAtEnd: current?.lockPendingAtEnd ?? false });

  async function start(acknowledgeWarning: boolean) {
    setError(null);
    setPending(true);
    try {
      setSession(await api.startSession(id, acknowledgeWarning));
      notify.success(vi.common.saved);
      setWarning(null);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'WARNING_CONFIRMATION_REQUIRED') {
        setWarning(e.message);
      } else {
        setError(messageOf(e));
      }
    } finally {
      setPending(false);
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!current) return;
    setError(null);
    setFieldErrors({});
    setPending(true);
    try {
      setSession(current.session.state === 'AUTO_CLOSED' ? await api.completeIndicators(id, form) : await api.endSession(id, form));
      notify.success(vi.common.saved);
      await load.reload();
    } catch (err) {
      setError(messageOf(err));
      setFieldErrors(fieldsOf(err));
    } finally {
      setPending(false);
    }
  }

  const run = can('RUN_SESSIONS');
  const num = (v: string) => (v === '' ? '' : Number(v));

  return (
    <DataView load={load} skeleton={<DetailSkeleton />}>
      {({ session, lockPendingAtEnd }) => {
        // Every Head Trainer reads the session; only the assigned one writes (BR-153), the service decides.
        const runs = run && session.canWrite;
        const schedules = can('SCHEDULE_SESSIONS') && session.canWrite;
        const finished = session.state === 'COMPLETED' || session.state === 'AUTO_CLOSED';
        const entering = session.state === 'IN_PROGRESS' || (session.state === 'AUTO_CLOSED' && session.missingIndicators);
        const type = vi.training.types[session.type];
        return (
          <>
            <PageHeader
              title={`${type} — ${session.date}${session.startTime ? ` ${hhmm(session.startTime)}` : ''}`}
              crumbs={[
                { label: vi.horses.title, to: '/horses' },
                { label: vi.training.plan, to: `/plans/${session.planId}` },
                { label: type },
              ]}
              meta={
                <>
                  <Badge tone={STATE_TONE[session.state] ?? 'neutral'}>{vi.training.sessionStates[session.state]}</Badge>
                  {session.overtime && <Badge tone="watch">{vi.training.overtime}</Badge>}
                  {session.lockDuringSession && <LockBadge>{vi.training.lockDuring}</LockBadge>}
                  {session.needsResponsible && <Badge tone="watch">{vi.assign.needsResponsible}</Badge>}
                </>
              }
              actions={
                runs &&
                session.state === 'SCHEDULED' && (
                  <Button onClick={() => void start(false)} loading={pending} disabled={session.responsiblePerson === null}>
                    <Play aria-hidden />
                    {vi.training.startSession}
                  </Button>
                )
              }
            />
            <div className="grid gap-8">
              <div className="grid gap-4">
                {run && !session.canWrite && <TrainerBanner trainer={session.assignedTrainer} />}
                <p className="m-0">
                  {vi.training.responsible}: {session.responsiblePerson?.displayName ?? vi.common.none}
                  {session.assignmentAck && (
                    <span className="text-muted-foreground"> ({vi.training.assignmentAck[session.assignmentAck]})</span>
                  )}
                </p>
                <p className="m-0">
                  {vi.training.endTime}:{' '}
                  <SessionEnd plannedEnd={session.plannedEnd} plannedEndNextDay={session.plannedEndNextDay} endedAt={session.endedAt} date={session.date} />
                </p>
                {session.needsResponsible && (
                  <Callout tone="warning" icon={CircleAlert} role="status">
                    {vi.assign.needsResponsible}
                    {session.declinedBy && session.declineReason && (
                      <span className="block text-meta">{vi.assign.lastDecline(session.declinedBy.displayName, session.declineReason)}</span>
                    )}
                  </Callout>
                )}
                {schedules && NOT_STARTED.includes(session.state) && (
                  <ResponsibleForm key={`${session.id}-${session.responsiblePerson?.id ?? 0}`} session={session} onChanged={setSession} />
                )}
                {session.state === 'IN_PROGRESS' && session.assignmentAck === 'UNCONFIRMED' && (
                  <Callout tone="warning" icon={CircleAlert} role="status">
                    {vi.training.unconfirmedReminder}
                  </Callout>
                )}
                {lockPendingAtEnd && (
                  <Callout tone="warning" icon={CircleAlert}>
                    {vi.training.pendingLockNotice}
                  </Callout>
                )}
                {session.state === 'AUTO_CLOSED' && session.missingIndicators && (
                  <Callout tone="warning" icon={CircleAlert}>
                    {vi.training.autoClosedNotice}
                  </Callout>
                )}
                {error && (
                  <Callout tone="danger" icon={CircleAlert} role="alert">
                    {error}
                  </Callout>
                )}
              </div>

              {session.state === 'IN_PROGRESS' && <LivePanel horseId={session.horseId} />}

              {runs && entering && (
                <form onSubmit={submit} noValidate aria-label={vi.training.indicators}>
                  <Section id="session-indicators" title={vi.training.indicators}>
                    <div className="grid max-w-4xl gap-5">
                      <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
                        <FormField label={vi.training.distance} error={fieldErrors.distanceM}>
                          <Input type="number" value={form.distanceM} onChange={(e) => setForm({ ...form, distanceM: num(e.target.value) })} />
                        </FormField>
                        <FormField label={vi.training.durationActual} error={fieldErrors.durationMin}>
                          <Input type="number" value={form.durationMin} onChange={(e) => setForm({ ...form, durationMin: num(e.target.value) })} />
                        </FormField>
                        <FormField label={vi.training.surface} error={fieldErrors.surface}>
                          <Input value={form.surface} onChange={(e) => setForm({ ...form, surface: e.target.value })} />
                        </FormField>
                        <FormField label={vi.training.intensity} error={fieldErrors.intensity}>
                          <Select
                            value={form.intensity}
                            onChange={(e) => setForm({ ...form, intensity: e.target.value as Indicators['intensity'] })}
                          >
                            <option value="">—</option>
                            <option value="LIGHT">{vi.training.intensities.LIGHT}</option>
                            <option value="MEDIUM">{vi.training.intensities.MEDIUM}</option>
                            <option value="HEAVY">{vi.training.intensities.HEAVY}</option>
                          </Select>
                        </FormField>
                        <FormField label={vi.training.form} error={fieldErrors.form}>
                          <Input type="number" min={1} max={5} value={form.form} onChange={(e) => setForm({ ...form, form: num(e.target.value) })} />
                        </FormField>
                        <FormField label={vi.training.avgHeartRate}>
                          <Input type="number" value={form.avgHeartRate} onChange={(e) => setForm({ ...form, avgHeartRate: num(e.target.value) })} />
                        </FormField>
                        <FormField label={vi.training.maxSpeed}>
                          <Input type="number" value={form.maxSpeed} onChange={(e) => setForm({ ...form, maxSpeed: num(e.target.value) })} />
                        </FormField>
                        <FormField label={vi.training.weight}>
                          <Input type="number" value={form.weightKg} onChange={(e) => setForm({ ...form, weightKg: num(e.target.value) })} />
                        </FormField>
                        {session.state === 'IN_PROGRESS' ? (
                          <FormField label={vi.training.earlyEndReason}>
                            <Input value={form.earlyEndReason ?? ''} onChange={(e) => setForm({ ...form, earlyEndReason: e.target.value })} />
                          </FormField>
                        ) : (
                          <FormField label={vi.training.actualEndTime}>
                            <Input
                              type="datetime-local"
                              onChange={(e) =>
                                setForm({ ...form, actualEndTime: e.target.value ? new Date(e.target.value).toISOString() : undefined })
                              }
                            />
                          </FormField>
                        )}
                      </div>
                      <FormField label={vi.training.comment} className="md:max-w-2xl">
                        <Textarea value={form.comment} onChange={(e) => setForm({ ...form, comment: e.target.value })} />
                      </FormField>
                      <div className="flex sm:justify-end">
                        <Button type="submit" loading={pending} className="w-full sm:w-auto">
                          {session.state === 'IN_PROGRESS' ? vi.training.endSession : vi.training.completeIndicators}
                        </Button>
                      </div>
                    </div>
                  </Section>
                </form>
              )}

              {finished && (
                <Section id="session-results" title={vi.training.indicators}>
                  <DefinitionList
                    className="max-w-4xl"
                    items={[
                      { term: vi.training.distance, value: session.distanceM ?? vi.common.none },
                      { term: vi.training.durationActual, value: session.durationMin ?? vi.common.none },
                      { term: vi.training.intensity, value: session.intensity ? vi.training.intensities[session.intensity] : vi.common.none },
                      { term: vi.training.form, value: session.form ?? vi.common.none },
                      {
                        term: vi.training.avgHeartRate,
                        value: (
                          <span className="inline-flex flex-wrap items-center gap-2">
                            {session.avgHeartRate ?? vi.common.none}
                            {session.avgHeartRateSource === 'SIMULATED' && <SimulatedBadge>{vi.training.simulated}</SimulatedBadge>}
                          </span>
                        ),
                      },
                      { term: vi.training.comment, value: session.comment ?? vi.common.none },
                      ...(session.earlyEndReason ? [{ term: vi.training.earlyEndReason, value: session.earlyEndReason }] : []),
                    ]}
                  />
                </Section>
              )}

              {session.type === 'TRIAL_RUN' && <TrialVideosPanel sessionId={session.id} completed={finished} canWrite={session.canWrite} />}
            </div>
            <WarningDialog
              warning={warning}
              confirmLabel={vi.training.confirmWarning}
              cancelLabel={vi.common.cancel}
              onConfirm={() => void start(true)}
              onClose={() => setWarning(null)}
            />
          </>
        );
      }}
    </DataView>
  );
}
