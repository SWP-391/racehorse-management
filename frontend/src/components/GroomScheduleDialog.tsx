import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert } from 'lucide-react';
import { api } from '../api/api';
import type { Account, ScheduleRange } from '../api/types';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';
import { fieldsOf, messageOf } from './ErrorText';
import { ScheduleEditor, fromServiceRanges, toServiceRanges } from './ScheduleEditor';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Callout } from './ui/states';

/** The Club Manager edits a Groom's fixed weekly barn hours (BR-166, PERM-047). */
export function GroomScheduleDialog({ groom, onClose, onSaved }: { groom: Account | null; onClose: () => void; onSaved: () => void }) {
  const [ranges, setRanges] = useState<ScheduleRange[] | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!groom) return;
    setRanges(null);
    setErrors({});
    setError(null);
    let stopped = false;
    api
      .groomSchedule(groom.id)
      .then((s) => !stopped && setRanges(fromServiceRanges(s.ranges)))
      .catch((e) => !stopped && setError(messageOf(e)));
    return () => {
      stopped = true;
    };
  }, [groom]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!groom || !ranges) return;
    setPending(true);
    setError(null);
    setErrors({});
    try {
      await api.setGroomSchedule(groom.id, toServiceRanges(ranges));
      notify.success(vi.common.saved);
      onSaved();
      onClose();
    } catch (err) {
      setErrors(fieldsOf(err));
      setError(messageOf(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={groom !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl">
        <form className="grid gap-4" noValidate onSubmit={(e) => void submit(e)}>
          <DialogHeader>
            <DialogTitle>{groom && vi.schedule.dialogTitle(groom.displayName)}</DialogTitle>
            <DialogDescription>{vi.schedule.description}</DialogDescription>
          </DialogHeader>
          {error && Object.keys(errors).length === 0 && (
            <Callout tone="danger" icon={CircleAlert} role="alert">
              {error}
            </Callout>
          )}
          {ranges && <ScheduleEditor value={ranges} onChange={setRanges} errors={errors} />}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {vi.common.cancel}
            </Button>
            <Button type="submit" loading={pending} disabled={!ranges}>
              {vi.schedule.save}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
