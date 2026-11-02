import { Link, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Printer } from 'lucide-react';
import { api } from '../api/api';
import { Button } from '../components/ui/button';
import { DataView } from '../components/ui/data-view';
import { useLoad } from '../hooks/useLoad';
import { vi } from '../i18n/vi';
import { clubDate, clubMonth, formatVnd } from '../lib/utils';

/**
 * The printable invoice or provisional statement (D5): a plain page with the club heading, number, owner, month,
 * closing date, lines grouped by horse and the total, with `@media print` styles that hide the menu and fit A4. The
 * browser prints it or saves it as PDF. It loads the same endpoints as the screens, so it shows only what the person may see.
 * `/billing/print/invoice/:id` prints a closed invoice, `/billing/print/statement/:ownerId?month=` an owner's month.
 */
export default function BillingPrintPage() {
  const { kind, id } = useParams();
  const [params] = useSearchParams();
  const month = params.get('month') ?? clubMonth(-1);
  const statement = useLoad(
    () => (kind === 'invoice' ? api.invoice(Number(id)) : api.ownerStatement(Number(id), month)),
    [kind, id, month],
  );
  return (
    <div className="print-sheet mx-auto grid max-w-3xl gap-6">
      <style>{`
        @page { size: A4; margin: 16mm; }
        @media print {
          aside, nav, header[role="banner"], [data-print-hide], [data-page-header] { display: none !important; }
          body, main { background: #fff !important; color: #000 !important; }
          .print-sheet { max-width: none !important; margin: 0 !important; }
          .print-sheet table { break-inside: auto; }
          .print-sheet tr { break-inside: avoid; }
        }
      `}</style>
      <div className="flex items-center justify-between gap-3" data-print-hide>
        <Button asChild variant="outline" size="sm">
          <Link to="/billing">
            <ArrowLeft aria-hidden />
            {vi.billing.back}
          </Link>
        </Button>
        <Button size="sm" onClick={() => window.print()}>
          <Printer aria-hidden />
          {vi.billing.print}
        </Button>
      </div>
      <DataView load={statement}>
        {(s) => (
          <article className="grid gap-5" aria-label={vi.billing.printView}>
            <header className="grid gap-1 border-b pb-4">
              <p className="text-sm text-muted-foreground">
                {vi.shell.wordmark} · {vi.shell.club}
              </p>
              <h1 className="text-h1">{s.closed && s.invoiceNumber ? `${vi.billing.printHeading} ${s.invoiceNumber}` : vi.billing.printStatementHeading}</h1>
              {!s.closed && <p className="font-display text-h2 tracking-wide">{vi.billing.provisional}</p>}
              <dl className="mt-2 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
                <dt className="text-muted-foreground">{vi.billing.owner}</dt>
                <dd>{s.ownerName}</dd>
                <dt className="text-muted-foreground">{vi.billing.month}</dt>
                <dd className="tabular">{s.month}</dd>
                {s.closedAt && (
                  <>
                    <dt className="text-muted-foreground">{vi.billing.closedOn}</dt>
                    <dd className="tabular">{clubDate(s.closedAt)}</dd>
                  </>
                )}
              </dl>
            </header>
            {s.horses.map((h) => (
              <section key={`${h.horseId ?? 'none'}-${h.horseName}`} className="grid gap-2">
                <h2 className="flex items-baseline justify-between text-lg font-semibold">
                  <span>{h.horseName}</span>
                  <span className="tabular">{formatVnd(h.total)}</span>
                </h2>
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-1 pr-2 font-medium">{vi.billing.date}</th>
                      <th className="py-1 pr-2 font-medium">{vi.billing.description}</th>
                      <th className="py-1 pr-2 text-right font-medium">{vi.billing.quantity}</th>
                      <th className="py-1 pr-2 text-right font-medium">{vi.billing.unitPrice}</th>
                      <th className="py-1 text-right font-medium">{vi.billing.amount}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {h.categories.flatMap((c) =>
                      c.lines.map((l, i) => (
                        <tr key={`${c.category}-${i}`} className="border-b align-top">
                          <td className="tabular py-1 pr-2">{l.date}</td>
                          <td className="py-1 pr-2">
                            {l.description}
                            <span className="block text-meta text-muted-foreground">{vi.billing.categories[c.category] ?? c.category}{l.reason ? ` · ${l.reason}` : ''}</span>
                          </td>
                          <td className="tabular py-1 pr-2 text-right">{l.quantity}</td>
                          <td className="tabular py-1 pr-2 text-right">{formatVnd(l.unitPrice)}</td>
                          <td className="tabular py-1 text-right">{formatVnd(l.amount)}</td>
                        </tr>
                      )),
                    )}
                  </tbody>
                </table>
              </section>
            ))}
            <p className="tabular border-t pt-3 text-right font-display text-h2">
              {vi.billing.total}: {formatVnd(s.total)}
            </p>
          </article>
        )}
      </DataView>
    </div>
  );
}
