import { useEffect, useRef, useState } from 'react';
import { Copy, Landmark } from 'lucide-react';
import { api } from '../api/api';
import type { PaymentView } from '../api/types';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';
import { clubDateTime, formatVnd } from '../lib/utils';
import { messageOf } from './ErrorText';
import { Button } from './ui/button';
import { DataView } from './ui/data-view';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from './ui/dialog';
import { Badge } from './ui/display';
import { Callout, Skeleton } from './ui/states';
import { useLoad } from '../hooks/useLoad';

const POLL_MS = 5000;

/** "mô phỏng" next to a payment that came from the test mode of the service (BR-226). */
export function SimulatedBadge() {
  return <Badge tone="simulated">{vi.payment.simulated}</Badge>;
}

function CopyRow({ label, value }: { label: string; value: string }) {
  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      notify.success(vi.payment.copied);
    } catch {
      /* the value stays on screen to be typed */
    }
  }
  return (
    <div className="flex items-center justify-between gap-3 border-b border-border/70 py-2 last:border-b-0">
      <div className="min-w-0">
        <div className="text-meta text-muted-foreground">{label}</div>
        <div className="tabular break-words text-sm font-medium">{value}</div>
      </div>
      <Button type="button" size="icon" variant="ghost" onClick={() => void copy()} aria-label={`${vi.payment.copy} ${label}`}>
        <Copy aria-hidden />
      </Button>
    </div>
  );
}

/** The QR, the transfer details and the wait for the money, for one unpaid invoice (BR-218, BR-219, BR-223). */
function PayDialog({ invoiceId, number, open, onClose, onPaid }: { invoiceId: number; number: string; open: boolean; onClose: () => void; onPaid: () => void }) {
  const [view, setView] = useState<PaymentView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [qrFailed, setQrFailed] = useState(false);
  const paidSent = useRef(false);

  useEffect(() => {
    if (!open) return;
    let alive = true;
    paidSent.current = false;
    setView(null);
    setError(null);
    setQrFailed(false);
    api
      .openPayment(invoiceId)
      .then((v) => alive && setView(v))
      .catch((e) => alive && setError(messageOf(e)));
    return () => {
      alive = false;
    };
  }, [open, invoiceId]);

  const waiting = open && view !== null && view.status === 'UNPAID';
  useEffect(() => {
    if (!waiting) return;
    const timer = setInterval(() => {
      api
        .paymentStatus(invoiceId)
        .then((v) => {
          if (v.status === 'PAID') setView(v);
        })
        .catch(() => undefined);
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [waiting, invoiceId]);

  useEffect(() => {
    if (view?.status === 'PAID' && !paidSent.current) {
      paidSent.current = true;
      onPaid();
    }
  }, [view, onPaid]);

  const t = vi.payment;
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-md overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t.title(number)}</DialogTitle>
          <DialogDescription>{t.intro}</DialogDescription>
        </DialogHeader>
        {error && (
          <Callout tone="danger" role="alert">
            {error}
          </Callout>
        )}
        {!view && !error && <Skeleton className="h-72" />}
        {view?.status === 'PAID' && (
          <Callout tone="success" role="status">
            {t.paidDone} {view.paidAt ? t.paidAt(clubDateTime(view.paidAt)) : ''}
          </Callout>
        )}
        {view?.status === 'UNPAID' && view.transfer && (
          <div className="grid gap-4">
            {view.simulated && <Callout tone="info">{t.testMode}</Callout>}
            <div className="flex justify-center">
              {view.qrUrl && !qrFailed ? (
                <img src={view.qrUrl} alt={t.qrAlt} className="h-64 w-64 rounded-md border bg-white object-contain" onError={() => setQrFailed(true)} />
              ) : (
                <p className="text-sm text-muted-foreground">{t.qrFailed}</p>
              )}
            </div>
            <div>
              <CopyRow label={t.bank} value={view.transfer.bankCode} />
              <CopyRow label={t.account} value={view.transfer.accountNumber} />
              {view.transfer.accountName && <CopyRow label={t.accountName} value={view.transfer.accountName} />}
              <CopyRow label={t.amount} value={formatVnd(view.transfer.amount)} />
              <CopyRow label={t.content} value={view.transfer.content} />
            </div>
            <p className="text-sm text-muted-foreground">{t.exact}</p>
            <p className="text-sm text-muted-foreground">{t.cash}</p>
            <p className="text-sm font-medium" role="status">
              {t.waiting}
            </p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

/**
 * The payment of one closed invoice of the signed-in owner: its status, and a button that opens the QR while it is
 * unpaid (BR-218). A month that is still open has no invoice, so the page does not render this for it (AC-481).
 */
export function InvoicePayment({ invoiceId, number }: { invoiceId: number; number: string }) {
  const status = useLoad(() => api.paymentStatus(invoiceId), [invoiceId]);
  const [open, setOpen] = useState(false);
  const reload = status.reload;
  return (
    <div className="flex flex-wrap items-center gap-3">
      <DataView load={status} skeleton={<Skeleton className="h-9 w-40" />}>
        {(p) =>
          p.status === 'PAID' ? (
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="eligible">{vi.payment.paid}</Badge>
              {p.simulated && <SimulatedBadge />}
              {p.paidAt && <span className="text-sm text-muted-foreground">{vi.payment.paidAt(clubDateTime(p.paidAt))}</span>}
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="watch">{vi.payment.unpaid}</Badge>
              <Button size="sm" onClick={() => setOpen(true)}>
                <Landmark aria-hidden />
                {vi.payment.pay}
              </Button>
            </div>
          )
        }
      </DataView>
      <PayDialog invoiceId={invoiceId} number={number} open={open} onClose={() => setOpen(false)} onPaid={() => void reload()} />
    </div>
  );
}
