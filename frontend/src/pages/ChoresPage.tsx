import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert, CircleCheck, CircleDashed, CircleX, ClipboardCheck, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import { CHORES, type Page, type ChoreCode, type ChoreHistoryEntry, type ChoreHorse, type ChoreRangeHorse, type ChoreState } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { PagerBar } from '../components/PagerBar';
import { HorseAvatar } from '../components/HorseAvatar';
import { useHorsePhotos } from '../hooks/useHorsePhotos';
import { Select } from '../components/ui/select';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { usePagedList, type PagedList } from '../hooks/usePagedList';
import { notify } from '../lib/notify';
import { Button } from '../components/ui/button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Badge, PageHeader } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { SearchSelect, type Chosen } from '../components/ui/search-select';
import { FilterTabs } from '../components/ui/tabs';
import { Callout, EmptyState } from '../components/ui/states';
import { clubDateTime, clubTime } from '../lib/utils';
import { useSearchParams } from 'react-router-dom';

type Cancelling = { entryId: number; chore: ChoreCode; horseName: string };

/** Every mark of one horse, cancelled ones with their reason (BR-126, AC-211); not for the Owner. */
function HistoryDialog({ horse, onClose }: { horse: ChoreHorse | null; onClose: () => void }) {
  const [historyPage, setHistoryPage] = useState(0);
  useEffect(() => setHistoryPage(0), [horse?.horseId]);
  const history = useLoad<Page<ChoreHistoryEntry>>(() => api.choreHistory(horse!.horseId, historyPage), [horse?.horseId, historyPage], horse !== null);
  return (
    <Dialog open={horse !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{horse && vi.chores.historyOf(horse.horseName)}</DialogTitle>
          <DialogDescription>{vi.chores.historyDescription}</DialogDescription>
        </DialogHeader>
        <DataView
          load={history}
          isEmpty={(p) => p.items.length === 0}
          empty={<p className="text-sm text-muted-foreground">{vi.chores.historyEmpty}</p>}
        >
          {(p) => (
            <>
            <ul className="grid max-h-[min(60dvh,28rem)] divide-y overflow-y-auto" aria-label={vi.chores.history}>
              {p.items.map((r) => (
                <li key={r.entryId} className="grid gap-1 py-2.5">
                  <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span className="tabular text-meta text-muted-foreground">{r.date}</span>
                    <span className="font-medium">{vi.chores.names[r.chore]}</span>
                    {r.cancelledAt === null ? (
                      <ChoreBadge done />
                    ) : (
                      <Badge tone="neutral" icon={<CircleX aria-hidden />}>
                        {vi.chores.cancelled}
                      </Badge>
                    )}
                  </span>
                  <span className="flex flex-wrap items-center gap-2 text-meta text-muted-foreground">
                    {vi.chores.doneBy(r.groomName, clubTime(r.markedAt))}
                    {r.onBehalf && (
                      <Badge tone="outline" title={vi.chores.onBehalfHint}>
                        {vi.chores.onBehalf}
                      </Badge>
                    )}
                    {r.backfilled && (
                      <Badge tone="outline" title={r.backfillReason ?? vi.chores.backfilledHint}>
                        {vi.chores.backfilled}
                      </Badge>
                    )}
                  </span>
                  {r.cancelledAt !== null && (
                    <span className="text-meta text-muted-foreground">
                      {vi.chores.cancelledBy(r.cancelledByName ?? vi.common.none, clubDateTime(r.cancelledAt), r.cancelReason ?? '')}
                    </span>
                  )}
                </li>
              ))}
            </ul>
            <PagerBar page={p.page} size={p.size} total={p.total} onPage={setHistoryPage} />
            </>
          )}
        </DataView>
      </DialogContent>
    </Dialog>
  );
}

/** Chore state as badge + icon + text, never colour alone. */
function ChoreBadge({ done }: { done: boolean }) {
  return done ? (
    <Badge tone="eligible" icon={<CircleCheck aria-hidden />}>
      {vi.chores.done}
    </Badge>
  ) : (
    <Badge tone="neutral" icon={<CircleDashed aria-hidden />}>
      {vi.chores.notDone}
    </Badge>
  );
}

/** The date `days` before an ISO date, computed in UTC so the club's own calendar day is never shifted. */
function daysBefore(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - days);
  return d.toISOString().slice(0, 10);
}

