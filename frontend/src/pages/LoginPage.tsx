import { lazy, Suspense, useState, type FormEvent } from 'react';
import { CircleAlert, Clock, LogIn } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { vi } from '../i18n/vi';
import BlurText from '../components/effects/BlurText';
import { Wordmark } from '../components/layout/Wordmark';
import { Button } from '../components/ui/button';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { Callout } from '../components/ui/states';

// WebGL lives in its own chunk and is skipped entirely when motion is reduced or WebGL2 is missing.
const LoginBackdrop = lazy(() => import('../components/layout/LoginBackdrop'));

const canAnimate = () =>
  typeof window !== 'undefined' &&
  !(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true) &&
  Boolean(document.createElement('canvas').getContext?.('webgl2'));

export default function LoginPage() {
  const { login, endedReason, clearEndedReason } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [animate] = useState(canAnimate);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      await login(username, password);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : vi.common.unexpectedError);
    } finally {
      setPending(false);
    }
  }

  return (
    <main id="main" className="grid min-h-dvh lg:grid-cols-[minmax(0,1.15fr)_minmax(26rem,0.85fr)] [@media(min-width:640px)_and_(max-height:500px)]:grid-cols-2">
      <section className="relative isolate flex flex-col justify-between gap-10 overflow-hidden border-b bg-secondary/50 px-5 py-6 md:px-10 md:py-10 lg:border-r lg:border-b-0 lg:px-14 lg:py-12 [@media(min-width:640px)_and_(max-height:500px)]:gap-4 [@media(min-width:640px)_and_(max-height:500px)]:border-r [@media(min-width:640px)_and_(max-height:500px)]:border-b-0 [@media(min-width:640px)_and_(max-height:500px)]:py-5">
        <div aria-hidden className="absolute inset-0 -z-10">
          {animate && (
            <Suspense fallback={null}>
              <LoginBackdrop />
            </Suspense>
          )}
        </div>
        <Wordmark />
        <div className="grid max-w-[34rem] gap-4 pb-2 lg:pb-10">
          <h1 className="text-balance text-[2.25rem] leading-[1.08] font-medium md:text-[3rem] xl:text-[3.75rem] [@media(max-height:500px)]:text-[1.75rem]">
            <BlurText text={vi.appTitle} trigger="load" delay={90} stepDuration={0.3} distance={14} blur={8} />
          </h1>
          <p className="max-w-[44ch] text-base text-pretty text-muted-foreground md:text-lg [@media(max-height:500px)]:hidden">{vi.auth.tagline}</p>
        </div>
      </section>

      <section className="flex items-start justify-center px-5 py-10 md:items-center md:px-10 [@media(max-height:500px)]:items-center [@media(max-height:500px)]:py-4">
        <form onSubmit={submit} noValidate className="grid w-full max-w-sm gap-5">
          <div className="grid gap-1.5">
            <h2 className="text-h2">{vi.auth.formTitle}</h2>
            <p className="text-sm text-muted-foreground">{vi.auth.formHint}</p>
          </div>
          <FormField label={vi.auth.username}>
            <Input value={username} onChange={(e) => {
                clearEndedReason();
                setUsername(e.target.value);
              }} autoComplete="username" autoFocus />
          </FormField>
          <FormField label={vi.auth.password}>
            <Input
              type="password"
              value={password}
              onChange={(e) => {
                clearEndedReason();
                setPassword(e.target.value);
              }}
              autoComplete="current-password"
            />
          </FormField>
          {endedReason && (
            <Callout tone="warning" icon={Clock} role="status">
              {endedReason}
            </Callout>
          )}
          {error && (
            <Callout tone="danger" icon={CircleAlert} role="alert">
              {error}
            </Callout>
          )}
          <Button type="submit" size="lg" loading={pending} className="w-full">
            {!pending && <LogIn aria-hidden />}
            {vi.auth.signIn}
          </Button>
        </form>
      </section>
    </main>
  );
}
