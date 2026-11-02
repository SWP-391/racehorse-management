import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarOff, ChevronLeft, ChevronRight } from 'lucide-react';
import { api } from '../api/api';
import type { TimetableDay, TimetableItem } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/ui/button';
import { DataView } from '../components/ui/data-view';
import { Badge, PageHeader } from '../components/ui/display';
import { EmptyState } from '../components/ui/states';
import { FilterTabs } from '../components/ui/tabs';
import { DateInput } from '../components/ui/typed-inputs';
import { useLoad } from '../hooks/useLoad';
import { hhmm } from '../i18n/time';
import { vi } from '../i18n/vi';
import { clubDate } from '../lib/utils';

type Mode = 'day' | 'week' | 'month';

const DAY_MS = 864e5;
const iso = (d: Date) => d.toISOString().slice(0, 10);
const parse = (s: string) => new Date(`${s}T00:00:00Z`);
const addDays = (s: string, n: number) => iso(new Date(parse(s).getTime() + n * DAY_MS));
/** Monday of the week of a day (the club week starts on Monday). */
const mondayOf = (s: string) => addDays(s, -((parse(s).getUTCDay() + 6) % 7));
const firstOfMonth = (s: string) => `${s.slice(0, 7)}-01`;
const lastOfMonth = (s: string) => iso(new Date(Date.UTC(Number(s.slice(0, 4)), Number(s.slice(5, 7)), 0)));
/** The same day one month on (or back), kept inside the shorter month: 31 Jan + 1 month = 28 Feb. */
const addMonths = (s: string, n: number) => {
  const d = parse(firstOfMonth(s));
  d.setUTCMonth(d.getUTCMonth() + n);
  const last = Number(lastOfMonth(iso(d)).slice(8));
  return `${iso(d).slice(0, 8)}${String(Math.min(Number(s.slice(8)), last)).padStart(2, '0')}`;
};
const dayLabel = (s: string) => {
  const d = parse(s);
  const [y, m, dd] = s.split('-');
  return `${vi.timetable.weekdays[d.getUTCDay()]}, ${dd}/${m}/${y}`;
};

function range(item: TimetableItem): string {
  if (!item.startTime) return vi.timetable.noTime;
  const end = item.plannedEnd ? `${hhmm(item.plannedEnd)}${item.endsNextDay ? ` (${vi.timetable.nextDay})` : ''}` : '';
  return end ? `${hhmm(item.startTime)} – ${end}` : hhmm(item.startTime);
}

function Row({ item, trainer }: { item: TimetableItem; trainer: boolean }) {
  const cancelled = item.state === 'CANCELLED';
  return (
    <li className="grid gap-1 border-b px-1 py-3 last:border-b-0 sm:grid-cols-[11rem_minmax(0,1fr)_auto] sm:items-center sm:gap-4">
      <span className={`tabular font-medium ${cancelled ? 'text-muted-foreground line-through' : ''}`}>{range(item)}</span>
      <span className="min-w-0">
        {/* A Head Trainer opens the session; a Groom confirms or declines on the assigned-sessions page (D7). */}
        <Link to={trainer ? `/sessions/${item.id}` : '/my-sessions'} className="font-medium text-primary hover:underline">
          {item.horseName}
        </Link>
        <span className="text-muted-foreground">
          {' · '}
          {vi.training.types[item.type] ?? item.type}
          {trainer && item.groomName ? ` · ${item.groomName}` : ''}
          {trainer && !item.groomName ? ` · ${vi.timetable.noGroom}` : ''}
        </span>
      </span>
      <Badge tone={cancelled ? 'neutral' : 'outline'}>{vi.training.sessionStates[item.state] ?? item.state}</Badge>
    </li>
  );
}

function Day({ day, today, trainer }: { day: TimetableDay; today: boolean; trainer: boolean }) {
  return (
    <section aria-labelledby={`day-${day.date}`} className={`rounded-lg border p-4 ${today ? 'border-primary/50 bg-primary/5' : ''}`}>
      <header className="mb-2 flex flex-wrap items-baseline gap-x-4 gap-y-1">
        <h2 id={`day-${day.date}`} className="text-h2">
          {dayLabel(day.date)}
          {today && <span className="ml-2 text-meta font-normal text-muted-foreground">({vi.timetable.today})</span>}
        </h2>
        {day.barnHours.length > 0 && (
          <span className="tabular text-sm text-muted-foreground">
            {vi.timetable.barnHours}: {day.barnHours.map((r) => `${r.start}–${r.end}`).join(', ')}
          </span>
        )}
        {day.leave && (
          <Badge tone="watch">
            {vi.timetable.leave}
            {day.leave.late ? ` (${vi.timetable.lateLeave})` : ''}
          </Badge>
        )}
      </header>
      {day.items.length === 0 ? (
        <p className="py-2 text-sm text-muted-foreground">{vi.timetable.emptyDay}</p>
      ) : (
        <ul className="m-0 list-none p-0">
          {day.items.map((i) => (
            <Row key={i.id} item={i} trainer={trainer} />
          ))}
        </ul>
      )}
    </section>
  );
}

