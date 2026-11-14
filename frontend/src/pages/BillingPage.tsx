import { useState, type FormEvent } from 'react';
import { CircleAlert, CircleCheck, Eye, Plus, Receipt, ShieldOff, X } from 'lucide-react';
import { api } from '../api/api';
import { PagerBar } from '../components/PagerBar';
import type { FeeTypeView, InvoiceRow, PriceRow, StatementOwnerRow } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { StatementView } from '../components/StatementView';
import { InvoicePayment } from '../components/InvoicePayment';
import { ExportButton, PrintLink } from '../components/StatementActions';
import { CategoryBreakdown, MonthlyTrend, OverviewCharts } from '../components/CostCharts';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle } from '../components/ui/alert-dialog';
import { Button } from '../components/ui/button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Badge, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { DateInput } from '../components/ui/typed-inputs';
import { MoneyInput } from '../components/ui/money-input';
import { LIMITS } from '../lib/limits';
import { BOUNDS, blank, collect, date as date_, focusFirstInvalid, maxLength, number, required, today } from '../lib/validate';
import { SearchSelect, type Chosen } from '../components/ui/search-select';
import { Select } from '../components/ui/select';
import { Callout, EmptyState, Skeleton } from '../components/ui/states';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { fold } from '../lib/text';
import { useTabParam } from '../hooks/useTabParam';
import { FilterTabs } from '../components/ui/tabs';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { notify } from '../lib/notify';
import { clubDateTime, clubMonth, formatVnd } from '../lib/utils';

const MONTHS_BACK = 18;

function MonthSelect({ value, onChange, allowAll }: { value: string; onChange: (m: string) => void; allowAll?: boolean }) {
  return (
    <FormField label={vi.billing.month} className="w-56">
      <Select value={value} onChange={(e) => onChange(e.target.value)}>
        {allowAll && <option value="all">{vi.billing.allMonths}</option>}
        {Array.from({ length: MONTHS_BACK }, (_, i) => clubMonth(-i)).map((m) => (
          <option key={m} value={m}>
            {m}
          </option>
        ))}
      </Select>
    </FormField>
  );
}

/** The signed-in owner's own statement of the month. */
function OwnerView() {
  const { me, can } = useAuth();
  const [month, setMonth] = useState(clubMonth());
  const statement = useLoad(() => api.myStatement(month), [month]);
  return (
    <div className="grid gap-6">
      <MonthSelect value={month} onChange={setMonth} />
      <div className="grid gap-4 lg:grid-cols-2">
        <MonthlyTrend title={vi.billing.charts.trendOwn} fetchTotal={(m) => api.myStatement(m).then((s) => s.total)} deps={[]} />
        <DataView load={statement} skeleton={<Skeleton className="h-72" />}>
          {(s) => <CategoryBreakdown statement={s} />}
        </DataView>
      </div>
      <DataView
        load={statement}
      >
        {(s) => (
          <div className="grid gap-4">
            {can('PAY_INVOICE') && s.closed && s.invoiceId != null && (
              <InvoicePayment key={s.invoiceId} invoiceId={s.invoiceId} number={s.invoiceNumber ?? ''} />
            )}
          <StatementView
            statement={s}
            actions={
              can('EXPORT_BILLING') && me ? (
                <>
                  <ExportButton run={() => api.exportMyStatement(month)} />
                  <PrintLink to={`/billing/print/statement/${me.id}?month=${month}`} />
                </>
              ) : undefined
            }
          />
          </div>
        )}
      </DataView>
    </div>
  );
}