/** The longest range the service answers, both ends included (BR-188). */
const MAX_RANGE_DAYS = 31;
const daysBetween = (a: string, b: string) => Math.round((Date.parse(b) - Date.parse(a)) / 86_400_000);

/** "2026-10-24" -> "24/10". */
const dayMonth = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

/**
 * The chore history of a date range (BR-188): horses by day with how many of the four chores were done, who and when
 * in the day's tooltip. Read-only: marking stays on today's list. The range defaults to the last 7 days.
 */
function HistoryTab({ today, owner }: { today: string; owner: boolean }) {
  const photoOf = useHorsePhotos();
  const [horse, setHorse] = useState<Chosen | null>(null);
  const defaults = { from: daysBefore(today, 6), to: today };
  // The range is checked here, so a range the service would refuse is never sent and its message shows once.
  const [params] = useSearchParams();
  const rangeFrom = params.get('hfrom') || defaults.from;
  const rangeTo = params.get('hto') || defaults.to;
  const invalid = rangeTo < rangeFrom;
  const tooLong = !invalid && daysBetween(rangeFrom, rangeTo) + 1 > MAX_RANGE_DAYS;
  // The range shown is always the one sent, so the service never applies a default the screen does not show.
  const list: PagedList<Page<ChoreRangeHorse>> = usePagedList<Page<ChoreRangeHorse>>(
    (query) => api.choreRange({ ...query, from: list.filters.from || defaults.from, to: list.filters.to || defaults.to }),
    { filters: ['from', 'to'], prefix: 'h', fixed: { horseId: horse ? horse.id : '' }, enabled: !invalid && !tooLong },
  );
  const from = list.filters.from || defaults.from;
  const to = list.filters.to || defaults.to;
  const columns: DataColumn<ChoreRangeHorse>[] = [
    {
      id: 'horse',
      header: vi.chores.horse,
      primary: true,
      cell: (h) => (
        <span className="flex items-start gap-2">
          <HorseAvatar id={h.horseId} name={h.horseName} photoVersion={photoOf(h.horseId)} className="size-8 shrink-0 text-sm" />
          <span className="grid gap-0.5">
            <span className="font-display text-[1.0625rem] font-medium text-foreground">{h.horseName}</span>
            <span className="tabular text-meta text-muted-foreground">{h.registrationNumber}</span>
          </span>
        </span>
      ),
    },
    {
      id: 'days',
      header: vi.chores.days,
      hideLabelOnMobile: true,
      cell: (h) => (
        <ul aria-label={vi.chores.daysOf(h.horseName)} className="m-0 flex list-none flex-wrap gap-1.5 p-0">
          {h.days.map((d) => {
            const done = d.chores.filter((c) => c.done);
            const detail = d.chores
              .map((c) => `${vi.chores.names[c.chore]}: ${c.done ? (owner || !c.groomName ? vi.chores.done : vi.chores.doneBy(c.groomName, clubTime(c.markedAt))) : vi.chores.notDone}`)
              .join('\n');
            return (
              <li key={d.date} title={detail}>
                <Badge tone={done.length === d.chores.length ? 'eligible' : done.length === 0 ? 'neutral' : 'watch'}>
                  <span className="tabular">
                    {dayMonth(d.date)}: {done.length}/{d.chores.length}
                  </span>
                </Badge>
              </li>
            );
          })}
        </ul>
      ),
    },
  ];
  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <FormField label={vi.chores.from}>
          <Input type="date" value={from} max={to} onChange={(e) => list.setFilter('from', e.target.value)} className="sm:w-44" />
        </FormField>
        <FormField label={vi.chores.to} error={invalid ? vi.chores.rangeInvalid : tooLong ? vi.chores.rangeTooLong : null}>
          <Input type="date" value={to} min={from} max={today} onChange={(e) => list.setFilter('to', e.target.value)} className="sm:w-44" />
        </FormField>
        {!owner && (
          <FormField label={vi.chores.horse} className="min-w-[14rem]">
            <SearchSelect
              value={horse}
              clearable
              placeholder={vi.chores.allHorses}
              load={(q) => api.options('horses', { q })}
              onChange={setHorse}
            />
          </FormField>
        )}
      </div>
      <ListToolbar query={list.query} onQuery={list.setQuery} placeholder={vi.chores.searchPlaceholder} total={list.load.state.data?.total} />
      <DataView
        load={list.load}
        isEmpty={(data) => data.items.length === 0}
        empty={<EmptyState icon={ClipboardCheck} title={list.narrowed ? vi.common.noResults : vi.chores.empty} text={list.narrowed ? vi.common.noResultsHint : vi.chores.emptyHint} />}
      >
        {(data) => (
          <div className="grid gap-3">
            <DataTable caption={vi.chores.history} data={data.items} columns={columns} rowKey={(h) => h.horseId} />
            <ListFooter data={data} onPage={list.setPage} onSize={list.setSize} />
          </div>
        )}
      </DataView>
    </div>
  );
}

