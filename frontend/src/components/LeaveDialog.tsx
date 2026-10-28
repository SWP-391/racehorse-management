import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { CircleAlert, CircleCheck, Clock } from 'lucide-react';
import { api } from '../api/api';
import type { Account, Leave, Page } from '../api/types';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';
import { fieldsOf, messageOf } from './ErrorText';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Badge } from './ui/display';
import { FormField } from './ui/form-field';
import { Input } from './ui/input';
import { ListFooter } from './ui/list-toolbar';
import { Callout } from './ui/states';
import { NoMatches, PanelSearch } from './PanelSearch';

const dayLabel = (iso: string) => iso.split('-').reverse().join('/');

/**
 * The Club Manager records and cancels a Groom's leave (BR-167, PERM-047). A leave recorded with less notice than the
 * club's setting is labelled "đột xuất": a reminder only, never a block.
 */
export function LeaveDialog({ groom, onClose }: { groom: Account | null; onClose: () => void }) {
  const [leave, setLeave] = useState<Page<Leave> | null>(null);
  const [noticeDays, setNoticeDays] = useState<number | null>(null);
  const [date, setDate] = useState('');
  const [reason, setReason] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [q, setQ] = useState('');
  const qRef = useRef('');

  const reload = useCallback(async (id: number, page = 0) => {
    const next = await api.leave(id, { page, q: qRef.current || undefined });
    // A page left empty by a cancellation steps back; a new record shows from the first page.
    if (next.items.length === 0 && page > 0) return reload(id, page - 1);
    setLeave(next);
  }, []);

  useEffect(() => {
    if (!groom) return;
    setLeave(null);
    setQ('');
    qRef.current = '';
    setDate('');
    setReason('');
    setFieldErrors({});
    setError(null);
    reload(groom.id).catch((e) => setError(messageOf(e)));
    // The notice setting is the Club Manager's; the hint is skipped when it cannot be read.
    api.leaveNotice().then((n) => setNoticeDays(n.days)).catch(() => setNoticeDays(null));
  }, [groom, reload]);

  async function record(e: FormEvent) {
    e.preventDefault();
    if (!groom) return;
    if (!date) {
      setFieldErrors({ date: vi.leave.dateRequired });
      return;
    }
    setPending(true);
    setError(null);
    setFieldErrors({});
    try {
      await api.recordLeave(groom.id, { date, reason: reason.trim() || undefined });
      notify.success(vi.common.saved);
      setDate('');
      setReason('');
      await reload(groom.id);
    } catch (err) {
      setFieldErrors(fieldsOf(err));
      setError(messageOf(err));
    } finally {
      setPending(false);
    }
  }

  async function cancel(item: Leave) {
    if (!groom) return;
    try {
      await api.cancelLeave(item.id);
      notify.success(vi.common.saved);
      await reload(groom.id, leave?.page ?? 0);
    } catch (err) {
      setError(messageOf(err));
    }
  }

  return (
    <Dialog open={groom !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{groom && vi.leave.dialogTitle(groom.displayName)}</DialogTitle>
          <DialogDescription>{noticeDays !== null ? vi.leave.description(noticeDays) : vi.leave.reassignNote}</DialogDescription>
        </DialogHeader>
        {error && Object.keys(fieldErrors).length === 0 && (
          <Callout tone="danger" icon={CircleAlert} role="alert">
            {error}
          </Callout>
        )}
        <form className="grid gap-4" noValidate onSubmit={(e) => void record(e)}>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label={vi.leave.date} error={fieldErrors.date}>
              <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </FormField>
            <FormField label={vi.leave.reason}>
              <Input value={reason} onChange={(e) => setReason(e.target.value)} autoComplete="off" />
            </FormField>
          </div>
          <div>
            <Button type="submit" loading={pending}>
              {vi.leave.record}
            </Button>
          </div>
        </form>
        <section aria-label={vi.leave.upcoming} className="grid gap-2">
          <h3 className="text-sm font-medium">{vi.leave.upcoming}</h3>
          <PanelSearch
            data={leave}
            q={q}
            onSearch={(next) => {
              setQ(next);
              qRef.current = next;
              if (groom) void reload(groom.id).catch((e) => setError(messageOf(e)));
            }}
          />
          {leave && leave.total === 0 && (q ? <NoMatches /> : <p className="text-meta text-muted-foreground">{vi.leave.none}</p>)}
          <ul className="grid gap-2">
            {leave?.items.map((l) => (
              <li key={l.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border px-3 py-2">
                <span className="flex flex-wrap items-center gap-2">
                  <span className="tabular font-medium">{dayLabel(l.date)}</span>
                  {l.cancelled ? (
                    <Badge tone="outline">{vi.leave.cancelled}</Badge>
                  ) : l.late ? (
                    <Badge tone="watch" icon={<Clock aria-hidden />}>
                      {vi.leave.late}
                    </Badge>
                  ) : (
                    <Badge tone="eligible" icon={<CircleCheck aria-hidden />}>
                      {vi.leave.planned}
                    </Badge>
                  )}
                  <span className="text-meta text-muted-foreground">{vi.leave.noticeDays(l.noticeDays)}</span>
                  {l.reason && <span className="text-meta">{l.reason}</span>}
                </span>
                {!l.cancelled && (
                  <Button type="button" variant="ghost" size="sm" aria-label={vi.leave.cancelFor(dayLabel(l.date))} onClick={() => void cancel(l)}>
                    {vi.leave.cancel}
                  </Button>
                )}
              </li>
            ))}
          </ul>
          {leave && groom && <ListFooter data={leave} onPage={(page) => void reload(groom.id, page).catch((e) => setError(messageOf(e)))} />}
        </section>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>
            {vi.common.close}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
