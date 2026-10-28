import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { CalendarCheck, CalendarClock, CircleAlert, CircleCheck, Clock, History, PencilLine, Plus, ShieldOff, SquareCheckBig, TriangleAlert } from 'lucide-react';
import { api } from '../api/api';
import { CARE_TYPES, type CareEvent, type CareItem, type CareType, type Medicine, type Page } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { HorseAvatar } from '../components/HorseAvatar';
import { useHorsePhotos } from '../hooks/useHorsePhotos';
import { vi } from '../i18n/vi';
import { usePagedList } from '../hooks/usePagedList';
import { notify } from '../lib/notify';
import { Button } from '../components/ui/button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Badge, PageHeader } from '../components/ui/display';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { Select } from '../components/ui/select';
import { SearchSelect, type Chosen } from '../components/ui/search-select';
import { NoMatches, PanelSearch } from '../components/PanelSearch';
import { StaggerList } from '../components/ui/stagger-list';
import { Callout, EmptyState } from '../components/ui/states';

type DoneForm = {
  id: number;
  type: CareType | '';
  dateDone: string;
  performer: string;
  product: string;
  batch: string;
  note: string;
  /** The medicine taken from stock and the dose, for a vaccination or deworming (BR-172). */
  supplyItemId: number | '';
  quantity: string;
};
const NO_DONE: DoneForm = { id: 0, type: '', dateDone: '', performer: '', product: '', batch: '', note: '', supplyItemId: '', quantity: '' };

/** The few medicines are filtered on the client, so they are one page of the picker. */
const asPage = <T,>(items: T[]) => ({ items, page: 0, size: items.length, total: items.length });

/** Due state as badge + icon, so an overdue item is never marked by colour alone (BR-064). */
function DueBadge({ state }: { state: string }) {
  const label = vi.vet.dueStates[state] ?? state;
  if (state === 'OVERDUE') return <Badge tone="injured" icon={<TriangleAlert aria-hidden />}>{label}</Badge>;
  if (state === 'DUE_SOON') return <Badge tone="watch" icon={<Clock aria-hidden />}>{label}</Badge>;
  return <Badge tone="eligible" icon={<CircleCheck aria-hidden />}>{label}</Badge>;
}

/** The latest completion in one line (date, product, who); notes and batch stay in the details (BR-187). */
function LastDone({ item }: { item: CareItem }) {
  const last = item.lastCompletion;
  if (!last) return <span className="text-muted-foreground">{vi.vet.neverDone}</span>;
  return (
    <span className="grid gap-0.5">
      <span className="tabular">{last.dateDone}</span>
      {(last.product || last.performer) && (
        <span className="text-meta text-muted-foreground">{[last.product, last.performer].filter(Boolean).join(' · ')}</span>
      )}
    </span>
  );
}

/** Filters shared by the two care lists: type and state. */
function CareFilters({ list }: { list: { filters: Record<string, string>; setFilter: (n: string, v: string) => void } }) {
  return (
    <>
      <Select aria-label={vi.vet.type} value={list.filters.type ?? ''} onChange={(e) => list.setFilter('type', e.target.value)} className="w-44">
        <option value="">{vi.vet.anyType}</option>
        {CARE_TYPES.map((t) => (
          <option key={t} value={t}>
            {vi.vet.careTypes[t]}
          </option>
        ))}
      </Select>
      <Select aria-label={vi.vet.state} value={list.filters.state ?? ''} onChange={(e) => list.setFilter('state', e.target.value)} className="w-44">
        <option value="">{vi.vet.anyState}</option>
        {Object.entries(vi.vet.dueStates).map(([code, label]) => (
          <option key={code} value={code}>
            {label}
          </option>
        ))}
      </Select>
    </>
  );
}

