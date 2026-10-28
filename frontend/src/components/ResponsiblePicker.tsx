import { useEffect, useState } from 'react';
import { api } from '../api/api';
import type { AssignableGrooms } from '../api/types';
import { vi } from '../i18n/vi';
import { FormField } from './ui/form-field';
import { SearchSelect, localOptions } from './ui/search-select';
import { Callout } from './ui/states';

interface Props {
  horseId: number;
  date: string;
  startTime: string;
  durationMin: number | '';
  /** The session being changed, so it does not collide with itself. */
  sessionId?: number;
  value: number | '';
  onChange: (id: number | '') => void;
  /** A draft may leave the field empty (BR-158). */
  optional?: boolean;
  label?: string;
  error?: string | null;
}

const complete = (date: string, startTime: string, durationMin: number | ''): boolean =>
  /^\d{4}-\d{2}-\d{2}$/.test(date) && /^\d{2}:\d{2}/.test(startTime) && durationMin !== '' && durationMin > 0;

/**
 * The responsible person of a session, chosen only among the Grooms who are free for its time range: outside their
 * barn hours, not on leave, no overlapping session (BR-161, BR-162). The service decides; the Grooms who cannot be
 * chosen are listed with the reason, and when nobody is free the earliest free time is shown.
 */
export default function ResponsiblePicker({ horseId, date, startTime, durationMin, sessionId, value, onChange, optional, label, error }: Props) {
  const [data, setData] = useState<AssignableGrooms | null>(null);
  const [failed, setFailed] = useState(false);
  const ready = complete(date, startTime, durationMin);

  useEffect(() => {
    if (!ready) {
      setData(null);
      return;
    }
    let stale = false;
    setFailed(false);
    api
      .assignableGrooms({ horseId, date, startTime: startTime.slice(0, 5), durationMin: Number(durationMin), sessionId })
      .then((result) => {
        if (stale) return;
        setData(result);
        // A choice made for another time may no longer be free (the form refetches when the time changes).
        if (value !== '' && !result.available.some((g) => g.id === value)) onChange('');
      })
      .catch(() => !stale && setFailed(true));
    return () => {
      stale = true;
    };
    // `value` and `onChange` only matter at the moment the answer arrives.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, horseId, date, startTime, durationMin, sessionId]);

  const text = label ?? vi.training.responsible;
  const picked = value === '' ? undefined : data?.available.find((g) => g.id === value);
  const chosen = picked ? { id: picked.id, label: picked.displayName } : null;
  return (
    <div className="grid min-w-0 content-start gap-2">
      <FormField label={text} error={error} description={optional ? vi.assign.optionalForDraft : undefined}>
        <SearchSelect
          value={chosen}
          disabled={!data}
          clearable={optional}
          placeholder={optional ? vi.assign.chooseLater : vi.training.chooseResponsible}
          load={localOptions((data?.available ?? []).map((g) => ({ id: g.id, label: g.displayName, hint: null })))}
          onChange={(c) => onChange(c ? c.id : '')}
        />
      </FormField>
      {!ready && <p className="text-meta text-muted-foreground">{vi.assign.needTimeFirst}</p>}
      {failed && <p className="text-meta text-status-injured-ink">{vi.common.unexpectedError}</p>}
      {data && data.available.length === 0 && (
        <Callout tone="warning" role="status">
          {vi.assign.noneFree} {data.nextFree && vi.assign.nextFree(data.nextFree)}
        </Callout>
      )}
      {data && data.unavailable.length > 0 && (
        <details className="text-meta text-muted-foreground">
          <summary className="cursor-pointer">
            {vi.assign.unavailableTitle} ({data.unavailable.length})
          </summary>
          <ul className="m-0 mt-1 grid list-none gap-0.5 p-0">
            {data.unavailable.map((u) => (
              <li key={u.id}>
                {u.displayName}: {vi.assign.unavailableReasons[u.reason]}
                {u.detail && <span> — {u.detail}</span>}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
