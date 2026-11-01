import { useState, type FormEvent } from 'react';
import { CircleAlert } from 'lucide-react';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { Horse } from '../api/types';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Badge, DefinitionList, Section } from './ui/display';
import { FormField } from './ui/form-field';
import { DateInput } from './ui/typed-inputs';
import { collect, date, focusFirstInvalid, required, today } from '../lib/validate';
import { SearchSelect, type Chosen } from './ui/search-select';
import { Callout } from './ui/states';

type Mode = 'change' | 'lend' | null;

/** The Club Manager's view of who trains a horse, with hand-over and loan (PERM-045, BR-155, BR-156). */
export default function HorseTrainerSection({ horse, onChanged }: { horse: Horse; onChanged: (horse: Horse) => void }) {
  const [mode, setMode] = useState<Mode>(null);
  const [trainer, setTrainer] = useState<Chosen | null>(null);
  const [until, setUntil] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  function open(next: Mode) {
    setMode(next);
    setTrainer(null);
    setUntil('');
    setError(null);
    setFieldErrors({});
  }

  async function run(action: () => Promise<Horse>, close: boolean) {
    setError(null);
    setFieldErrors({});
    setPending(true);
    try {
      onChanged(await action());
      notify.success(vi.common.saved);
      if (close) setMode(null);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
        setFieldErrors(err.fields);
      } else {
        notify.error(err);
      }
    } finally {
      setPending(false);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    // The end of a loan is a day after today (BR-156), checked here before the service is asked.
    const refused = collect({
      trainerId: trainer ? null : vi.horses.required,
      ...(mode === 'lend' ? { until: required(until) ?? date(until, { min: today(1) }) } : {}),
    });
    setFieldErrors(refused);
    if (!trainer || Object.keys(refused).length > 0) {
      focusFirstInvalid(e.currentTarget as HTMLFormElement);
      return;
    }
    void run(
      () => (mode === 'lend' ? api.lendTrainer(horse.id, trainer.id, until) : api.changeTrainer(horse.id, trainer.id)),
      true,
    );
  }

  const main = horse.mainTrainer;
  const loan = horse.temporaryTrainer;
  return (
    <Section title={vi.assign.trainer} id="trainer-title">
      <div className="grid max-w-xl gap-4">
        <DefinitionList
          className="grid-cols-1 sm:grid-cols-2"
          items={[
            {
              term: vi.assign.mainTrainer,
              value: main ? (
                <span>
                  {main.displayName}
                  {!main.active && (
                    <Badge tone="outline" className="ml-2">
                      {vi.assign.inactive}
                    </Badge>
                  )}
                </span>
              ) : (
                vi.assign.noTrainer
              ),
            },
            {
              term: vi.assign.temporaryTrainer,
              value: loan ? `${loan.displayName} ${vi.assign.temporaryUntil(loan.until)}` : vi.common.none,
            },
          ]}
        />
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => open('change')}>
            {vi.assign.change}
          </Button>
          <Button variant="outline" onClick={() => open('lend')}>
            {vi.assign.lend}
          </Button>
          {loan && (
            <Button variant="outline" loading={pending} onClick={() => void run(() => api.endLoan(horse.id), false)}>
              {vi.assign.endLoan}
            </Button>
          )}
        </div>
        {error && mode === null && (
          <Callout tone="danger" icon={CircleAlert} role="alert">
            {error}
          </Callout>
        )}
      </div>

      <Dialog open={mode !== null} onOpenChange={(isOpen) => !isOpen && setMode(null)}>
        <DialogContent>
          <form className="grid gap-4" noValidate onSubmit={submit}>
            <DialogHeader>
              <DialogTitle>{mode === 'lend' ? vi.assign.lendTitle : vi.assign.changeTitle}</DialogTitle>
              <DialogDescription>{mode === 'lend' ? vi.assign.lendDescription : vi.assign.changeDescription}</DialogDescription>
            </DialogHeader>
            {error && Object.keys(fieldErrors).length === 0 && (
              <Callout tone="danger" icon={CircleAlert} role="alert">
                {error}
              </Callout>
            )}
            <FormField label={`${vi.assign.trainer} *`} error={fieldErrors.trainerId}>
              <SearchSelect
                value={trainer}
                placeholder={vi.assign.chooseTrainer}
                load={(q) => api.options('trainers', { q })}
                onChange={setTrainer}
              />
            </FormField>
            {mode === 'lend' && (
              <FormField label={`${vi.assign.until} *`} error={fieldErrors.until}>
                <DateInput min={today(1)} value={until} onChange={setUntil} />
              </FormField>
            )}
            <DialogFooter>
              <Button variant="outline" onClick={() => setMode(null)}>
                {vi.common.cancel}
              </Button>
              <Button type="submit" loading={pending}>
                {vi.common.save}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </Section>
  );
}
