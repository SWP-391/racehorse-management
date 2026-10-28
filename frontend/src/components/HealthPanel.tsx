import { useState, type ReactNode } from 'react';
import { CircleAlert, Eye } from 'lucide-react';
import { api } from '../api/api';
import { HEALTH_STATUSES, type HealthStatus, type HorseHealth, type ReturnLevel } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { messageOf } from './ErrorText';
import { HealthBadge, LockBadge } from './StatusBadge';
import { Button } from './ui/button';
import { DataView } from './ui/data-view';
import { DefinitionList, Section } from './ui/display';
import { FormField } from './ui/form-field';
import { Input } from './ui/input';
import { Select } from './ui/select';
import { Callout, DetailSkeleton } from './ui/states';
import { ReadinessBadge } from './ReadinessBadge';

/** A titled group of fields and its action, ruled off from the next one. */
function ActionForm({ legend, children }: { legend: string; children: ReactNode }) {
  return (
    <fieldset className="grid gap-3 border-0 border-t p-0 pt-4 md:max-w-xl">
      <legend className="mb-3 font-medium">{legend}</legend>
      {children}
    </fieldset>
  );
}

/**
 * A horse's health status and training lock. The Veterinarian changes status, locks and lifts (BR-011, BR-016);
 * the Head Trainer may request a review (BR-016). Refusals come from the service.
 */
