import { useState, type FormEvent } from 'react';
import { CircleAlert } from 'lucide-react';
import { ApiError } from '../api/client';
import { api } from '../api/api';
import { PEDIGREE_POSITIONS, type Ancestor, type AncestorInput, type PedigreePosition } from '../api/types';
import { vi } from '../i18n/vi';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Select } from './ui/select';
import { SearchSelect, type Chosen } from './ui/search-select';
import { Callout } from './ui/states';

type Choice = { kind: 'unknown' } | { kind: 'horse'; horse: Chosen | null } | { kind: 'name'; name: string };

function toChoice(a: Ancestor | undefined): Choice {
  if (a?.horse) return { kind: 'horse', horse: { id: a.horse.id, label: a.horse.name } };
  if (a?.name) return { kind: 'name', name: a.name };
  return { kind: 'unknown' };
}

/** Six positions; each is a horse in the system, a name only, or unknown (BR-004). Integrity errors from BR-005. */
export default function PedigreeEditor({
  pedigree,
  horseId,
  onSave,
  onCancel,
}: {
  pedigree: Ancestor[];
  /** The horse whose pedigree is edited: it cannot be its own ancestor (BR-005). */
  horseId: number;
  onSave: (ancestors: AncestorInput[]) => Promise<void>;
  onCancel: () => void;
}) {
  const [choices, setChoices] = useState<Record<PedigreePosition, Choice>>(() => {
    const byPosition = Object.fromEntries(pedigree.map((a) => [a.position, a]));
    return Object.fromEntries(PEDIGREE_POSITIONS.map((p) => [p, toChoice(byPosition[p])])) as Record<
      PedigreePosition,
      Choice
    >;
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const set = (p: PedigreePosition, c: Choice) => setChoices({ ...choices, [p]: c });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setErrors({});
    setMessage(null);
    setPending(true);
    const ancestors: AncestorInput[] = [];
    for (const p of PEDIGREE_POSITIONS) {
      const c = choices[p];
      if (c.kind === 'horse' && c.horse) ancestors.push({ position: p, horseId: c.horse.id });
      if (c.kind === 'name' && c.name.trim()) ancestors.push({ position: p, name: c.name.trim() });
    }
    try {
      await onSave(ancestors);
    } catch (err) {
      if (err instanceof ApiError) {
        setMessage(err.message);
        setErrors(err.fields);
      } else {
        setMessage(vi.common.unexpectedError);
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} aria-label={vi.pedigree.edit} className="grid gap-5">
      {message && (
        <Callout tone="danger" icon={CircleAlert} role="alert">
          {message}
        </Callout>
      )}
      <div className="grid gap-5 md:grid-cols-2">
        {PEDIGREE_POSITIONS.map((p) => {
          const c = choices[p];
          const error = errors[`ancestors.${p}`];
          const errorId = error ? `pedigree-${p}-error` : undefined;
          return (
            <fieldset key={p} className="grid content-start gap-2 border-0 p-0">
              <legend className="mb-2 text-sm font-medium">{vi.pedigree.positions[p]}</legend>
              <Select
                aria-label={`${vi.pedigree.positions[p]} kind`}
                aria-describedby={errorId}
                aria-invalid={error ? true : undefined}
                value={c.kind}
                onChange={(e) => {
                  const kind = e.target.value as Choice['kind'];
                  set(p, kind === 'horse' ? { kind, horse: null } : kind === 'name' ? { kind, name: '' } : { kind });
                }}
              >
                <option value="unknown">{vi.pedigree.unknown}</option>
                <option value="horse">{vi.pedigree.inSystem}</option>
                <option value="name">{vi.pedigree.nameOnly}</option>
              </Select>
              {c.kind === 'horse' && (
                <SearchSelect
                  aria-label={`${vi.pedigree.positions[p]} horse`}
                  aria-describedby={errorId}
                  aria-invalid={error ? true : undefined}
                  value={c.horse}
                  placeholder={vi.pedigree.chooseHorse}
                  load={(q) => api.options('horses', { q }).then((page) => ({ ...page, items: page.items.filter((o) => o.id !== horseId) }))}
                  onChange={(horse) => set(p, { kind: 'horse', horse })}
                />
              )}
              {c.kind === 'name' && (
                <Input
                  aria-label={`${vi.pedigree.positions[p]} name`}
                  aria-describedby={errorId}
                  value={c.name}
                  onChange={(e) => set(p, { kind: 'name', name: e.target.value })}
                />
              )}
              {error && (
                <p id={errorId} className="flex items-start gap-1.5 text-meta text-status-injured-ink">
                  <CircleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0" />
                  <span>{error}</span>
                </p>
              )}
            </fieldset>
          );
        })}
      </div>
      <div className="flex flex-col-reverse gap-2 border-t pt-5 sm:flex-row sm:justify-end">
        <Button variant="outline" onClick={onCancel}>
          {vi.common.cancel}
        </Button>
        <Button type="submit" loading={pending}>
          {vi.common.save}
        </Button>
      </div>
    </form>
  );
}