/**
 * Daily chores (BR-124 to BR-127): today by default, a date input for past days (read-only). Only a Groom marks and
 * cancels, only on today; nothing is invented on the client, every write is followed by a reload.
 */
export default function ChoresPage() {
  const { me, can } = useAuth();
  const photoOf = useHorsePhotos();
  const allowed = can('VIEW_CHORES');
  const mayMark = can('MARK_CHORES');
  const owner = !mayMark && me?.role === 'HORSE_OWNER';
  const [date, setDate] = useState('');
  const [tab, setTab] = useState<'today' | 'history'>('today');
  const [busy, setBusy] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<Cancelling | null>(null);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [historyOf, setHistoryOf] = useState<ChoreHorse | null>(null);
  // A refused mark stays on screen with the names of the short items (BR-173), not only in a toast.
  const [markError, setMarkError] = useState<string | null>(null);
  const [filling, setFilling] = useState(false);
  const [fill, setFill] = useState<{ horse: Chosen | null; chore: ChoreCode; date: string; reason: string }>({
    horse: null,
    chore: 'FEEDING',
    date: '',
    reason: '',
  });
  const [fillErrors, setFillErrors] = useState<Record<string, string>>({});
  const [fillError, setFillError] = useState<string | null>(null);

  const list = usePagedList((query) => api.chores(date || undefined, query), { prefix: 't', fixed: { date }, enabled: allowed, rows: (d) => d.horses.length });
  const load = list.load;
  // Today's meals beside "Cho ăn" (BR-144); an extra, so a failed load only hides them.
  const rations = useLoad(() => api.rationsToday(), [], allowed && can('VIEW_RATION'));
  const mealsByHorse = new Map((rations.state.data ?? []).map((r) => [r.horseId, r.meals]));
  const mealsShown = rations.state.status === 'ready' && date === '';

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  const day = load.state.data;
  const editable = mayMark && day !== null && day.date === day.today;
  const onToday = day !== null && day.date === day.today;
  // A Groom cancels only a mark they made; the Club Manager may cancel any mark of the day (BR-170).
  const mayCancel = (groomId: number | null) =>
    onToday && ((mayMark && groomId !== null && groomId === me?.id) || can('MANAGE_CHORE_MARKS'));

  async function mark(h: ChoreHorse, chore: ChoreCode) {
    if (!day) return;
    const key = `${h.horseId}:${chore}`;
    setBusy(key);
    setMarkError(null);
    try {
      await api.markChore(h.horseId, chore, day.date);
      notify.success(vi.common.saved);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'INSUFFICIENT_STOCK') setMarkError(e.message);
      else notify.error(e);
    } finally {
      await load.reload();
      setBusy(null);
    }
  }

  function closeFill() {
    setFilling(false);
    setFillErrors({});
    setFillError(null);
  }

  /** The Club Manager fills a past day with a mandatory reason; feeding takes that day's ration out of stock (BR-174). */
  async function submitFill(e: FormEvent) {
    e.preventDefault();
    const errors: Record<string, string> = {};
    if (!fill.horse) errors.horseId = vi.chores.fillHorseRequired;
    if (!fill.date) errors.date = vi.chores.fillDateRequired;
    if (fill.reason.trim() === '') errors.reason = vi.chores.fillReasonRequired;
    setFillErrors(errors);
    setFillError(null);
    if (Object.keys(errors).length > 0 || !fill.horse) return;
    setBusy('fill');
    try {
      await api.backfillChore({ horseId: fill.horse.id, chore: fill.chore, date: fill.date, reason: fill.reason.trim() });
      notify.success(vi.common.saved);
      closeFill();
      setFill({ horse: null, chore: 'FEEDING', date: '', reason: '' });
      await load.reload();
    } catch (err) {
      const fields = fieldsOf(err);
      setFillErrors(fields);
      if (Object.keys(fields).length === 0) setFillError(messageOf(err));
    } finally {
      setBusy(null);
    }
  }

  function closeCancel() {
    setCancelling(null);
    setReason('');
    setReasonError(null);
    setCancelError(null);
  }

  async function submitCancel(e: FormEvent) {
    e.preventDefault();
    if (!cancelling) return;
    if (reason.trim() === '') {
      setReasonError(vi.chores.reasonRequired);
      return;
    }
    setReasonError(null);
    setCancelError(null);
    setBusy('cancel');
    try {
      await api.cancelChore(cancelling.entryId, reason.trim());
      notify.success(vi.common.saved);
      closeCancel();
      await load.reload();
    } catch (err) {
      const fields = fieldsOf(err);
      if (fields.reason) setReasonError(fields.reason);
      else setCancelError(messageOf(err));
    } finally {
      setBusy(null);
    }
  }

  const find = (h: ChoreHorse, chore: ChoreCode): ChoreState =>
    h.chores.find((c) => c.chore === chore) ?? { chore, done: false, entryId: null, groomName: null, markedAt: null, groomId: null };

  const columns: DataColumn<ChoreHorse>[] = [
    {
      id: 'horse',
      header: vi.chores.horse,
      primary: true,
      cell: (h) => (
        <span className="flex items-start gap-2">
          <HorseAvatar id={h.horseId} name={h.horseName} photoVersion={photoOf(h.horseId)} className="size-8 shrink-0 text-sm" />
          <span className="grid gap-0.5">
          <span className="font-display text-[1.0625rem] font-medium text-foreground">{h.horseName}</span>
          <span className="text-meta text-muted-foreground">
            <span className="tabular">{h.registrationNumber}</span>
            {' · '}
            {h.stall ? vi.barn.stallFull(h.stall.area, h.stall.slot) : vi.chores.noStall}
          </span>
          {!owner && (
            <Button
              size="sm"
              variant="ghost"
              className="-ml-3 justify-self-start"
              aria-label={vi.chores.historyOf(h.horseName)}
              onClick={() => setHistoryOf(h)}
            >
              {vi.chores.history}
            </Button>
          )}
          </span>
        </span>
      ),
    },
    ...CHORES.map<DataColumn<ChoreHorse>>((code) => ({
      id: code,
      header: vi.chores.names[code],
      cell: (h) => {
        const c = find(h, code);
        const name = vi.chores.names[code];
        return (
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            {(c.done || !editable) && <ChoreBadge done={c.done} />}
            {c.done && c.groomName && (
              <span className="text-meta text-muted-foreground">{vi.chores.doneBy(c.groomName, clubTime(c.markedAt))}</span>
            )}
            {c.done && c.onBehalf && (
              <Badge tone="outline" title={vi.chores.onBehalfHint}>
                {vi.chores.onBehalf}
              </Badge>
            )}
            {c.done && c.backfilled && (
              <Badge tone="outline" title={vi.chores.backfilledHint}>
                {vi.chores.backfilled}
              </Badge>
            )}
            {editable && !c.done && (
              <Button
                size="sm"
                variant="outline"
                aria-label={vi.chores.markOf(name, h.horseName)}
                loading={busy === `${h.horseId}:${code}`}
                disabled={busy !== null}
                onClick={() => void mark(h, code)}
              >
                {vi.chores.mark}
              </Button>
            )}
            {c.done && c.entryId !== null && mayCancel(c.groomId) && (
              <Button
                size="sm"
                variant="ghost"
                className="-ml-3"
                aria-label={vi.chores.cancelMarkOf(name, h.horseName)}
                onClick={() => setCancelling({ entryId: c.entryId as number, chore: code, horseName: h.horseName })}
              >
                {vi.chores.cancelMark}
              </Button>
            )}
            {code === 'FEEDING' && mealsShown && (
              <span className="basis-full text-meta text-muted-foreground" aria-label={vi.ration.todayMeals}>
                {mealsByHorse.has(h.horseId)
                  ? (mealsByHorse.get(h.horseId) ?? [])
                      .map((m) => `${m.time} ${m.name}: ${m.items.map((i) => `${i.supplyItemName} ${i.quantity} ${i.unit}`).join(', ')}`)
                      .join(' · ')
                  : vi.ration.none}
              </span>
            )}
          </span>
        );
      },
    })),
  ];

  const back = day !== null && day.date !== day.today ? (
    <Button variant="outline" onClick={() => setDate('')}>
      {vi.chores.backToToday}
    </Button>
  ) : undefined;

  return (
    <>
      <PageHeader
        title={vi.chores.title}
        description={owner ? vi.chores.ownerDescription : mayMark ? vi.chores.description : vi.chores.readOnlyDescription}
      />
      <FilterTabs
        label={vi.chores.view}
        value={tab}
        onChange={setTab}
        className="mb-6"
        options={[
          { value: 'today', label: vi.chores.tabToday },
          { value: 'history', label: vi.chores.tabHistory },
        ]}
      />
      {tab === 'history' ? (
        <HistoryTab today={day?.today ?? new Date().toISOString().slice(0, 10)} owner={owner} />
      ) : (
      <>
      {!owner && (
        // The Owner sees today only on the day list (PERM-036): no date input. One filter row at every width.
        <div className="mb-6 flex flex-wrap items-end gap-3">
          <FormField label={vi.chores.date}>
            <Input type="date" value={day?.date ?? date} onChange={(e) => setDate(e.target.value)} className="sm:w-44" />
          </FormField>
          {back}
          {can('MANAGE_CHORE_MARKS') && (
            <Button variant="outline" onClick={() => setFilling(true)}>
              {vi.chores.fillPast}
            </Button>
          )}
        </div>
      )}
      {markError && (
        <Callout tone="danger" icon={CircleAlert} role="alert" className="mb-6">
          {markError}
        </Callout>
      )}
      {day !== null && day.date !== day.today && (
        <Callout tone="info" icon={CircleAlert} className="mb-6">
          {vi.chores.pastNote(day.date)}
        </Callout>
      )}
      <div className="mb-4">
        <ListToolbar query={list.query} onQuery={list.setQuery} placeholder={vi.chores.searchPlaceholder} total={load.state.data?.total} />
      </div>
      <DataView
        load={load}
        isEmpty={(d) => d.horses.length === 0}
        empty={
          list.narrowed ? (
            <EmptyState icon={ClipboardCheck} title={vi.common.noResults} text={vi.common.noResultsHint} />
          ) : (
            <EmptyState icon={ClipboardCheck} title={vi.chores.empty} text={vi.chores.emptyHint} />
          )
        }
      >
        {(d) => (
          <div className="grid gap-3">
            <DataTable caption={vi.chores.title} data={d.horses} columns={columns} rowKey={(h) => h.horseId} />
            <ListFooter data={{ items: d.horses, page: d.page, size: d.size, total: d.total }} onPage={list.setPage} onSize={list.setSize} />
          </div>
        )}
      </DataView>
      </>
      )}

      <HistoryDialog horse={historyOf} onClose={() => setHistoryOf(null)} />

      <Dialog open={filling} onOpenChange={(open) => !open && closeFill()}>
        <DialogContent>
          <form className="grid gap-4" noValidate onSubmit={(e) => void submitFill(e)}>
            <DialogHeader>
              <DialogTitle>{vi.chores.fillTitle}</DialogTitle>
              <DialogDescription>{vi.chores.fillDescription}</DialogDescription>
            </DialogHeader>
            {fillError && (
              <Callout tone="danger" icon={CircleAlert} role="alert">
                {fillError}
              </Callout>
            )}
            <FormField label={vi.chores.horse} error={fillErrors.horseId}>
              <SearchSelect
                value={fill.horse}
                placeholder={vi.incidents.chooseHorse}
                load={(q) => api.options('horses', { q })}
                onChange={(horse) => setFill({ ...fill, horse })}
              />
            </FormField>
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField label={vi.chores.fillChore}>
                <Select value={fill.chore} onChange={(e) => setFill({ ...fill, chore: e.target.value as ChoreCode })}>
                  {CHORES.map((c) => (
                    <option key={c} value={c}>
                      {vi.chores.names[c]}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField label={vi.chores.date} error={fillErrors.date}>
                <Input
                  type="date"
                  max={day ? daysBefore(day.today, 1) : undefined}
                  value={fill.date}
                  onChange={(e) => setFill({ ...fill, date: e.target.value })}
                />
              </FormField>
            </div>
            <FormField label={vi.chores.fillReason} error={fillErrors.reason}>
              <Input value={fill.reason} onChange={(e) => setFill({ ...fill, reason: e.target.value })} autoComplete="off" />
            </FormField>
            <DialogFooter>
              <Button variant="outline" onClick={closeFill}>
                {vi.common.cancel}
              </Button>
              <Button type="submit" loading={busy === 'fill'}>
                {vi.chores.fillSubmit}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={cancelling !== null} onOpenChange={(open) => !open && closeCancel()}>
        <DialogContent>
          <form className="grid gap-4" noValidate onSubmit={(e) => void submitCancel(e)}>
            <DialogHeader>
              <DialogTitle>{vi.chores.cancelTitle}</DialogTitle>
              <DialogDescription>
                {cancelling && vi.chores.cancelDescription(vi.chores.names[cancelling.chore], cancelling.horseName)}
              </DialogDescription>
            </DialogHeader>
            {cancelError && (
              <Callout tone="danger" icon={CircleAlert} role="alert">
                {cancelError}
              </Callout>
            )}
            <FormField label={vi.chores.reason} error={reasonError}>
              <Input value={reason} onChange={(e) => setReason(e.target.value)} autoComplete="off" />
            </FormField>
            <DialogFooter>
              <Button variant="outline" onClick={closeCancel}>
                {vi.chores.keep}
              </Button>
              <Button type="submit" variant="destructive" loading={busy === 'cancel'}>
                {vi.chores.confirmCancel}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
