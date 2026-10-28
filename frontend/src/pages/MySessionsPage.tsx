import { useState, type FormEvent } from 'react';
import { CalendarX, CircleAlert, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import type { AssignedSession } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { messageOf } from '../components/ErrorText';
import { usePagedList } from '../hooks/usePagedList';
import { SessionEnd } from '../components/SessionEnd';
import { Button } from '../components/ui/button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Badge, PageHeader } from '../components/ui/display';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { FormField } from '../components/ui/form-field';
import { Textarea } from '../components/ui/input';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { Select } from '../components/ui/select';
import { Callout, EmptyState } from '../components/ui/states';
import { hhmm } from '../i18n/time';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';

/** States in which an assignment can still be confirmed: not started (BR-118). */
const NOT_STARTED = ['DRAFT', 'SCHEDULED', 'BLOCKED_BY_LOCK', 'AWAITING_REVIEW'];

/**
 * "Buổi tập được giao": the Groom's read-only list of sessions where they are the responsible person (BR-114), with
 * the "Đã nhận" confirmation of each assignment (BR-118, PERM-033).
 */
export default function MySessionsPage() {
  const { can } = useAuth();
  const allowed = can('VIEW_ASSIGNED_SESSIONS');
  const list = usePagedList((query) => api.mySessions(query), { filters: ['state'], enabled: allowed });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const [declining, setDeclining] = useState<AssignedSession | null>(null);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);

  async function acknowledge(id: number) {
    setError(null);
    setBusy(id);
    try {
      const updated = await api.acknowledgeSession(id);
      notify.success(vi.common.saved);
      const data = list.load.state.data;
      if (data) list.load.set({ ...data, items: data.items.map((s) => (s.id === id ? updated : s)) });
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setBusy(null);
    }
  }

  /** The Groom refuses with a reason; the trainer is told at once and the session leaves this list (BR-159). */
  async function decline(e: FormEvent) {
    e.preventDefault();
    if (!declining) return;
    setReasonError(null);
    setBusy(declining.id);
    try {
      await api.declineSession(declining.id, reason);
      notify.success(vi.assign.declined);
      setDeclining(null);
      await list.load.reload();
    } catch (err) {
      setReasonError(messageOf(err));
    } finally {
      setBusy(null);
    }
  }

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  const columns: DataColumn<AssignedSession>[] = [
    { id: 'horse', header: vi.mySessions.horse, primary: true, cell: (s) => <span className="font-display text-[1.0625rem] font-medium">{s.horseName}</span> },
    { id: 'date', fit: true, header: vi.mySessions.date, cell: (s) => <span className="tabular">{s.date}</span> },
    { id: 'time', fit: true, header: vi.training.startTime, cell: (s) => <span className="tabular">{hhmm(s.startTime)}</span> },
    { id: 'type', fit: true, header: vi.mySessions.type, cell: (s) => vi.training.types[s.type] },
    { id: 'duration', fit: true, header: vi.mySessions.duration, cell: (s) => <span className="tabular">{s.plannedDurationMin}</span> },
    { id: 'end', fit: true, header: vi.training.endTime, cell: (s) => <SessionEnd plannedEnd={s.plannedEnd} plannedEndNextDay={s.plannedEndNextDay} endedAt={s.endedAt} date={s.date} /> },
    { id: 'state', fit: true, header: vi.mySessions.state, cell: (s) => vi.training.sessionStates[s.state] },
    {
      id: 'ack',
      header: vi.mySessions.ack,
      cell: (s) => (
        <span className="flex flex-wrap items-center gap-2">
          {s.assignmentAck && (
            <Badge tone={s.assignmentAck === 'ACKNOWLEDGED' ? 'eligible' : 'watch'}>{vi.training.assignmentAck[s.assignmentAck]}</Badge>
          )}
          {s.assignmentAck === 'UNCONFIRMED' && NOT_STARTED.includes(s.state) && can('ACKNOWLEDGE_ASSIGNMENT') && (
            <Button variant="outline" size="sm" loading={busy === s.id} onClick={() => void acknowledge(s.id)}>
              {vi.mySessions.acknowledge}
            </Button>
          )}
          {s.assignmentAck !== null && NOT_STARTED.includes(s.state) && can('ACKNOWLEDGE_ASSIGNMENT') && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setReason('');
                setReasonError(null);
                setDeclining(s);
              }}
            >
              {vi.assign.decline}
            </Button>
          )}
        </span>
      ),
    },
  ];

  return (
    <>
      <PageHeader title={vi.mySessions.title} description={vi.mySessions.readOnly} />
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert" className="mb-4">
          {error}
        </Callout>
      )}
      <div className="mb-4">
        <ListToolbar query={list.query} onQuery={list.setQuery} placeholder={vi.mySessions.searchPlaceholder} total={list.load.state.data?.total}>
          <Select
            aria-label={vi.mySessions.state}
            value={list.filters.state ?? ''}
            onChange={(e) => list.setFilter('state', e.target.value)}
            className="w-52"
          >
            <option value="">{vi.incidents.anyState}</option>
            {Object.entries(vi.training.sessionStates).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </Select>
        </ListToolbar>
      </div>
      <DataView
        load={list.load}
        isEmpty={(data) => data.items.length === 0}
        empty={
          list.narrowed ? (
            <EmptyState icon={CalendarX} title={vi.common.noResults} text={vi.common.noResultsHint} />
          ) : (
            <EmptyState icon={CalendarX} title={vi.mySessions.empty} text={vi.mySessions.emptyHint} />
          )
        }
      >
        {(data) => (
          <div className="grid gap-3">
            <DataTable caption={vi.mySessions.title} data={data.items} columns={columns} rowKey={(s) => s.id} />
            <ListFooter data={data} onPage={list.setPage} onSize={list.setSize} />
          </div>
        )}
      </DataView>
      <Dialog open={declining !== null} onOpenChange={(open) => !open && setDeclining(null)}>
        <DialogContent>
          <form className="grid gap-4" noValidate onSubmit={(e) => void decline(e)}>
            <DialogHeader>
              <DialogTitle>{vi.assign.declineTitle}</DialogTitle>
              <DialogDescription>
                {declining && `${declining.horseName} — ${declining.date}`} {vi.assign.declineDescription}
              </DialogDescription>
            </DialogHeader>
            <FormField label={`${vi.assign.declineReason} *`} error={reasonError}>
              <Textarea value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
            </FormField>
            <DialogFooter>
              <Button variant="outline" onClick={() => setDeclining(null)}>
                {vi.common.cancel}
              </Button>
              <Button type="submit" variant="destructive" loading={busy !== null && busy === declining?.id}>
                {vi.assign.declineConfirm}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
