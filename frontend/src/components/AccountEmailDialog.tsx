import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert } from 'lucide-react';
import { api } from '../api/api';
import type { Account } from '../api/types';
import { vi } from '../i18n/vi';
import { LIMITS } from '../lib/limits';
import { notify } from '../lib/notify';
import { emailShape } from '../lib/validate';
import { fieldsOf, messageOf } from './ErrorText';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { FormField } from './ui/form-field';
import { Input } from './ui/input';
import { Callout } from './ui/states';

/**
 * The Club Manager enters the missing email of an account, or corrects it with a mandatory reason (BR-212). The
 * correction is audited and voids the live recovery code; nobody changes their own email, so the page never opens
 * this for the signed-in person.
 */
export function AccountEmailDialog({ account, onClose, onSaved }: { account: Account | null; onClose: () => void; onSaved: () => void }) {
  const correcting = account?.email != null;
  const [email, setEmail] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!account) return;
    setEmail('');
    setReason('');
    setErrors({});
    setError(null);
  }, [account]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!account) return;
    setError(null);
    const local: Record<string, string> = {};
    if (!email.trim()) local.email = vi.validation.required;
    else if (emailShape(email)) local.email = vi.validation.email;
    if (correcting && !reason.trim()) local.reason = 'Bắt buộc nhập lý do.';
    setErrors(local);
    if (Object.keys(local).length > 0) return;
    setPending(true);
    try {
      await api.changeEmail(account.id, { email, reason: correcting ? reason : undefined });
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
    <Dialog open={account !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-lg">
        <form className="grid gap-4" noValidate onSubmit={(e) => void submit(e)}>
          <DialogHeader>
            <DialogTitle>{account && vi.accounts.emailDialogTitle(account.displayName)}</DialogTitle>
            <DialogDescription>{correcting ? vi.accounts.emailDialogCorrect : vi.accounts.emailDialogEnter}</DialogDescription>
          </DialogHeader>
          {error && Object.keys(errors).length === 0 && (
            <Callout tone="danger" icon={CircleAlert} role="alert">
              {error}
            </Callout>
          )}
          {correcting && <p className="text-sm text-muted-foreground">{account?.email}</p>}
          <FormField label={vi.accounts.email} error={errors.email}>
            <Input type="email" inputMode="email" maxLength={LIMITS.email} autoComplete="off" autoFocus value={email} onChange={(e) => setEmail(e.target.value)} />
          </FormField>
          {correcting && (
            <FormField label={vi.accounts.emailReason} error={errors.reason}>
              <Input maxLength={500} autoComplete="off" value={reason} onChange={(e) => setReason(e.target.value)} />
            </FormField>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {vi.common.cancel}
            </Button>
            <Button type="submit" loading={pending}>
              {vi.accounts.emailSave}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
