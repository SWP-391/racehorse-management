import { DayPicker } from 'react-day-picker';
import { vi as viLocale } from 'react-day-picker/locale';
import 'react-day-picker/style.css';
import { vi } from '@/i18n/vi';
import { dateToIso, isoToDate } from '@/lib/date-text';
import { today } from '@/lib/validate';
import { Button } from './button';

interface Props {
  value: string;
  min: string;
  max: string;
  onPick: (iso: string) => void;
}

/** The calendar of a day field (loaded on first open): month and year selectors, "Hôm nay", days outside the limits off. */
export default function DateCalendar({ value, min, max, onPick }: Props) {
  const selected = isoToDate(value);
  const lo = isoToDate(min);
  const hi = isoToDate(max);
  const now = today();
  const todayOk = now >= min && now <= max;
  return (
    <div className="grid gap-2">
      <DayPicker
        mode="single"
        locale={viLocale}
        weekStartsOn={1}
        captionLayout="dropdown"
        startMonth={lo}
        endMonth={hi}
        defaultMonth={selected ?? isoToDate(todayOk ? now : min)}
        selected={selected}
        onSelect={(d) => d && onPick(dateToIso(d))}
        disabled={[...(lo ? [{ before: lo }] : []), ...(hi ? [{ after: hi }] : [])]}
        autoFocus
      />
      <div className="flex justify-end border-t pt-2">
        <Button type="button" variant="outline" size="sm" disabled={!todayOk} onClick={() => onPick(now)}>
          {vi.date.today}
        </Button>
      </div>
    </div>
  );
}