/** The month as weeks from Monday; each day shows how many sessions it holds and opens that day (BR-200). */
function MonthGrid({ days, today, onOpen }: { days: TimetableDay[]; today: string; onOpen: (date: string) => void }) {
  const lead = days.length === 0 ? 0 : (parse(days[0].date).getUTCDay() + 6) % 7;
  const cells: (TimetableDay | null)[] = [...Array<null>(lead).fill(null), ...days];
  return (
    <div role="group" aria-label={vi.timetable.monthGrid} className="grid grid-cols-7 gap-1 sm:gap-2">
      {vi.timetable.weekdaysShort.map((w) => (
        <span key={w} className="pb-1 text-center text-meta font-semibold text-muted-foreground">
          {w}
        </span>
      ))}
      {cells.map((d, i) => {
        if (!d) return <span key={`blank-${i}`} aria-hidden />;
        const count = d.items.filter((it) => it.state !== 'CANCELLED').length;
        return (
          <button
            key={d.date}
            type="button"
            onClick={() => onOpen(d.date)}
            aria-label={vi.timetable.dayCount(dayLabel(d.date), count)}
            className={`grid min-h-14 content-start gap-1 rounded-md border p-1.5 text-left hover:bg-secondary focus-visible:outline-2 focus-visible:outline-ring sm:min-h-20 sm:p-2 ${
              d.date === today ? 'border-primary/60 bg-primary/5' : ''
            }`}
          >
            <span className={`tabular text-sm ${d.date === today ? 'font-bold text-primary' : 'font-medium'}`}>{Number(d.date.slice(8))}</span>
            {count > 0 && <Badge tone="outline">{count}</Badge>}
            {d.leave && <span className="text-meta text-status-watch-ink">{vi.timetable.leave}</span>}
          </button>
        );
      })}
    </div>
  );
}

/**
 * "Thời gian biểu": the read-only day or week of the signed-in Groom (barn hours, leave, own sessions) or Head Trainer
 * (sessions of the horses assigned to them), in time order (BR-200 to BR-202). A list, not a grid, so it reads at 320 px.
 */
export default function TimetablePage() {
  const { me } = useAuth();
  const trainer = me?.role === 'HEAD_TRAINER';
  const todayIso = clubDate(new Date().toISOString());
  const [mode, setMode] = useState<Mode>('day');
  const [anchor, setAnchor] = useState(todayIso);
  const from = mode === 'day' ? anchor : mode === 'week' ? mondayOf(anchor) : firstOfMonth(anchor);
  const to = mode === 'day' ? anchor : mode === 'week' ? addDays(from, 6) : lastOfMonth(anchor);
  const load = useLoad(() => api.myTimetable(from, to), [from, to]);
  const move = (dir: 1 | -1) => setAnchor(mode === 'month' ? addMonths(anchor, dir) : addDays(anchor, dir * (mode === 'day' ? 1 : 7)));

  return (
    <>
      <PageHeader title={vi.timetable.title} description={trainer ? vi.timetable.descriptionTrainer : vi.timetable.descriptionGroom} />
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <FilterTabs
          label={vi.timetable.view}
          value={mode}
          onChange={setMode}
          options={[
            { value: 'day', label: vi.timetable.dayTab },
            { value: 'week', label: vi.timetable.weekTab },
            { value: 'month', label: vi.timetable.monthTab },
          ]}
          className="border-b-0"
        />
        <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
          <div className="w-44">
            <DateInput aria-label={vi.timetable.pickDay} value={anchor} onChange={(v) => v && setAnchor(v)} />
          </div>
          <Button variant="outline" size="sm" onClick={() => move(-1)} aria-label={vi.timetable.previous}>
            <ChevronLeft aria-hidden />
            {vi.timetable.previous}
          </Button>
          <Button variant="outline" size="sm" onClick={() => setAnchor(todayIso)}>
            {vi.timetable.today}
          </Button>
          <Button variant="outline" size="sm" onClick={() => move(1)} aria-label={vi.timetable.next}>
            {vi.timetable.next}
            <ChevronRight aria-hidden />
          </Button>
        </div>
      </div>
      <p className="tabular mb-4 text-sm text-muted-foreground" aria-live="polite">
        {mode === 'month' ? vi.timetable.monthOf(anchor.slice(5, 7), anchor.slice(0, 4)) : from === to ? dayLabel(from) : `${dayLabel(from)} – ${dayLabel(to)}`}
      </p>
      <DataView
        load={load}
        isEmpty={(t) => mode === 'day' && t.days.every((d) => d.items.length === 0 && d.barnHours.length === 0 && !d.leave)}
        empty={<EmptyState icon={CalendarOff} title={vi.timetable.emptyDay} text={vi.timetable.emptyHint} />}
      >
        {(t) =>
          mode === 'month' ? (
            <MonthGrid
              days={t.days}
              today={todayIso}
              onOpen={(date) => {
                setAnchor(date);
                setMode('day');
              }}
            />
          ) : (
          <div className="grid gap-4">
            {t.days.map((d) => (
              <Day key={d.date} day={d} today={d.date === todayIso} trainer={trainer} />
            ))}
          </div>
          )
        }
      </DataView>
    </>
  );
}
