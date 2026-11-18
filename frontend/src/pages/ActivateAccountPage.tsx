import { useState, type FormEvent } from 'react';
import { CircleAlert, KeyRound } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import { vi } from '../i18n/vi';
import { LIMITS } from '../lib/limits';
import { Wordmark } from '../components/layout/Wordmark';
import { Button } from '../components/ui/button';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { Callout } from '../components/ui/states';

/**
 * A person with an account the Club Manager created without a password gives the email, the mailed code and the
 * password they choose (BR-237). It uses the same confirmation as the recovery of a forgotten password; the page
 * never shows a code and a refusal is the same for every cause.
 */
export default function ActivateAccountPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const local: Record<string, string> = {};
    if (!email.trim()) local.email = vi.validation.required;
    if (!code.trim()) local.code = vi.validation.required;
    if (password.length < 8) local.newPassword = vi.recovery.tooShort;
    else if (password !== repeat) local.repeat = vi.recovery.mismatch;
    setFields(local);
    if (Object.keys(local).length > 0) return;
    setPending(true);
    try {
      await api.recoveryConfirm({ email: email.trim(), code: code.trim(), newPassword: password });
      navigate('/', { replace: true, state: { activated: true } });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
        setFields(err.fields);
      } else {
        setError(vi.common.unexpectedError);
        setFields({});
      }
    } finally {
      setPending(false);
    }
  }

  const banner = error && Object.keys(fields).length === 0;

  return (
    <main id="main" className="grid min-h-dvh place-items-start justify-center px-5 py-10 md:place-items-center">
      <div className="grid w-full max-w-sm gap-6">
        <Wordmark />
        <div className="grid gap-1.5">
          <h1 className="text-h2">{vi.activation.title}</h1>
          <p className="text-sm text-muted-foreground">{vi.activation.hint}</p>
        </div>
        {banner && (
          <Callout tone="danger" icon={CircleAlert} role="alert">
            {error}
          </Callout>
        )}
        <form onSubmit={(e) => void submit(e)} noValidate className="grid gap-5">
          <FormField label={vi.recovery.email} error={fields.email}>
            <Input type="email" inputMode="email" maxLength={LIMITS.email} autoComplete="username" autoFocus value={email} onChange={(e) => setEmail(e.target.value)} />
          </FormField>
          <FormField label={vi.recovery.code} error={fields.code}>
            <Input inputMode="numeric" autoComplete="one-time-code" maxLength={20} value={code} onChange={(e) => setCode(e.target.value)} />
          </FormField>
          <FormField label={vi.recovery.newPassword} error={fields.newPassword}>
            <Input type="password" maxLength={100} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </FormField>
          <FormField label={vi.recovery.repeatPassword} error={fields.repeat}>
            <Input type="password" maxLength={100} autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
          </FormField>
          <Button type="submit" size="lg" loading={pending} className="w-full">
            {!pending && <KeyRound aria-hidden />}
            {vi.activation.submit}
          </Button>
        </form>
        <p className="text-sm text-muted-foreground">{vi.activation.expired}</p>
        <Link to="/" className="text-sm text-primary underline-offset-4 hover:underline">
          {vi.recovery.backToSignIn}
        </Link>
      </div>
    </main>
  );
}