export default function HealthPanel({ horseId, archived, canWrite = true }: { horseId: number; archived: boolean; canWrite?: boolean }) {
  const { can } = useAuth();
  const allowed = can('VIEW_HEALTH');
  const load = useLoad(() => api.horseHealth(horseId), [horseId], allowed);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [status, setStatus] = useState<HealthStatus | ''>('');
  const [statusReason, setStatusReason] = useState('');
  const [lockReason, setLockReason] = useState('');
  const [reviewDate, setReviewDate] = useState('');
  const [returnLevel, setReturnLevel] = useState<ReturnLevel | ''>('');
  const [note, setNote] = useState('');

  if (!allowed) return null;

  /** Runs one action and clears only the inputs that belong to it, never what the user is typing elsewhere. */
  async function act(key: string, action: () => Promise<HorseHealth>, clear: () => void = () => undefined) {
    setError(null);
    setBusy(key);
    try {
      load.set(await action());
      clear();
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setBusy(null);
    }
  }

  const editable = !archived;

  return (
    <Section title={vi.health.section} id="health-section">
      <DataView load={load} skeleton={<DetailSkeleton />}>
        {(health) => {
          const lock = health.lock;
          // While the horse is new and unexamined, a lone history entry is its intake record, which the banner already says.
          const shownHistory = health.newUnexamined && health.history.length === 1 ? [] : health.history;
          return (
            <div className="grid gap-6">
              {health.newUnexamined && (
                <Callout tone="warning" icon={Eye}>
                  {vi.health.newHorse}
                </Callout>
              )}
              {error && (
                <Callout tone="danger" icon={CircleAlert} role="alert">
                  {error}
                </Callout>
              )}
              <DefinitionList
                items={[
                  { term: vi.health.status, value: <HealthBadge status={health.status} /> },
                  { term: vi.health.activity, value: vi.health.activities[health.activity] },
                  {
                    term: vi.health.lock,
                    value: lock ? (
                      <div className="grid gap-1">
                        <LockBadge>{`${vi.health.lockKinds[lock.kind]}: ${lock.reason}`}</LockBadge>
                        <span className="text-meta text-muted-foreground">
                          {vi.health.placedBy}: {lock.placedBy}
                          {lock.reviewDate && ` · ${vi.health.reviewDate}: ${lock.reviewDate}`}
                        </span>
                      </div>
                    ) : (
                      vi.health.noLock
                    ),
                  },
                  { term: vi.readiness.label, value: <ReadinessBadge value={health.readiness} /> },
                ]}
              />

              {lock && lock.reviewNotes.length > 0 && (
                <div className="grid gap-2">
                  <h3 className="font-display text-lg font-medium">{vi.health.reviewNotes}</h3>
                  <ul className="grid gap-1.5 text-sm">
                    {lock.reviewNotes.map((n, i) => (
                      <li key={i} className="border-b pb-1.5">
                        {n.author}: {n.note}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {editable && can('CHANGE_HEALTH_STATUS') && (
                <ActionForm legend={vi.health.changeStatus}>
                  <FormField label={vi.health.newStatus}>
                    <Select value={status} onChange={(e) => setStatus(e.target.value as HealthStatus | '')}>
                      <option value="">—</option>
                      {HEALTH_STATUSES.filter((s) => s !== health.status).map((s) => (
                        <option key={s} value={s}>
                          {vi.health.statuses[s]}
                        </option>
                      ))}
                    </Select>
                  </FormField>
                  <FormField label={vi.health.reason}>
                    <Input value={statusReason} onChange={(e) => setStatusReason(e.target.value)} />
                  </FormField>
                  <div>
                    <Button
                      type="button"
                      variant="outline"
                      loading={busy === 'status'}
                      onClick={() => void act('status', () => api.changeStatus(horseId, status || null, statusReason), () => { setStatus(''); setStatusReason(''); })}
                    >
                      {vi.health.changeStatus}
                    </Button>
                  </div>
                </ActionForm>
              )}

              {editable && can('MANAGE_LOCKS') && !lock && health.activity === 'IDLE' && (
                <ActionForm legend={vi.health.placeLock}>
                  <FormField label={vi.health.reason}>
                    <Input value={lockReason} onChange={(e) => setLockReason(e.target.value)} />
                  </FormField>
                  <FormField label={vi.health.reviewDate}>
                    <Input type="date" value={reviewDate} onChange={(e) => setReviewDate(e.target.value)} />
                  </FormField>
                  <div>
                    <Button
                      type="button"
                      variant="outline"
                      loading={busy === 'lock'}
                      onClick={() => void act('lock', () => api.lock(horseId, lockReason, reviewDate || null), () => { setLockReason(''); setReviewDate(''); })}
                    >
                      {vi.health.placeLock}
                    </Button>
                  </div>
                </ActionForm>
              )}

              {editable && can('MANAGE_LOCKS') && lock && (
                <ActionForm legend={vi.health.lift}>
                  <FormField label={vi.health.returnLevel}>
                    <Select value={returnLevel} onChange={(e) => setReturnLevel(e.target.value as ReturnLevel | '')}>
                      <option value="">—</option>
                      <option value="RESUME_AS_IS">{vi.health.returnLevels.RESUME_AS_IS}</option>
                      <option value="RESUME_WITH_ADJUSTMENT">{vi.health.returnLevels.RESUME_WITH_ADJUSTMENT}</option>
                    </Select>
                  </FormField>
                  <div>
                    <Button type="button" variant="outline" loading={busy === 'lift'} onClick={() => void act('lift', () => api.lift(horseId, returnLevel || null), () => setReturnLevel(''))}>
                      {vi.health.lift}
                    </Button>
                  </div>
                </ActionForm>
              )}

              {health.pendingLock && (
                <Callout tone="warning" icon={Eye}>
                  {vi.training.pendingLockNotice} ({health.pendingLock.requestedBy}: {health.pendingLock.reason})
                </Callout>
              )}

              {editable && can('REQUEST_PENDING_LOCK') && health.activity === 'TRAINING' && !health.pendingLock && (
                <ActionForm legend={vi.health.pendingLock}>
                  <FormField label={vi.health.reason}>
                    <Input value={lockReason} onChange={(e) => setLockReason(e.target.value)} />
                  </FormField>
                  <div>
                    <Button type="button" variant="outline" loading={busy === 'pending'} onClick={() => void act('pending', () => api.requestPendingLock(horseId, lockReason), () => setLockReason(''))}>
                      {vi.health.pendingLock}
                    </Button>
                  </div>
                </ActionForm>
              )}

              {editable && can('REQUEST_PENDING_LOCK') && health.pendingLock && (
                <div>
                  <Button type="button" variant="outline" loading={busy === 'withdraw'} onClick={() => void act('withdraw', () => api.withdrawPendingLock(horseId))}>
                    {vi.health.withdrawPendingLock}
                  </Button>
                </div>
              )}

              {editable && canWrite && can('REQUEST_LOCK_REVIEW') && lock && (
                <ActionForm legend={vi.health.requestReview}>
                  <FormField label={vi.health.note}>
                    <Input value={note} onChange={(e) => setNote(e.target.value)} />
                  </FormField>
                  <div>
                    <Button type="button" loading={busy === 'review'} onClick={() => void act('review', () => api.requestReview(horseId, note), () => setNote(''))}>
                      {vi.health.requestReview}
                    </Button>
                  </div>
                </ActionForm>
              )}

              {shownHistory.length > 0 && (
                <div className="grid gap-2 border-t pt-4">
                  <h3 className="font-display text-lg font-medium">{vi.health.history}</h3>
                  <ul className="grid gap-1.5 text-sm">
                    {shownHistory.map((h, i) => (
                      <li key={i} className="border-b pb-1.5">
                        {h.from ? `${vi.health.statuses[h.from]} → ` : ''}
                        {vi.health.statuses[h.to]}: {h.reason} ({h.changedBy})
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          );
        }}
      </DataView>
    </Section>
  );
}
