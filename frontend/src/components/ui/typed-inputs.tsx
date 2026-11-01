import { forwardRef, lazy, Suspense, useEffect, useState, type InputHTMLAttributes } from 'react';
import * as PopoverPrimitive from '@radix-ui/react-popover';
import { CalendarDays, Clock } from 'lucide-react';
import { vi } from '@/i18n/vi';
import { formatDateText, parseDateText } from '@/lib/date-text';
import { cn } from '@/lib/utils';
import { DATE_LIMITS } from '@/lib/validate';
import { Input } from './input';

type Base = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'min' | 'max' | 'step' | 'value' | 'onChange'> & {
  value: string | number;
  onChange: (value: string) => void;
};

interface NumberInputProps extends Base {
  /** The rule's bounds travel with the control, so a number field cannot be written without them (input-validation). */
  min: number;
  max: number;
  /** Whole numbers only (step 1) unless `step` says otherwise. */
  integer?: boolean;
  step?: number | 'any';
  /** Shown after the value and exposed as `data-unit`: m, min, bpm, km/h, kg, VND… (DEC-026). */
  unit?: string;
  /** The label already names the unit, "Cự ly (m)": keep `data-unit` for tests and tools but draw no second one. */
  unitInLabel?: boolean;
}

/** A number field with its range, step and unit; the wheel never changes the value by accident. */
export const NumberInput = forwardRef<HTMLInputElement, NumberInputProps>(function NumberInput(
  { min, max, integer, step, unit, unitInLabel, value, onChange, onKeyDown, className, ...props },
  ref,
) {
  const input = (
    <Input
      ref={ref}
      type="number"
      inputMode={integer ? 'numeric' : 'decimal'}
      min={min}
      max={max}
      step={step ?? (integer ? 1 : 'any')}
      data-unit={unit}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onWheel={(e) => e.currentTarget.blur()}
      // The browser would let a number field hold "1e3" or "+5"; a whole-number field never takes those keys (BR-197).
      onKeyDown={(e) => {
        if (integer && ['e', 'E', '+', '.', ','].includes(e.key)) e.preventDefault();
        onKeyDown?.(e);
      }}
      className={cn(unit && !unitInLabel && 'pr-14', className)}
      {...props}
    />
  );
  if (!unit || unitInLabel) return input;
  return (
    <div className="relative">
      {input}
      <span aria-hidden className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-meta text-muted-foreground">
        {unit}
      </span>
    </div>
  );
});

interface DateInputProps extends Base {
  min?: string;
  max?: string;
}

const DateCalendar = lazy(() => import('./date-calendar'));

/**
 * A day field (BR-206): the day typed as dd/mm/yyyy (yyyy-mm-dd is read too), or chosen on a calendar with month and
 * year selectors and "Hôm nay". The value in and out stays `yyyy-MM-dd`. The bounds default to the plausible years,
 * and the page narrows them (not in the future, after the start…); a typed day outside them is passed on, so the
 * form's own check names it, and the field is marked and says which limit it breaks.
 */
