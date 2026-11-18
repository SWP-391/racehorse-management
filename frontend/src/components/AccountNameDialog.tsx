import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert } from 'lucide-react';
import { api } from '../api/api';
import type { Account } from '../api/types';
import { vi } from '../i18n/vi';
import { LIMITS } from '../lib/limits';
import { notify } from '../lib/notify';
import { fieldsOf, messageOf } from './ErrorText';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { FormField } from './ui/form-field';
import { Input } from './ui/input';
import { Callout } from './ui/states';

/**
 * The Club Manager changes the display name of any account, their own included (BR-239). No reason is asked; the
 * change is audited by the service with the old and the new name.
 */
export function AccountNameDialog({ account, onClose, onSaved }: { account: Account | null; onClose: () => void; onSaved: (account: Account) => void }) {
  const [name, setName] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!account) return;
    setName(account.displayName);
    setErrors({});
    setError(null);
  }, [account]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!account) return;
    setError(null);
    const tidy = name.trim().replace(/\s+/g, ' ');
    const local: Record<string, string> = {};
    if (!tidy) local.displayName = vi.validation.required;
    else if (tidy.length > LIMITS.displayName) local.displayName = vi.accounts.nameTooLong(LIMITS.displayName);
    setErrors(local);
    if (Object.keys(local).length > 0) return;
    setPending(true);
    try {
      await api.renameAccount(account.id, { displayName: name });
      notify.success(vi.common.saved);
      onSaved(account);
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
            <DialogTitle>{account && vi.accounts.renameDialogTitle(account.displayName)}</DialogTitle>
            <DialogDescription>{vi.accounts.renameDialogText}</DialogDescription>
          </DialogHeader>
          {error && Object.keys(errors).length === 0 && (
            <Callout tone="danger" icon={CircleAlert} role="alert">
              {error}
            </Callout>
          )}
          <FormField label={vi.accounts.displayName} error={errors.displayName}>
            <Input maxLength={LIMITS.displayName + 50} autoComplete="off" autoFocus value={name} onChange={(e) => setName(e.target.value)} />
          </FormField>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {vi.common.cancel}
            </Button>
            <Button type="submit" loading={pending}>
              {vi.accounts.renameSave}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
