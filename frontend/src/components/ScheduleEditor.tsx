import { Plus, Trash2 } from 'lucide-react';
import type { ScheduleRange } from '../api/types';
import { vi } from '../i18n/vi';
import { Button } from './ui/button';
import { Input } from './ui/input';

interface ScheduleEditorProps {
  value: ScheduleRange[];
  onChange: (ranges: ScheduleRange[]) => void;
  /** Field messages from the service, keyed like `ranges[2]` or `ranges` (BR-166). */
  errors?: Record<string, string>;
}

/** An end of 00:00 means the end of the day: the service reads it as 24:00 (a time input has no 24:00). */
export function toServiceRanges(ranges: ScheduleRange[]): ScheduleRange[] {
  return ranges.map((r) => ({ ...r, end: r.end === '00:00' ? '24:00' : r.end }));
}

export function fromServiceRanges(ranges: ScheduleRange[]): ScheduleRange[] {
  return ranges.map((r) => ({ ...r, end: r.end === '24:00' ? '00:00' : r.end }));
}

const DEFAULT_RANGE = { start: '05:00', end: '12:00' };

/**
 * The fixed weekly barn hours of a Groom: for each weekday off or one or more ranges (BR-166). The same every week;
 * the weekday order is Monday to Sunday (ISO 1 to 7).
 */
export function ScheduleEditor({ value, onChange, errors = {} }: ScheduleEditorProps) {
  const indexed = value.map((r, index) => ({ r, index }));

  function patch(index: number, change: Partial<ScheduleRange>) {
    onChange(value.map((r, i) => (i === index ? { ...r, ...change } : r)));
  }

  function copyMonday() {
    const monday = value.filter((r) => r.weekday === 1);
    const others = value.filter((r) => r.weekday === 1 || r.weekday > 5);
    onChange([...others, ...[2, 3, 4, 5].flatMap((weekday) => monday.map((r) => ({ ...r, weekday })))]);
  }

  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-meta text-muted-foreground">{vi.schedule.endNote}</p>
        <Button type="button" variant="ghost" size="sm" onClick={copyMonday} disabled={!value.some((r) => r.weekday === 1)}>
          {vi.schedule.copyWeek}
        </Button>
      </div>
      {errors.ranges && (
        <p role="alert" className="text-meta text-status-injured-ink">
          {errors.ranges}
        </p>
      )}
      <ul className="grid gap-2">
        {vi.schedule.weekdays.map((day, d) => {
          const weekday = d + 1;
          const mine = indexed.filter(({ r }) => r.weekday === weekday);
          return (
            <li key={weekday} className="grid gap-2 rounded-md border px-3 py-2 sm:grid-cols-[6rem_1fr_auto] sm:items-start">
              <span className="pt-2 text-sm font-medium">{day}</span>
              <div className="grid gap-2">
                {mine.length === 0 && <span className="pt-2 text-meta text-muted-foreground">{vi.schedule.off}</span>}
                {mine.map(({ r, index }) => (
                  <div key={index} className="grid gap-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Input
                        type="time"
                        aria-label={vi.schedule.startFor(day)}
                        className="w-32"
                        value={r.start}
                        onChange={(e) => patch(index, { start: e.target.value })}
                      />
                      <span aria-hidden>–</span>
                      <Input
                        type="time"
                        aria-label={vi.schedule.endFor(day)}
                        className="w-32"
                        value={r.end}
                        onChange={(e) => patch(index, { end: e.target.value })}
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        aria-label={vi.schedule.removeRange(day)}
                        onClick={() => onChange(value.filter((_, i) => i !== index))}
                      >
                        <Trash2 aria-hidden />
                      </Button>
                    </div>
                    {errors[`ranges[${index}]`] && (
                      <p role="alert" className="text-meta text-status-injured-ink">
                        {errors[`ranges[${index}]`]}
                      </p>
                    )}
                  </div>
                ))}
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                aria-label={vi.schedule.addRangeFor(day)}
                onClick={() => onChange([...value, { weekday, ...DEFAULT_RANGE }])}
              >
                <Plus aria-hidden /> {vi.schedule.addRange}
              </Button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