export const DateInput = forwardRef<HTMLInputElement, DateInputProps>(function DateInput(
  { min = DATE_LIMITS.min, max = DATE_LIMITS.max, value, onChange, className, disabled, onBlur, ...props },
  ref,
) {
  const iso = String(value);
  const [text, setText] = useState(() => formatDateText(iso));
  const [open, setOpen] = useState(false);

  // The page changed the day (a reset, a loaded record): show it, unless it is what the field already reads.
  useEffect(() => {
    if ((parseDateText(text) ?? '') !== iso) setText(formatDateText(iso));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [iso]);

  const parsed = parseDateText(text);
  const limit = parsed && parsed < min ? vi.date.tooEarly(formatDateText(min)) : parsed && parsed > max ? vi.date.tooLate(formatDateText(max)) : null;
  const unreadable = text.trim() !== '' && parsed === null;
  const problem = unreadable ? (text.length >= 10 ? vi.date.notADay : null) : limit;

  const type = (next: string) => {
    setText(next);
    onChange(parseDateText(next) ?? '');
  };
  const pick = (day: string) => {
    setText(formatDateText(day));
    onChange(day);
    setOpen(false);
  };

  return (
    <PopoverPrimitive.Root open={open} onOpenChange={setOpen}>
      <PopoverPrimitive.Anchor asChild>
        <div className="relative">
          <Input
            ref={ref}
            type="text"
            inputMode="numeric"
            autoComplete="off"
            maxLength={10}
            data-min={min}
            data-max={max}
            placeholder={vi.date.placeholder}
            value={text}
            disabled={disabled}
            aria-invalid={problem ? true : props['aria-invalid']}
            onChange={(e) => type(e.target.value)}
            onBlur={(e) => {
              // A complete day is shown in the one format; a half-typed one stays as typed so it can be fixed.
              if (parsed) setText(formatDateText(parsed));
              onBlur?.(e);
            }}
            className={cn('pr-10', className)}
            {...props}
          />
          <PopoverPrimitive.Trigger asChild>
            <button
              type="button"
              disabled={disabled}
              aria-label={vi.date.open}
              className="absolute top-1/2 right-1 grid size-8 -translate-y-1/2 place-items-center rounded-sm text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-50"
            >
              <CalendarDays aria-hidden className="size-4" />
            </button>
          </PopoverPrimitive.Trigger>
          {problem && (
            <p role="alert" className="m-0 mt-1 text-meta text-destructive">
              {problem}
            </p>
          )}
        </div>
      </PopoverPrimitive.Anchor>
      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content align="start" sideOffset={4} className="z-50 rounded-lg border bg-popover p-3 text-popover-foreground shadow-pop">
          <Suspense fallback={<div className="h-72 w-72" />}>
            <DateCalendar value={parsed ?? ''} min={min} max={max} onPick={pick} />
          </Suspense>
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
});

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
const QUARTERS = Array.from({ length: 96 }, (_, i) => `${String(Math.floor(i / 4)).padStart(2, '0')}:${String((i % 4) * 15).padStart(2, '0')}`);

/** "0630" or "6:30" typed becomes "06:30" once it is complete; anything else stays as typed. */
function shapeTime(raw: string): string {
  const t = raw.replace(/[^\d:]/g, '');
  if (/^\d{4}$/.test(t)) return `${t.slice(0, 2)}:${t.slice(2)}`;
  if (/^\d:\d{2}$/.test(t)) return `0${t}`;
  return t;
}

/**
 * A time of day in 24-hour hh:mm (BR-206), typed or chosen from a list of quarter-hours; it does not depend on the
 * browser's 12/24-hour setting. The value is "HH:mm", or '' while the text is not a valid time.
 */
export const TimeInput = forwardRef<HTMLInputElement, Base>(function TimeInput(
  { value, onChange, className, disabled, onBlur, ...props },
  ref,
) {
  const saved = String(value);
  const [text, setText] = useState(saved);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if ((TIME.test(text) ? text : '') !== saved) setText(saved);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [saved]);

  const problem = text.length >= 5 && !TIME.test(text) ? vi.date.notATime : null;
  const set = (next: string) => {
    setText(next);
    onChange(TIME.test(next) ? next : '');
  };

  return (
    <PopoverPrimitive.Root open={open} onOpenChange={setOpen}>
      <PopoverPrimitive.Anchor asChild>
        <div className="relative">
          <Input
            ref={ref}
            type="text"
            inputMode="numeric"
            autoComplete="off"
            maxLength={5}
            placeholder={vi.date.timePlaceholder}
            value={text}
            disabled={disabled}
            aria-invalid={problem ? true : props['aria-invalid']}
            onChange={(e) => set(shapeTime(e.target.value))}
            onBlur={onBlur}
            className={cn('pr-10', className)}
            {...props}
          />
          <PopoverPrimitive.Trigger asChild>
            <button
              type="button"
              disabled={disabled}
              aria-label={vi.date.timeOpen}
              className="absolute top-1/2 right-1 grid size-8 -translate-y-1/2 place-items-center rounded-sm text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-50"
            >
              <Clock aria-hidden className="size-4" />
            </button>
          </PopoverPrimitive.Trigger>
          {problem && (
            <p role="alert" className="m-0 mt-1 text-meta text-destructive">
              {problem}
            </p>
          )}
        </div>
      </PopoverPrimitive.Anchor>
      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content align="start" sideOffset={4} className="z-50 w-32 rounded-lg border bg-popover p-1 text-popover-foreground shadow-pop">
          <ul role="listbox" aria-label={vi.date.timeOpen} className="m-0 max-h-60 list-none overflow-y-auto p-0">
            {QUARTERS.map((q) => (
              <li key={q} role="option" aria-selected={q === saved}>
                <button
                  type="button"
                  ref={(el) => {
                    if (el && q === (saved || '08:00') && open) el.scrollIntoView?.({ block: 'center' });
                  }}
                  onClick={() => {
                    set(q);
                    setOpen(false);
                  }}
                  className="tabular w-full rounded-sm px-3 py-1.5 text-left text-sm hover:bg-secondary aria-[current=true]:bg-secondary aria-[current=true]:font-semibold"
                  aria-current={q === saved}
                >
                  {q}
                </button>
              </li>
            ))}
          </ul>
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
});

interface DateTimeInputProps {
  /** `yyyy-MM-ddTHH:mm` club-local, or '' while the day or the time is missing. */
  value: string;
  onChange: (value: string) => void;
  min?: string;
  max?: string;
  dateLabel: string;
  timeLabel: string;
  className?: string;
}

/** A day with a time of day: the two fields side by side, in place of the browser's date-time control. */
export function DateTimeInput({ value, onChange, min, max, dateLabel, timeLabel, className }: DateTimeInputProps) {
  const [date, setDate] = useState(value.slice(0, 10));
  const [time, setTime] = useState(value.slice(11, 16));
  const emit = (d: string, t: string) => onChange(d && t ? `${d}T${t}` : '');
  return (
    <div className={cn('grid gap-2 sm:grid-cols-[1fr_9rem]', className)}>
      <DateInput aria-label={dateLabel} min={min} max={max} value={date} onChange={(d) => { setDate(d); emit(d, time); }} />
      <TimeInput aria-label={timeLabel} value={time} onChange={(t) => { setTime(t); emit(date, t); }} />
    </div>
  );
}
