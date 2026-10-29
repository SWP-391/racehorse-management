import { useState, type FormEvent } from 'react';
import { CircleAlert, Eye, Receipt, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import { PagerBar } from '../components/PagerBar';
import type { FeeTypeView, InvoiceRow, PriceRow, StatementOwnerRow } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { StatementView } from '../components/StatementView';
import { CategoryBreakdown, MonthlyTrend, OwnerShare } from '../components/CostCharts';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle } from '../components/ui/alert-dialog';
import { Button } from '../components/ui/button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Badge, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { SearchSelect, type Chosen } from '../components/ui/search-select';
import { Select } from '../components/ui/select';
import { Callout, EmptyState, Skeleton } from '../components/ui/states';
import { FilterTabs } from '../components/ui/tabs';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { notify } from '../lib/notify';
import { clubDateTime, clubMonth, formatVnd } from '../lib/utils';

const MONTHS_BACK = 18;

function MonthSelect({ value, onChange }: { value: string; onChange: (m: string) => void }) {
  return (
    <FormField label={vi.billing.month} className="w-44">
      <Select value={value} onChange={(e) => onChange(e.target.value)}>
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
      <DataView load={statement}>{(s) => <StatementView statement={s} />}</DataView>
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
    setErrors({});
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
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </FormField>
          <FormField label={`${a.amount} *`} error={errors.amount}>
            <Input type="number" step="1" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </FormField>
          <FormField label={`${a.reason} *`} error={errors.reason}>
            <Input value={reason} onChange={(e) => setReason(e.target.value)} />
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
  const [adjusting, setAdjusting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const finished = month < clubMonth();
  const anyClosed = (owners.state.data ?? []).some((o) => o.closed);

  async function close() {
    setConfirming(false);
    setError(null);
    try {
      const r = await api.closeMonth(month);
      notify.success(vi.billing.close.done.replace('{month}', r.month).replace('{count}', String(r.invoices)));
      await owners.reload();
      if (selected) await statement.reload();
    } catch (err) {
      setError(messageOf(err));
    }
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
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert">
          {error}
        </Callout>
      )}
      <div className="flex flex-wrap gap-3">
        <Button variant="outline" onClick={() => setAdjusting(true)}>
          {vi.billing.adjust.button}
        </Button>
        <Button disabled={!finished || anyClosed} onClick={() => setConfirming(true)}>
          {vi.billing.close.button}
        </Button>
      </div>
      <DataView
        load={owners}
        isEmpty={(list) => list.length === 0}
        empty={<EmptyState icon={Receipt} title={vi.billing.noOwners} />}
      >
        {(list) => (
          <>
            <div className="grid gap-4 lg:grid-cols-2">
              <MonthlyTrend
                title={vi.billing.charts.trendClub}
                fetchTotal={(m) => api.statementOwners(m).then((rows) => rows.reduce((sum, r) => sum + r.total, 0))}
                deps={[]}
              />
              <OwnerShare rows={list} month={month} />
            </div>
            <DataTable caption={vi.billing.tabs.statements} data={list} columns={columns} rowKey={(o) => o.ownerId} />
          </>
        )}
      </DataView>
      {selected && (
        <Section title={selected.ownerName}>
          <DataView load={statement}>{(s) => <StatementView statement={s} />}</DataView>
        </Section>
      )}
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
    <ul className="m-0 grid list-none gap-0.5 p-0 text-meta">
      {rows.map((r) => (
        <li key={r.id} className="tabular">
          {formatVnd(r.amount)} · {vi.billing.prices.from} {r.effectiveFrom}
        </li>
      ))}
    </ul>
  );
}

type PriceTarget = { kind: 'DAILY_FEE' | 'EXAM_FEE'; feeTypeId: number | null; title: string };

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
    setErrors({});
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
            <Input type="number" step="1" min="0" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </FormField>
          <FormField label={`${p.from} *`} error={errors.effectiveFrom}>
            <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
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
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function addType(e: FormEvent) {
    e.preventDefault();
    setNameError(null);
    setPending(true);
    try {
      await api.createFeeType(name);
      setName('');
      notify.success(vi.common.saved);
      await prices.reload();
    } catch (err) {
      setNameError(messageOf(err));
    } finally {
      setPending(false);
    }
  }

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
          <span className="flex gap-2">
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
            <form onSubmit={addType} noValidate aria-label={p.newType} className="mb-4 flex max-w-xl flex-col gap-3 sm:flex-row sm:items-end">
              <FormField label={p.typeName} error={nameError ?? undefined} className="flex-1">
                <Input value={name} onChange={(e) => setName(e.target.value)} />
              </FormField>
              <Button type="submit" variant="outline" loading={pending}>
                {p.newType}
              </Button>
            </form>
            <DataTable caption={p.examFees} data={list.feeTypes} columns={columns} rowKey={(t) => t.id} />
          </Section>
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
      id: 'view',
      header: vi.billing.view,
      actions: true,
      cell: (i) => (
        <Button size="sm" variant="outline" onClick={() => setSelected(i)} aria-label={`${vi.billing.view} ${i.number}`}>
          {vi.billing.view}
        </Button>
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
      {selected && <DataView load={detail}>{(s) => <StatementView statement={s} />}</DataView>}
    </div>
  );
}

type Tab = 'statements' | 'prices' | 'invoices';

/** The Club Manager's side: statements per owner, the price list, closed invoices. */
function ManagerView() {
  const [tab, setTab] = useState<Tab>('statements');
  const [month, setMonth] = useState(clubMonth(-1));
  return (
    <div className="grid gap-6">
      <FilterTabs<Tab>
        label={vi.billing.tabs.label}
        value={tab}
        onChange={setTab}
        options={[
          { value: 'statements', label: vi.billing.tabs.statements },
          { value: 'prices', label: vi.billing.tabs.prices },
          { value: 'invoices', label: vi.billing.tabs.invoices },
        ]}
      />
      {tab !== 'prices' && <MonthSelect value={month} onChange={setMonth} />}
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