function AdjustDialog({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved: () => void }) {
  const a = vi.billing.adjust;
  const [owner, setOwner] = useState<Chosen | null>(null);
  const [horse, setHorse] = useState<Chosen | null>(null);
  const [date, setDate] = useState('');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const refused = collect({
      ownerId: owner ? null : 'Bắt buộc chọn chủ ngựa.',
      date: required(date) ?? date_(date),
      amount: number(amount, { integer: true, min: -BOUNDS.price.max, max: BOUNDS.price.max, required: true }),
      reason: blank(reason) ? 'Bắt buộc nhập lý do.' : maxLength(reason, LIMITS.invoiceReason),
    });
    setErrors(refused);
    if (Object.keys(refused).length > 0) {
      focusFirstInvalid(e.currentTarget as HTMLFormElement);
      return;
    }
    setPending(true);
    try {
      await api.addAdjustment({
        ownerId: owner ? owner.id : '',
        horseId: horse ? horse.id : '',
        date,
        amount: amount === '' ? '' : Number(amount),
        reason,
      });
      notify.success(vi.common.saved);
      setOwner(null);
      setHorse(null);
      setAmount('');
      setReason('');
      onSaved();
      onClose();
    } catch (err) {
      setError(messageOf(err));
      setErrors(fieldsOf(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{a.title}</DialogTitle>
          <DialogDescription>{vi.billing.close.text}</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate aria-label={a.title} className="grid gap-4">
          {error && (
            <Callout tone="danger" icon={CircleAlert} role="alert">
              {error}
            </Callout>
          )}
          <FormField label={`${a.owner} *`} error={errors.ownerId}>
            <SearchSelect value={owner} onChange={setOwner} load={(q) => api.options('owners', { q })} placeholder={vi.horses.chooseOwner} />
          </FormField>
          <FormField label={a.horse} error={errors.horseId}>
            <SearchSelect value={horse} onChange={setHorse} clearable load={(q) => api.options('horses', { q })} />
          </FormField>
          <FormField label={`${a.date} *`} error={errors.date}>
            <DateInput max={today()} value={date} onChange={setDate} />
          </FormField>
          <FormField label={`${a.amount} *`} error={errors.amount}>
            <MoneyInput allowNegative value={amount} onChange={setAmount} />
          </FormField>
          <FormField label={`${a.reason} *`} error={errors.reason}>
            <Input maxLength={LIMITS.invoiceReason} value={reason} onChange={(e) => setReason(e.target.value)} />
          </FormField>
          <Button type="submit" loading={pending}>
            {a.submit}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Owners with charges in a month, one statement at a time, the month closing and the correction lines (PERM-051/052). */
function StatementsTab({ month }: { month: string }) {
  const owners = useLoad(() => api.statementOwners(month), [month]);
  const [selected, setSelected] = useState<StatementOwnerRow | null>(null);
  const statement = useLoad(() => api.ownerStatement(selected!.ownerId, month), [selected?.ownerId, month], selected !== null);
  // The owners of the month are one short list from the service: search and paging by 20 are done on it here (BR-185).
  const [text, setText] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [adjusting, setAdjusting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  // What closing did or why it was refused stays here until dismissed, so the answer cannot be missed (BR-205).
  const [outcome, setOutcome] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const closing = useLoad(() => api.closingStatus(month), [month]);
  const closingState = closing.state.data;
  const ready = closingState?.state === 'READY';

  async function close() {
    setConfirming(false);
    setOutcome(null);
    try {
      const r = await api.closeMonth(month);
      const text =
        r.invoices === 0
          ? vi.billing.close.doneEmpty.replace('{month}', r.month)
          : vi.billing.close.done.replace('{month}', r.month).replace('{count}', String(r.invoices)).replace('{total}', formatVnd(r.total));
      notify.success(text);
      setOutcome({ tone: 'success', text });
    } catch (err) {
      setOutcome({ tone: 'danger', text: messageOf(err) });
    }
    await Promise.allSettled([owners.reload(), closing.reload(), selected ? statement.reload() : Promise.resolve()]);
  }

  const columns: DataColumn<StatementOwnerRow>[] = [
    { id: 'owner', header: vi.billing.owner, primary: true, cell: (o) => o.ownerName },
    { id: 'total', header: vi.billing.total, cell: (o) => <span className="tabular">{formatVnd(o.total)}</span> },
    {
      id: 'state',
      header: vi.billing.invoice,
      cell: (o) => <Badge tone={o.closed ? 'eligible' : 'outline'}>{o.closed ? vi.billing.closed : vi.billing.open}</Badge>,
    },
    {
      id: 'view',
      header: vi.billing.view,
      actions: true,
      cell: (o) => (
        <Button size="sm" variant="outline" onClick={() => setSelected(o)} aria-label={`${vi.billing.view} ${o.ownerName}`}>
          <Eye aria-hidden />
          {vi.billing.view}
        </Button>
      ),
    },
  ];

  return (
    <div className="grid gap-6">
      {outcome && (
        <Callout tone={outcome.tone} icon={outcome.tone === 'success' ? CircleCheck : CircleAlert} role={outcome.tone === 'danger' ? 'alert' : 'status'}>
          <div className="flex items-start justify-between gap-3">
            <span>{outcome.text}</span>
            <Button type="button" variant="ghost" size="sm" onClick={() => setOutcome(null)} aria-label={vi.common.close}>
              <X aria-hidden />
            </Button>
          </div>
        </Callout>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="outline" onClick={() => setAdjusting(true)}>
          {vi.billing.adjust.button}
        </Button>
        <Button disabled={!ready} onClick={() => setConfirming(true)}>
          {vi.billing.close.button}
        </Button>
        <ExportButton run={() => api.exportAllStatements(month)} label={vi.billing.exportAll} />
        <p className="min-w-0 flex-1 text-sm text-muted-foreground" aria-live="polite">
          {closing.state.status === 'error' && closingState === null
            ? closing.state.error
            : ready
              ? vi.billing.close.ready.replace('{month}', month)
              : closingState?.message}
        </p>
      </div>
      <DataView
        load={owners}
        isEmpty={(list) => list.length === 0}
        empty={<EmptyState icon={Receipt} title={vi.billing.noOwners} />}
      >
        {(list) => {
          const q = fold(text.trim());
          const found = list.filter((o) => !q || fold(o.ownerName).includes(q));
          const pages = Math.max(1, Math.ceil(found.length / size));
          const at = Math.min(page, pages - 1);
          const shown = { items: found.slice(at * size, at * size + size), page: at, size, total: found.length };
          return (
            <div className="grid gap-4">
              <ListToolbar
                query={text}
                onQuery={(v) => {
                  setText(v);
                  setPage(0);
                }}
                placeholder={vi.billing.searchOwner}
                total={found.length}
              />
              {found.length === 0 ? (
                <EmptyState icon={Receipt} title={vi.common.noResults} text={vi.common.noResultsHint} />
              ) : (
                <div className="grid gap-3">
                  <DataTable caption={vi.billing.tabs.statements} data={shown.items} columns={columns} rowKey={(o) => o.ownerId} />
                  <ListFooter
                    data={shown}
                    onPage={setPage}
                    onSize={(n) => {
                      setSize(n);
                      setPage(0);
                    }}
                  />
                </div>
              )}
            </div>
          );
        }}
      </DataView>
      <Dialog open={selected !== null} onOpenChange={(open) => !open && setSelected(null)}>
        <DialogContent className="max-h-[90vh] max-w-4xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{selected ? vi.billing.statementOf(selected.ownerName) : ''}</DialogTitle>
            <DialogDescription className="sr-only">{vi.billing.statementOf(selected?.ownerName ?? '')}</DialogDescription>
          </DialogHeader>
          {selected && (
            <DataView load={statement}>
              {(st) => (
                <StatementView
                  statement={st}
                  actions={
                    <>
                      <ExportButton run={() => api.exportOwnerStatement(selected.ownerId, month)} />
                      <PrintLink to={st.invoiceId ? `/billing/print/invoice/${st.invoiceId}` : `/billing/print/statement/${selected.ownerId}?month=${month}`} />
                    </>
                  }
                />
              )}
            </DataView>
          )}
        </DialogContent>
      </Dialog>
      <AdjustDialog
        open={adjusting}
        onClose={() => setAdjusting(false)}
        onSaved={() => {
          void owners.reload();
          if (selected) void statement.reload();
        }}
      />
      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogTitle>{vi.billing.close.title.replace('{month}', month)}</AlertDialogTitle>
          <AlertDialogDescription>{vi.billing.close.text}</AlertDialogDescription>
          <AlertDialogFooter>
            <AlertDialogCancel>{vi.common.cancel}</AlertDialogCancel>
            <AlertDialogAction onClick={() => void close()}>{vi.billing.close.confirm}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function History({ rows }: { rows: PriceRow[] }) {
  if (rows.length === 0) return <span className="text-muted-foreground">{vi.billing.prices.none}</span>;
  return (
    <ul className="m-0 grid min-w-0 list-none grid-cols-[minmax(0,1fr)] gap-0.5 p-0 text-meta">
      {rows.map((r) => (
        <li key={r.id} className="tabular whitespace-normal [overflow-wrap:anywhere]">
          {formatVnd(r.amount)} · {vi.billing.prices.from} {r.effectiveFrom}
        </li>
      ))}
    </ul>
  );
}

type PriceTarget = { kind: 'DAILY_FEE' | 'EXAM_FEE'; feeTypeId: number | null; title: string };

/** A fee type is added with its first price: the name, the price and the day it starts, all required (BR-207, AC-452, AC-453). */
function NewFeeTypeDialog({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved: () => void }) {
  const p = vi.billing.prices;
  const [name, setName] = useState('');
  const [amount, setAmount] = useState('');
  const [from, setFrom] = useState(today());
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const refused = collect({
      name: required(name) ?? maxLength(name, LIMITS.feeTypeName),
      amount: number(amount, { integer: true, ...BOUNDS.price, required: true }),
      effectiveFrom: required(from) ?? date_(from),
    });
    setErrors(refused);
    if (Object.keys(refused).length > 0) {
      focusFirstInvalid(e.currentTarget as HTMLFormElement);
      return;
    }
    setPending(true);
    try {
      await api.createFeeType(name, Number(amount), from);
      notify.success(vi.common.saved);
      setName('');
      setAmount('');
      setFrom(today());
      onSaved();
      onClose();
    } catch (err) {
      setError(messageOf(err));
      setErrors(fieldsOf(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{p.newType}</DialogTitle>
          <DialogDescription>{p.newTypeHint}</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate aria-label={p.newType} className="grid gap-4">
          {error && (
            <Callout tone="danger" icon={CircleAlert} role="alert">
              {error}
            </Callout>
          )}
          <FormField label={`${p.typeName} *`} error={errors.name}>
            <Input maxLength={LIMITS.feeTypeName} value={name} onChange={(e) => setName(e.target.value)} />
          </FormField>
          <FormField label={`${p.amount} *`} error={errors.amount}>
            <MoneyInput value={amount} onChange={setAmount} />
          </FormField>
          <FormField label={`${p.from} *`} error={errors.effectiveFrom}>
            <DateInput min={today()} value={from} onChange={setFrom} />
          </FormField>
          <Button type="submit" loading={pending}>
            {p.save}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function PriceDialog({ target, onClose, onSaved }: { target: PriceTarget | null; onClose: () => void; onSaved: () => void }) {
  const p = vi.billing.prices;
  const [amount, setAmount] = useState('');
  const [from, setFrom] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!target) return;
    setError(null);
    const refused = collect({
      amount: number(amount, { integer: true, ...BOUNDS.price, required: true }),
      effectiveFrom: required(from) ?? date_(from),
    });
    setErrors(refused);
    if (Object.keys(refused).length > 0) {
      focusFirstInvalid(e.currentTarget as HTMLFormElement);
      return;
    }
    setPending(true);
    try {
      await api.setPrice(target.kind, target.feeTypeId, Number(amount), from);
      notify.success(vi.common.saved);
      setAmount('');
      onSaved();
      onClose();
    } catch (err) {
      setError(messageOf(err));
      setErrors(fieldsOf(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={target !== null} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{target?.title}</DialogTitle>
          <DialogDescription>{p.dateHint}</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate aria-label={p.add} className="grid gap-4">
          {error && (
            <Callout tone="danger" icon={CircleAlert} role="alert">
              {error}
            </Callout>
          )}
          <FormField label={`${p.amount} *`} error={errors.amount}>
            <MoneyInput value={amount} onChange={setAmount} />
          </FormField>
          <FormField label={`${p.from} *`} error={errors.effectiveFrom}>
            <DateInput value={from} onChange={setFrom} />
          </FormField>
          <Button type="submit" loading={pending}>
            {p.save}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** The service price list: the daily fee and the fee of each kind of examination, each with its history (BR-178). */
function PricesTab() {
  const p = vi.billing.prices;
  const prices = useLoad(() => api.priceList(), []);
  const [target, setTarget] = useState<PriceTarget | null>(null);
  const [adding, setAdding] = useState(false);
  const [typeText, setTypeText] = useState('');

  async function deactivate(t: FeeTypeView) {
    try {
      await api.deactivateFeeType(t.id);
      await prices.reload();
    } catch (err) {
      notify.error(messageOf(err));
    }
  }

  const columns: DataColumn<FeeTypeView>[] = [
    { id: 'name', header: p.typeName, primary: true, cell: (t) => (t.active ? t.name : `${t.name} (${p.inactive})`) },
    { id: 'current', header: p.current, cell: (t) => <span className="tabular">{t.currentPrice === null ? p.none : formatVnd(t.currentPrice)}</span> },
    { id: 'history', header: p.history, cell: (t) => <History rows={t.prices} /> },
    {
      id: 'actions',
      header: p.add,
      actions: true,
      cell: (t) =>
        t.active ? (
          <span className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" onClick={() => setTarget({ kind: 'EXAM_FEE', feeTypeId: t.id, title: `${p.add}: ${t.name}` })}>
              {p.add}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => void deactivate(t)}>
              {p.deactivate}
            </Button>
          </span>
        ) : null,
    },
  ];

  return (
    <DataView load={prices}>
      {(list) => (
        <div className="grid gap-8">
          <Section title={p.daily}>
            <div className="grid gap-3">
              <p className="tabular text-sm">
                {p.current}: <strong>{list.dailyFee.currentPrice === null ? p.none : formatVnd(list.dailyFee.currentPrice)}</strong>
              </p>
              <History rows={list.dailyFee.prices} />
              <div>
                <Button variant="outline" onClick={() => setTarget({ kind: 'DAILY_FEE', feeTypeId: null, title: `${p.add}: ${p.daily}` })}>
                  {p.add}
                </Button>
              </div>
            </div>
          </Section>
          <Section title={p.examFees}>
            <div className="mb-4">
              <ListToolbar query={typeText} onQuery={setTypeText} placeholder={p.searchType}>
                <Button variant="outline" onClick={() => setAdding(true)}>
                  <Plus aria-hidden />
                  {p.newType}
                </Button>
              </ListToolbar>
            </div>
            {list.feeTypes.filter((t) => !typeText.trim() || fold(t.name).includes(fold(typeText.trim()))).length === 0 ? (
              <EmptyState icon={Receipt} title={p.noMatch} />
            ) : (
              <DataTable
                caption={p.examFees}
                data={list.feeTypes.filter((t) => !typeText.trim() || fold(t.name).includes(fold(typeText.trim())))}
                columns={columns}
                rowKey={(t) => t.id}
              />
            )}
          </Section>
          <NewFeeTypeDialog open={adding} onClose={() => setAdding(false)} onSaved={() => void prices.reload()} />
          <PriceDialog target={target} onClose={() => setTarget(null)} onSaved={() => void prices.reload()} />
        </div>
      )}
    </DataView>
  );
}

function InvoicesTab({ month }: { month: string }) {
  const [invoicePage, setInvoicePage] = useState(0);
  const invoices = useLoad(() => api.invoices(month, invoicePage), [month, invoicePage]);
  const [selected, setSelected] = useState<InvoiceRow | null>(null);
  const detail = useLoad(() => api.invoice(selected!.id), [selected?.id], selected !== null);
  const columns: DataColumn<InvoiceRow>[] = [
    { id: 'number', header: vi.billing.invoices.number, primary: true, cell: (i) => <span className="tabular">{i.number}</span> },
    { id: 'owner', header: vi.billing.owner, cell: (i) => i.ownerName },
    { id: 'total', header: vi.billing.total, cell: (i) => <span className="tabular">{formatVnd(i.total)}</span> },
    { id: 'closedAt', header: vi.billing.invoices.closedAt, cell: (i) => clubDateTime(i.closedAt) },
    {
      id: 'payment',
      header: vi.payment.status,
      cell: (i) =>
        i.paymentStatus === 'PAID' ? (
          <Badge tone="eligible">{vi.payment.paid}</Badge>
        ) : (
          <Badge tone="watch">{vi.payment.unpaid}</Badge>
        ),
    },
    {
      id: 'view',
      header: vi.billing.view,
      actions: true,
      cell: (i) => (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" onClick={() => setSelected(i)} aria-label={`${vi.billing.view} ${i.number}`}>
            {vi.billing.view}
          </Button>
          <ExportButton run={() => api.exportInvoice(i.id)} ariaLabel={`${vi.billing.exportExcel} ${i.number}`} />
          <PrintLink to={`/billing/print/invoice/${i.id}`} ariaLabel={`${vi.billing.print} ${i.number}`} />
        </div>
      ),
    },
  ];
  return (
    <div className="grid gap-6">
      <DataView
        load={invoices}
        isEmpty={(p) => p.items.length === 0}
        empty={<EmptyState icon={Receipt} title={vi.billing.invoices.empty} />}
      >
        {(p) => (
          <>
            <DataTable caption={vi.billing.tabs.invoices} data={p.items} columns={columns} rowKey={(i) => i.id} />
            <PagerBar page={p.page} size={p.size} total={p.total} onPage={setInvoicePage} />
          </>
        )}
      </DataView>
      <Dialog open={selected !== null} onOpenChange={(open) => !open && setSelected(null)}>
        <DialogContent className="max-h-[90vh] max-w-4xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{selected ? `${vi.billing.invoice} ${selected.number}` : ''}</DialogTitle>
            <DialogDescription className="sr-only">{selected ? `${vi.billing.invoice} ${selected.number}` : ''}</DialogDescription>
          </DialogHeader>
          {selected && <DataView load={detail}>{(s) => <StatementView statement={s} />}</DataView>}
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** Only charts: the same four cards for a chosen month and for all months together (BR-180, BR-209). */
function OverviewTab({ month }: { month: string }) {
  const overview = useLoad(() => api.billingOverview(month), [month]);
  return (
    <DataView load={overview} skeleton={<Skeleton className="h-[34rem]" />}>
      {(o) => <OverviewCharts overview={o} />}
    </DataView>
  );
}

type Tab = 'overview' | 'statements' | 'prices' | 'invoices';
const TABS = ['overview', 'statements', 'prices', 'invoices'] as const;

/** The Club Manager's side: the overview, statements per owner, the price list, closed invoices. */
function ManagerView() {
  const [tab, setTab] = useTabParam<Tab>(TABS);
  const [month, setMonth] = useState(clubMonth(-1));
  return (
    <div className="grid gap-6">
      <FilterTabs<Tab>
        label={vi.billing.tabs.label}
        value={tab}
        onChange={setTab}
        options={[
          { value: 'overview', label: vi.billing.tabs.overview },
          { value: 'statements', label: vi.billing.tabs.statements },
          { value: 'prices', label: vi.billing.tabs.prices },
          { value: 'invoices', label: vi.billing.tabs.invoices },
        ]}
      />
      {tab !== 'prices' && <MonthSelect value={month} onChange={setMonth} allowAll={tab === 'overview'} />}
      {tab === 'overview' && <OverviewTab month={month} />}
      {tab === 'statements' && <StatementsTab key={month} month={month} />}
      {tab === 'prices' && <PricesTab />}
      {tab === 'invoices' && <InvoicesTab key={month} month={month} />}
    </div>
  );
}

/** Cost statements (owner: own horses; Club Manager: all), the price list and the month closing (PERM-051, PERM-052). */
export default function BillingPage() {
  const { can } = useAuth();
  if (!can('VIEW_BILLING')) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }
  return (
    <>
      <PageHeader title={vi.billing.title} description={vi.billing.subtitle} />
      {can('SET_PRICES_AND_CLOSE_MONTH') ? <ManagerView /> : <OwnerView />}
    </>
  );
}