/** Veterinarian: the due-soon and overdue list (BR-064) and the care items of one horse (BR-061 to BR-063). */
export default function CarePage() {
  const { can } = useAuth();
  const photoOf = useHorsePhotos();
  const allowed = can('MANAGE_CARE');
  const [horse, setHorse] = useState<Chosen | null>(null);
  const horseId: number | '' = horse ? horse.id : '';
  // The performer of a completion is a staff member picked from the list or, for a vet from outside, typed text (BR-186).
  const [outsider, setOutsider] = useState(false);
  const [history, setHistory] = useState<{ id: number; q: string; page: Page<CareEvent> } | null>(null);
  const [form, setForm] = useState({ type: '', name: '', intervalDays: '' as number | '', firstDue: '' });
  const [planned, setPlanned] = useState<{ medicine: Chosen | null; quantity: string }>({ medicine: null, quantity: '' });
  // Medicines in stock for the completion picker; the ones at zero are not offered (BR-172).
  const [medicines, setMedicines] = useState<Medicine[]>([]);
  const [done, setDone] = useState<DoneForm>(NO_DONE);
  const [change, setChange] = useState({ id: 0, kind: 'override' as 'override' | 'postpone', newDue: '', reason: '' });
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState<string | null>(null);
  const [addOpen, setAddOpen] = useState(false);

  const due = usePagedList((query) => api.careDue(query), { filters: ['type', 'state'], prefix: 'd', enabled: allowed });
  const items = usePagedList((query) => api.careOf(horseId as number, query), {
    filters: ['type', 'state'],
    prefix: 'c',
    fixed: { horse: horseId },
    enabled: allowed && horseId !== '',
  });

  const completing = done.id !== 0 && done.type !== '' && done.type !== 'HOOF_CHECK';
  useEffect(() => {
    if (completing) api.medicines().then(setMedicines).catch(() => setMedicines([]));
  }, [completing, done.id]);

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  async function act(key: string, action: () => Promise<unknown>) {
    setError(null);
    setFieldErrors({});
    setPending(key);
    try {
      await action();
      await Promise.all([due.load.reload(), horseId !== '' ? items.load.reload() : undefined]);
    } catch (e) {
      setError(messageOf(e));
      setFieldErrors(fieldsOf(e));
    } finally {
      setPending(null);
    }
  }

  const rowMark = (i: CareItem) => (i.state === 'OVERDUE' ? 'bg-status-injured/40' : undefined);

  /** The four things done to a care item; each opens its own popup, so the table keeps its place. */
  const rowActions = (i: CareItem) => (
    <>
      <Button variant="outline" size="sm" onClick={() => setDone({ ...NO_DONE, id: i.id, type: i.type })}>
        <SquareCheckBig aria-hidden />
        {vi.vet.complete}
      </Button>
      <Button variant="outline" size="icon" title={vi.vet.override} aria-label={vi.vet.override} onClick={() => setChange({ id: i.id, kind: 'override', newDue: '', reason: '' })}>
        <PencilLine aria-hidden />
      </Button>
      <Button variant="outline" size="icon" title={vi.vet.postpone} aria-label={vi.vet.postpone} onClick={() => setChange({ id: i.id, kind: 'postpone', newDue: '', reason: '' })}>
        <CalendarClock aria-hidden />
      </Button>
      <Button
        variant="outline"
        size="icon"
        title={vi.vet.history}
        aria-label={vi.vet.history}
        onClick={async () => {
          try {
            setHistory({ id: i.id, q: '', page: await api.careHistory(i.id) });
          } catch (e) {
            notify.error(e);
          }
        }}
      >
        <History aria-hidden />
      </Button>
    </>
  );

  const dueColumns: DataColumn<CareItem>[] = [
    {
      id: 'horse',
      header: vi.incidents.horse,
      primary: true,
      cell: (d) => (
        <span className="inline-flex items-center gap-2">
          <HorseAvatar id={d.horseId} name={d.horseName} photoVersion={photoOf(d.horseId)} className="size-8 shrink-0 text-sm" />
          <Link to={`/horses/${d.horseId}`} className="font-display text-[1.0625rem] font-medium text-foreground hover:text-primary hover:underline">
            {d.horseName}
          </Link>
        </span>
      ),
    },
    { id: 'type', header: vi.vet.type, cell: (d) => vi.vet.careTypes[d.type] },
    { id: 'name', header: vi.vet.name, cell: (d) => d.name },
    { id: 'due', header: vi.vet.nextDue, cell: (d) => <span className="tabular">{d.nextDue}</span> },
    { id: 'last', header: vi.vet.lastDone, cell: (d) => <LastDone item={d} /> },
    { id: 'state', header: vi.vet.state, hideLabelOnMobile: true, cell: (d) => <DueBadge state={d.state} /> },
    { id: 'actions', header: vi.vet.actions, actions: true, cell: (d) => rowActions(d) },
  ];

  const itemColumns: DataColumn<CareItem>[] = [
    { id: 'type', header: vi.vet.type, primary: true, cell: (i) => vi.vet.careTypes[i.type] },
    {
      id: 'name',
      header: vi.vet.name,
      cell: (i) => (
        <span className="grid gap-0.5">
          <span>{i.name}</span>
          {i.plannedSupplyItemName && i.plannedQuantity && (
            <span className="text-meta text-muted-foreground">{vi.vet.plannedDoseOf(i.plannedSupplyItemName, i.plannedQuantity)}</span>
          )}
        </span>
      ),
    },
    { id: 'due', header: vi.vet.nextDue, cell: (i) => <span className="tabular">{i.nextDue}</span> },
    { id: 'last', header: vi.vet.lastDone, cell: (i) => <LastDone item={i} /> },
    { id: 'state', header: vi.vet.state, hideLabelOnMobile: true, cell: (i) => <DueBadge state={i.state} /> },
    { id: 'actions', header: vi.vet.actions, actions: true, cell: (i) => rowActions(i) },
  ];

  // A medicine is chosen from stock with its dose; a hoof check keeps a free-text product (BR-172).
  const doneFields = [
    ['dateDone', vi.vet.dateDone, 'date'],
    ['performer', vi.vet.performer, 'text'],
    ...(completing ? [] : ([['product', vi.vet.product, 'text']] as const)),
    ['batch', vi.vet.batch, 'text'],
    ['note', vi.vet.note, 'text'],
  ] as const;
  const chosenMedicine = medicines.find((m) => m.id === done.supplyItemId) ?? null;

  const errorBox =
    error && Object.keys(fieldErrors).length === 0 ? (
      <Callout tone="danger" icon={CircleAlert} role="alert">
        {error}
      </Callout>
    ) : null;
  const closeDone = (open: boolean) => {
    if (!open) {
      setDone(NO_DONE);
      setError(null);
      setFieldErrors({});
    }
  };
  const closeChange = (open: boolean) => {
    if (!open) {
      setChange({ ...change, id: 0 });
      setError(null);
      setFieldErrors({});
    }
  };

  return (
    <>
      <PageHeader
        title={vi.vet.care}
        actions={
          <Button onClick={() => setAddOpen(true)} disabled={horseId === ''} title={horseId === '' ? vi.vet.chooseHorseFirst : undefined}>
            <Plus aria-hidden />
            {vi.vet.addCare}
          </Button>
        }
      />
      {error && Object.keys(fieldErrors).length > 0 ? null : !done.id && !change.id && !addOpen && error ? (
        <Callout tone="danger" icon={CircleAlert} role="alert" className="mb-6">
          {error}
        </Callout>
      ) : null}

      <div className="mb-6 grid gap-2 rounded-lg border bg-card p-4 md:p-5">
        <FormField label={vi.incidents.horse} description={vi.vet.horseCare} className="md:max-w-md">
          <SearchSelect
            value={horse}
            clearable
            placeholder={vi.incidents.chooseHorse}
            load={(q) => api.options('horses', { q })}
            onChange={(chosen) => {
              setHorse(chosen);
              setHistory(null);
              setDone(NO_DONE);
              setAddOpen(false);
              setChange({ id: 0, kind: 'override', newDue: '', reason: '' });
            }}
          />
        </FormField>
      </div>

      {horseId === '' ? (
        <section aria-labelledby="care-due-title" className="grid gap-4">
          <h2 id="care-due-title" className="text-h2">
            {vi.vet.dueList}
          </h2>
          <ListToolbar query={due.query} onQuery={due.setQuery} placeholder={vi.vet.searchPlaceholder} total={due.load.state.data?.total}>
            <CareFilters list={due} />
          </ListToolbar>
          <DataView
            load={due.load}
            isEmpty={(data) => data.items.length === 0}
            empty={
              due.narrowed ? (
                <EmptyState icon={CalendarCheck} title={vi.common.noResults} text={vi.common.noResultsHint} />
              ) : (
                <EmptyState icon={CalendarCheck} title={vi.vet.noCare} text={vi.vet.noCareHint} />
              )
            }
          >
            {(data) => (
              <div className="grid gap-3">
                <DataTable caption={vi.vet.dueList} data={data.items} columns={dueColumns} rowKey={(d) => d.id} rowClassName={rowMark} />
                <ListFooter data={data} onPage={due.setPage} onSize={due.setSize} />
              </div>
            )}
          </DataView>
        </section>
      ) : (
        <section aria-labelledby="care-horse-title" className="grid gap-4">
          <h2 id="care-horse-title" className="text-h2">
            {vi.vet.care}: {horse?.label}
          </h2>
          <ListToolbar query={items.query} onQuery={items.setQuery} placeholder={vi.vet.searchPlaceholder} total={items.load.state.data?.total}>
            <CareFilters list={items} />
          </ListToolbar>
          <DataView
            load={items.load}
            isEmpty={(data) => data.items.length === 0}
            empty={
              items.narrowed ? (
                <EmptyState icon={CalendarCheck} title={vi.common.noResults} text={vi.common.noResultsHint} />
              ) : (
                <EmptyState
                  icon={CalendarCheck}
                  title={vi.vet.noCare}
                  action={
                    <Button onClick={() => setAddOpen(true)}>
                      <Plus aria-hidden />
                      {vi.vet.addCare}
                    </Button>
                  }
                />
              )
            }
          >
            {(data) => (
              <div className="grid gap-3">
                <DataTable caption={vi.vet.care} data={data.items} columns={itemColumns} rowKey={(i) => i.id} rowClassName={rowMark} />
                <ListFooter data={data} onPage={items.setPage} onSize={items.setSize} />
              </div>
            )}
          </DataView>
        </section>
      )}

      <Dialog open={done.id !== 0} onOpenChange={closeDone}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{vi.vet.complete}</DialogTitle>
            <DialogDescription className="sr-only">{vi.vet.complete}</DialogDescription>
          </DialogHeader>
          {errorBox}
                <form
                  className="grid gap-5"
                  aria-label={vi.vet.complete}
                  noValidate
                  onSubmit={(e: FormEvent) => {
                    e.preventDefault();
                    void act('done', async () => {
                      const { id, type: _type, quantity, supplyItemId, product, ...rest } = done;
                      void _type;
                      await api.completeCare(id, completing
                        ? { ...rest, supplyItemId, quantity: quantity.trim() === '' ? '' : Number(quantity) }
                        : { ...rest, product });
                      notify.success(vi.common.saved);
                      setDone(NO_DONE);
                    });
                  }}
                >
                  <div className="grid gap-5 md:grid-cols-2">
                    {doneFields.map(([k, label, type]) =>
                      k === 'performer' ? (
                        <div key={k} className="grid gap-2 md:col-span-2">
                          {outsider ? (
                            <FormField label={`${label} (${vi.vet.performerOutside})`} error={fieldErrors[k]}>
                              <Input type="text" value={done.performer} onChange={(e) => setDone({ ...done, performer: e.target.value })} />
                            </FormField>
                          ) : (
                            <FormField label={label} error={fieldErrors[k]}>
                              <SearchSelect
                                value={done.performer ? { id: 0, label: done.performer } : null}
                                placeholder={vi.vet.choosePerformer}
                                load={(q) => api.options('staff', { q })}
                                onChange={(chosen) => setDone({ ...done, performer: chosen ? chosen.label : '' })}
                              />
                            </FormField>
                          )}
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="justify-self-start"
                            onClick={() => {
                              setOutsider((o) => !o);
                              setDone({ ...done, performer: '' });
                            }}
                          >
                            {outsider ? vi.vet.performerStaff : vi.vet.performerOutside}
                          </Button>
                        </div>
                      ) : (
                        <FormField key={k} label={label} error={fieldErrors[k]} className={k === 'dateDone' ? undefined : 'md:col-span-2'}>
                          <Input type={type} value={done[k]} onChange={(e) => setDone({ ...done, [k]: e.target.value })} />
                        </FormField>
                      ),
                    )}
                    {completing && (
                      <>
                        <FormField label={vi.vet.medicine} error={fieldErrors.supplyItemId} className="md:col-span-2">
                          <SearchSelect
                            value={chosenMedicine ? { id: chosenMedicine.id, label: vi.vet.medicineOption(chosenMedicine.name, chosenMedicine.quantity, chosenMedicine.unit) } : null}
                            placeholder={vi.vet.chooseMedicine}
                            load={async (q) =>
                              asPage(
                                medicines
                                  .filter((m) => m.name.toLowerCase().includes(q.trim().toLowerCase()))
                                  .map((m) => ({ id: m.id, label: vi.vet.medicineOption(m.name, m.quantity, m.unit), hint: null })),
                              )
                            }
                            onChange={(chosen) => setDone({ ...done, supplyItemId: chosen ? chosen.id : '', quantity: '' })}
                          />
                        </FormField>
                        <FormField
                          label={vi.vet.dose(chosenMedicine?.unit)}
                          description={chosenMedicine ? vi.vet.inStock(chosenMedicine.quantity, chosenMedicine.unit) : undefined}
                          error={fieldErrors.quantity}
                          className="md:max-w-xs"
                        >
                          <Input
                            type="number"
                            inputMode="numeric"
                            min={1}
                            max={chosenMedicine?.quantity}
                            value={done.quantity}
                            onChange={(e) => setDone({ ...done, quantity: e.target.value })}
                          />
                        </FormField>
                      </>
                    )}
                  </div>
                  <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                    <Button variant="outline" onClick={() => setDone(NO_DONE)}>
                      {vi.common.cancel}
                    </Button>
                    <Button type="submit" loading={pending === 'done'}>
                      {vi.common.save}
                    </Button>
                  </div>
                </form>
        </DialogContent>
      </Dialog>

      <Dialog open={change.id !== 0} onOpenChange={closeChange}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{change.kind === 'override' ? vi.vet.override : vi.vet.postpone}</DialogTitle>
            <DialogDescription className="sr-only">{change.kind === 'override' ? vi.vet.override : vi.vet.postpone}</DialogDescription>
          </DialogHeader>
          {errorBox}
                <form
                  className="grid gap-5"
                  aria-label={change.kind === 'override' ? vi.vet.override : vi.vet.postpone}
                  noValidate
                  onSubmit={(e: FormEvent) => {
                    e.preventDefault();
                    void act('change', async () => {
                      await (change.kind === 'override' ? api.overrideCare : api.postponeCare)(change.id, change.newDue, change.reason);
                      setChange({ ...change, id: 0 });
                    });
                  }}
                >
                  <FormField label={vi.vet.newDue} error={fieldErrors.newDue} className="md:max-w-xs">
                    <Input type="date" value={change.newDue} onChange={(e) => setChange({ ...change, newDue: e.target.value })} />
                  </FormField>
                  <FormField label={vi.vet.reason} error={fieldErrors.reason}>
                    <Input value={change.reason} onChange={(e) => setChange({ ...change, reason: e.target.value })} />
                  </FormField>
                  <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                    <Button variant="outline" onClick={() => setChange({ ...change, id: 0 })}>
                      {vi.common.cancel}
                    </Button>
                    <Button type="submit" loading={pending === 'change'}>
                      {vi.common.save}
                    </Button>
                  </div>
                </form>
        </DialogContent>
      </Dialog>

      <Dialog open={history !== null} onOpenChange={(open) => !open && setHistory(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{vi.vet.history}</DialogTitle>
            <DialogDescription className="sr-only">{vi.vet.history}</DialogDescription>
          </DialogHeader>
          {history && (
                <div role="group" aria-label={vi.vet.history}>
                <PanelSearch
                  data={history.page}
                  q={history.q}
                  onSearch={(q) =>
                    api
                      .careHistory(history.id, { q: q || undefined })
                      .then((next) => setHistory({ id: history.id, q, page: next }))
                      .catch((e) => notify.error(e))
                  }
                />
                {history.page.total === 0 && history.q && <NoMatches />}
                <StaggerList className="divide-y rounded-lg border bg-card text-sm">
                  {history.page.items.map((ev, i) => (
                    <div key={i} className="px-4 py-3">
                      <span className="font-medium">{vi.vet.eventKinds[ev.kind]}</span>{' '}
                      <span className="tabular">
                        {ev.eventDate}: {ev.previousDue} → {ev.newDue}
                      </span>
                      {ev.performer && ` (${ev.performer})`}
                      {ev.product && ` · ${ev.product}`}
                      {ev.quantity != null && ` × ${ev.quantity}`}
                      {ev.batch && ` · ${vi.vet.batch}: ${ev.batch}`}
                      {ev.note && ` – ${ev.note}`}
                    </div>
                  ))}
                </StaggerList>
                <ListFooter
                  data={history.page}
                  onPage={(page) =>
                    api
                      .careHistory(history.id, { page, q: history.q || undefined })
                      .then((next) => setHistory({ id: history.id, q: history.q, page: next }))
                      .catch((e) => notify.error(e))
                  }
                />
                </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={addOpen} onOpenChange={(open) => { setAddOpen(open); if (!open) { setError(null); setFieldErrors({}); } }}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{vi.vet.addCare}{horse ? `: ${horse.label}` : ''}</DialogTitle>
            <DialogDescription className="sr-only">{vi.vet.addCare}</DialogDescription>
          </DialogHeader>
          {errorBox}
            <form
              className="grid gap-5"
              aria-label={vi.vet.addCare}
              noValidate
              onSubmit={(e: FormEvent) => {
                e.preventDefault();
                void act('add', async () => {
                  const plan = planned.medicine && planned.quantity.trim() !== ''
                    ? { plannedSupplyItemId: planned.medicine.id, plannedQuantity: Number(planned.quantity) }
                    : {};
                  await api.createCare(horseId as number, { ...form, ...plan });
                  notify.success(vi.common.saved);
                  setForm({ type: '', name: '', intervalDays: '', firstDue: '' });
                  setPlanned({ medicine: null, quantity: '' });
                  setAddOpen(false);
                });
              }}
            >
              <div className="grid gap-5 md:grid-cols-2">
                <FormField label={vi.vet.type} error={fieldErrors.type}>
                  <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
                    <option value="">—</option>
                    {CARE_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {vi.vet.careTypes[t]}
                      </option>
                    ))}
                  </Select>
                </FormField>
                <FormField label={vi.vet.name} error={fieldErrors.name}>
                  <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
                </FormField>
                <FormField label={vi.vet.interval} error={fieldErrors.intervalDays}>
                  <Input
                    type="number"
                    inputMode="numeric"
                    value={form.intervalDays}
                    onChange={(e) => setForm({ ...form, intervalDays: e.target.value === '' ? '' : Number(e.target.value) })}
                  />
                </FormField>
                <FormField label={vi.vet.firstDue} error={fieldErrors.firstDue}>
                  <Input type="date" value={form.firstDue} onChange={(e) => setForm({ ...form, firstDue: e.target.value })} />
                </FormField>
                <FormField label={vi.vet.plannedMedicine} description={vi.vet.plannedHint} error={fieldErrors.plannedSupplyItemId}>
                  <SearchSelect
                    value={planned.medicine}
                    placeholder={vi.vet.chooseMedicine}
                    load={async (q) =>
                      asPage(
                        (await api.medicines(true))
                          .filter((m) => m.name.toLowerCase().includes(q.trim().toLowerCase()))
                          .map((m) => ({ id: m.id, label: m.name, hint: null })),
                      )
                    }
                    onChange={(chosen) => setPlanned({ ...planned, medicine: chosen })}
                  />
                </FormField>
                <FormField label={vi.vet.plannedDose} error={fieldErrors.plannedQuantity}>
                  <Input
                    type="number"
                    inputMode="numeric"
                    min={1}
                    value={planned.quantity}
                    onChange={(e) => setPlanned({ ...planned, quantity: e.target.value })}
                  />
                </FormField>
              </div>
              <div className="flex flex-col sm:flex-row sm:justify-end">
                <Button type="submit" loading={pending === 'add'}>
                  {vi.vet.addCare}
                </Button>
              </div>
            </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
