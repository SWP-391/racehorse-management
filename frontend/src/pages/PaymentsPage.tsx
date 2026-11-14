import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert, Landmark, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import type { TransferRow, UnpaidInvoice } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { SimulatedBadge } from '../components/InvoicePayment';
import { PagerBar } from '../components/PagerBar';
import { Button } from '../components/ui/button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Badge, PageHeader } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { LongText } from '../components/ui/long-text';
import { Select } from '../components/ui/select';
import { Callout, EmptyState } from '../components/ui/states';
import { FilterTabs } from '../components/ui/tabs';
import { useLoad } from '../hooks/useLoad';
import { useTabParam } from '../hooks/useTabParam';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';
import { clubDateTime, formatVnd } from '../lib/utils';

type Tab = 'received' | 'unmatched';
const TABS = ['received', 'unmatched'] as const;

/** Assigns an unmatched transfer to an unpaid invoice; the reason is mandatory and the assignment is audited (BR-222). */
function AssignDialog({ transfer, onClose, onDone }: { transfer: TransferRow | null; onClose: () => void; onDone: () => void }) {
  const t = vi.payment.page;
  const invoices = useLoad(() => api.unpaidInvoices(), [transfer?.id], transfer !== null);
  const [invoiceId, setInvoiceId] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    setInvoiceId('');
    setReason('');
    setErrors({});
    setError(null);
  }, [transfer]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!transfer) return;
    setError(null);
    const local: Record<string, string> = {};
    if (!invoiceId) local.invoiceId = t.chooseRequired;
    if (!reason.trim()) local.reason = t.reasonRequired;
    setErrors(local);
    if (Object.keys(local).length > 0) return;
    setPending(true);
    try {
      await api.assignTransfer(transfer.id, Number(invoiceId), reason);
      notify.success(t.done);
      onDone();
      onClose();
    } catch (err) {
      setErrors(fieldsOf(err));
      setError(messageOf(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={transfer !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-lg">
        <form className="grid gap-4" noValidate onSubmit={(e) => void submit(e)}>
          <DialogHeader>
            <DialogTitle>{t.assignTitle}</DialogTitle>
            <DialogDescription>{transfer ? t.assignText(formatVnd(transfer.amount)) : ''}</DialogDescription>
          </DialogHeader>
          {error && Object.keys(errors).length === 0 && (
            <Callout tone="danger" icon={CircleAlert} role="alert">
              {error}
            </Callout>
          )}
          <FormField label={t.chooseInvoice} error={errors.invoiceId}>
            <DataView load={invoices} isEmpty={(list: UnpaidInvoice[]) => list.length === 0} empty={<p className="text-sm text-muted-foreground">{t.noUnpaid}</p>}>
              {(list: UnpaidInvoice[]) => (
                <Select value={invoiceId} onChange={(e) => setInvoiceId(e.target.value)}>
                  <option value="">—</option>
                  {list.map((i) => (
                    <option key={i.id} value={i.id}>
                      {`${i.number} · ${i.ownerName} · ${formatVnd(i.total)}`}
                    </option>
                  ))}
                </Select>
              )}
            </DataView>
          </FormField>
          <FormField label={t.reasonLabel} error={errors.reason}>
            <Input maxLength={500} autoComplete="off" value={reason} onChange={(e) => setReason(e.target.value)} />
          </FormField>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {vi.common.cancel}
            </Button>
            <Button type="submit" loading={pending}>
              {t.confirm}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function TransferTable({ kind }: { kind: Tab }) {
  const t = vi.payment.page;
  const [page, setPage] = useState(0);
  const [assigning, setAssigning] = useState<TransferRow | null>(null);
  const list = useLoad(() => (kind === 'received' ? api.receivedPayments(page) : api.unmatchedPayments(page)), [kind, page]);
  const columns: DataColumn<TransferRow>[] = [
    { id: 'time', header: t.time, primary: true, cell: (r) => clubDateTime(r.transferredAt ?? r.receivedAt) },
    {
      id: 'amount',
      header: t.amount,
      cell: (r) => (
        <span className="inline-flex flex-wrap items-center gap-2">
          <span className="tabular">{formatVnd(r.amount)}</span>
          {r.simulated && <SimulatedBadge />}
        </span>
      ),
    },
    { id: 'content', header: t.content, cell: (r) => <LongText text={r.content} title={t.contentTitle} subject={formatVnd(r.amount)} limit={32} /> },
  ];
  if (kind === 'received') {
    columns.push(
      { id: 'invoice', header: t.invoice, cell: (r) => r.invoiceNumber ?? '—' },
      { id: 'owner', header: t.owner, cell: (r) => r.ownerName ?? '—' },
    );
  } else {
    columns.push(
      { id: 'reason', header: t.reason, cell: (r) => r.reasonText ?? '—' },
      {
        id: 'state',
        header: t.state,
        cell: (r) => (r.status === 'ASSIGNED' ? <Badge tone="eligible">{t.assigned}</Badge> : <Badge tone="watch">{t.open}</Badge>),
      },
      {
        id: 'assign',
        header: t.assign,
        actions: true,
        cell: (r) =>
          r.status === 'UNMATCHED' ? (
            <Button size="sm" variant="outline" onClick={() => setAssigning(r)} aria-label={`${t.assign} ${formatVnd(r.amount)}`}>
              {t.assign}
            </Button>
          ) : null,
      },
    );
  }
  return (
    <>
      <DataView
        load={list}
        isEmpty={(p) => p.items.length === 0}
        empty={<EmptyState icon={Landmark} title={kind === 'received' ? t.emptyReceived : t.emptyUnmatched} />}
      >
        {(p) => (
          <>
            <DataTable caption={t.tabs[kind]} data={p.items} columns={columns} rowKey={(r) => r.id} />
            <PagerBar page={p.page} size={p.size} total={p.total} onPage={setPage} />
          </>
        )}
      </DataView>
      <AssignDialog transfer={assigning} onClose={() => setAssigning(null)} onDone={() => void list.reload()} />
    </>
  );
}

/** Received payments and unmatched transfers, for the Club Manager only (PERM-060, BR-221, BR-222). */
export default function PaymentsPage() {
  const { can } = useAuth();
  const [tab, setTab] = useTabParam<Tab>(TABS);
  if (!can('MANAGE_PAYMENTS')) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }
  const t = vi.payment.page;
  return (
    <>
      <PageHeader title={t.title} description={t.subtitle} />
      <div className="grid gap-6">
        <FilterTabs<Tab>
          label={t.tabs.label}
          value={tab}
          onChange={setTab}
          options={[
            { value: 'received', label: t.tabs.received },
            { value: 'unmatched', label: t.tabs.unmatched },
          ]}
        />
        <TransferTable key={tab} kind={tab} />
      </div>
    </>
  );
}
