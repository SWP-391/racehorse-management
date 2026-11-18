import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert } from 'lucide-react';
import { api } from '../api/api';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { fieldsOf, messageOf } from './ErrorText';
import { FormField } from './ui/form-field';
import { Textarea } from './ui/input';
import { Select } from './ui/select';
import { Callout } from './ui/states';
import { useLoad } from '../hooks/useLoad';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';

export const SUBJECT_MAX = 120;
export const TEXT_MAX = 2000;

/** The service keeps a short title for every conversation; the person never types one: it is the start of the text. */
export function subjectOf(text: string): string {
  const flat = text.trim().replace(/\s+/g, ' ');
  return flat.length <= SUBJECT_MAX ? flat : `${flat.slice(0, SUBJECT_MAX - 1)}…`;
}

/**
 * Starts a chat with a person (the horse, when the pair needs one, decides who is offered: BR-228, BR-229) or sends a
 * feedback to the Club Manager, which shows highlighted in their chat. The service checks the same rules again and its
 * refusal is shown next to the field it names.
 */
export function NewMessageDialog({
  open,
  mode,
  onClose,
  onStarted,
}: {
  open: boolean;
  mode: 'message' | 'feedback';
  onClose: () => void;
  onStarted: (id: number) => void;
}) {
  const t = vi.inbox;
  const feedback = mode === 'feedback';
  const [horseId, setHorseId] = useState('');
  const [recipient, setRecipient] = useState('');
  const [text, setText] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const options = useLoad(() => api.inboxRecipients(horseId ? Number(horseId) : null), [horseId], open && !feedback);
  const data = options.state.data;

  useEffect(() => {
    if (!open) return;
    setHorseId('');
    setRecipient('');
    setText('');
    setErrors({});
    setError(null);
  }, [open]);

  // A horse that changes the offer can drop the person chosen before.
  useEffect(() => {
    if (data && recipient && !data.recipients.some((r) => key(r) === recipient)) setRecipient('');
  }, [data, recipient]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const local: Record<string, string> = {};
    if (!feedback && !recipient) local.recipient = t.chooseRequired;
    if (!text.trim()) local.text = t.required;
    setErrors(local);
    if (Object.keys(local).length > 0) return;
    const chosen = feedback ? undefined : data?.recipients.find((r) => key(r) === recipient);
    setPending(true);
    try {
      const { id } = await api.startConversation({
        subject: subjectOf(text),
        text,
        horseId: feedback ? null : horseId ? Number(horseId) : null,
        kind: feedback ? 'FEEDBACK' : 'MESSAGE',
        recipientId: feedback || chosen?.clubManagers ? null : (chosen?.accountId ?? null),
        toClubManagers: feedback || !!chosen?.clubManagers,
      });
      notify.success(feedback ? t.feedbackSent : t.sent);
      onStarted(id);
      onClose();
    } catch (err) {
      setErrors(fieldsOf(err));
      setError(messageOf(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-lg">
        <form className="grid gap-4" noValidate onSubmit={(e) => void submit(e)}>
          <DialogHeader>
            <DialogTitle>{feedback ? t.feedbackTitle : t.newTitle}</DialogTitle>
            <DialogDescription>{feedback ? t.feedbackHint : t.newText}</DialogDescription>
          </DialogHeader>
          {error && Object.keys(errors).length === 0 && (
            <Callout tone="danger" icon={CircleAlert} role="alert">
              {error}
            </Callout>
          )}
          {!feedback && data && data.horses.length > 0 && (
            <FormField label={t.horse} description={t.horseHint} error={errors.horseId}>
              <Select value={horseId} onChange={(e) => setHorseId(e.target.value)}>
                <option value="">{t.noHorse}</option>
                {data.horses.map((h) => (
                  <option key={h.id} value={h.id}>
                    {h.name}
                  </option>
                ))}
              </Select>
            </FormField>
          )}
          {!feedback && (
            <FormField label={t.recipient} error={errors.recipient}>
              <Select value={recipient} onChange={(e) => setRecipient(e.target.value)}>
                <option value="">{t.chooseRecipient}</option>
                {data?.recipients.map((r) => (
                  <option key={key(r)} value={key(r)}>
                    {r.clubManagers ? r.name : `${r.name} · ${vi.roles[r.role]}`}
                  </option>
                ))}
              </Select>
            </FormField>
          )}
          <FormField label={feedback ? t.feedbackText : t.text} error={errors.text} description={t.counter(text.length, TEXT_MAX)}>
            <Textarea maxLength={TEXT_MAX} autoFocus={feedback} value={text} onChange={(e) => setText(e.target.value)} />
          </FormField>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {vi.common.cancel}
            </Button>
            <Button type="submit" loading={pending}>
              {t.send}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function key(r: { clubManagers: boolean; accountId: number | null }): string {
  return r.clubManagers ? 'club' : String(r.accountId);
}
