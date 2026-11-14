import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert, KeyRound, Mail } from 'lucide-react';
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
 * Recovery of a forgotten password, open before signing in (PERM-058): the person gives an email, receives a code by
 * mail, then sends the code with a new password (BR-214 to BR-217). The page never shows a code and its answer to
 * the first step is the same for every address.
 */
export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<'email' | 'code'>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [wait, setWait] = useState(0);

  // The countdown of "Gửi lại mã": one tick a second until zero.
  useEffect(() => {
    if (wait <= 0) return;
    const timer = setTimeout(() => setWait((w) => w - 1), 1000);
    return () => clearTimeout(timer);
  }, [wait]);

  function refused(e: unknown) {
    if (e instanceof ApiError) {
      setError(e.message);
      setFields(e.fields);
      const retry = Number(e.fields.retryAfterSeconds);
      if (e.status === 429 && Number.isFinite(retry) && retry > 0) setWait(retry);
    } else {
      setError(vi.common.unexpectedError);
      setFields({});
    }
  }

  async function sendCode(e?: FormEvent) {
    e?.preventDefault();
    setError(null);
    setFields({});
    setPending(true);
    try {
      const answer = await api.recoveryRequest(email);
      setWait(answer.resendAfterSeconds);
      setStep('code');
    } catch (err) {
      refused(err);
    } finally {
      setPending(false);
    }
  }

  async function confirm(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const local: Record<string, string> = {};
    if (!code.trim()) local.code = 'Bắt buộc nhập.';
    if (password.length < 8) local.newPassword = vi.recovery.tooShort;
    else if (password !== repeat) local.repeat = vi.recovery.mismatch;
    setFields(local);
    if (Object.keys(local).length > 0) return;
    setPending(true);
    try {
      await api.recoveryConfirm({ email, code: code.trim(), newPassword: password });
      navigate('/', { replace: true, state: { passwordReset: true } });
    } catch (err) {
      refused(err);
    } finally {
      setPending(false);
    }
  }

  const banner = error && Object.keys(fields).filter((k) => k !== 'retryAfterSeconds').length === 0;

  return (
    <main id="main" className="grid min-h-dvh place-items-start justify-center px-5 py-10 md:place-items-center">
      <div className="grid w-full max-w-sm gap-6">
        <Wordmark />
        <div className="grid gap-1.5">
          <h1 className="text-h2">{vi.recovery.title}</h1>
          <p className="text-sm text-muted-foreground">{step === 'email' ? vi.recovery.hintEmail : vi.recovery.hintCode}</p>
        </div>

        {banner && (
          <Callout tone="danger" icon={CircleAlert} role="alert">
            {error}
          </Callout>
        )}

        {step === 'email' ? (
          <form onSubmit={(e) => void sendCode(e)} noValidate className="grid gap-5">
            <FormField label={vi.recovery.email} error={fields.email}>
              <Input
                type="email"
                inputMode="email"
                maxLength={LIMITS.email}
                autoComplete="username"
                autoFocus
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </FormField>
            <Button type="submit" size="lg" loading={pending} className="w-full">
              {!pending && <Mail aria-hidden />}
              {vi.recovery.sendCode}
            </Button>
          </form>
        ) : (
          <form onSubmit={(e) => void confirm(e)} noValidate className="grid gap-5">
            <Callout tone="info" role="status">
              {vi.recovery.sentNotice}
            </Callout>
            <FormField label={vi.recovery.code} error={fields.code}>
              <Input inputMode="numeric" autoComplete="one-time-code" maxLength={20} autoFocus value={code} onChange={(e) => setCode(e.target.value)} />
            </FormField>
            <FormField label={vi.recovery.newPassword} error={fields.newPassword}>
              <Input type="password" maxLength={100} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </FormField>
            <FormField label={vi.recovery.repeatPassword} error={fields.repeat}>
              <Input type="password" maxLength={100} autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
            </FormField>
            <Button type="submit" size="lg" loading={pending} className="w-full">
              {!pending && <KeyRound aria-hidden />}
              {vi.recovery.confirm}
            </Button>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <Button type="button" variant="outline" size="sm" disabled={wait > 0 || pending} onClick={() => void sendCode()}>
                {wait > 0 ? vi.recovery.resendIn(wait) : vi.recovery.resend}
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => {
                  setStep('email');
                  setError(null);
                  setFields({});
                  setCode('');
                }}
              >
                {vi.recovery.changeEmail}
              </Button>
            </div>
          </form>
        )}

        <Link to="/" className="text-sm text-primary underline-offset-4 hover:underline">
          {vi.recovery.backToSignIn}
        </Link>
      </div>
    </main>
  );
}
