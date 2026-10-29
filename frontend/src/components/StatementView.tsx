import { Receipt } from 'lucide-react';
import type { Statement, StatementLine } from '../api/types';
import { vi } from '../i18n/vi';
import { formatVnd } from '../lib/utils';
import { Badge, Section } from './ui/display';
import { DataTable, type DataColumn } from './ui/data-table';
import { EmptyState } from './ui/states';

const columns: DataColumn<StatementLine>[] = [
  { id: 'date', header: vi.billing.date, primary: true, cell: (l) => <span className="tabular">{l.date}</span> },
  {
    id: 'description',
    header: vi.billing.description,
    cell: (l) => (
      <span>
        {l.description}
        {l.reason ? <span className="block text-meta text-muted-foreground">{l.reason}</span> : null}
      </span>
    ),
  },
  { id: 'quantity', header: vi.billing.quantity, cell: (l) => <span className="tabular">{l.quantity}</span> },
  { id: 'unitPrice', header: vi.billing.unitPrice, cell: (l) => <span className="tabular">{formatVnd(l.unitPrice)}</span> },
  { id: 'amount', header: vi.billing.amount, cell: (l) => <span className="tabular font-medium">{formatVnd(l.amount)}</span> },
];

/**
 * A statement or a closed invoice grouped by horse then category, with the totals the service computed (BR-180). The
 * client never sums: what it shows is what the service stored.
 */
export function StatementView({ statement }: { statement: Statement }) {
  const through = statement.dailyFeeThrough;
  return (
    <div className="grid gap-6" data-testid="statement">
      <div className="flex flex-wrap items-center gap-3">
        <Badge tone={statement.closed ? 'eligible' : 'outline'}>
          {statement.closed ? vi.billing.closed : vi.billing.open}
        </Badge>
        {statement.invoiceNumber && (
          <span className="tabular text-sm text-muted-foreground">
            {vi.billing.invoice} {statement.invoiceNumber}
          </span>
        )}
        <span className="tabular ml-auto font-display text-h2" aria-label={vi.billing.total}>
          {vi.billing.total}: {formatVnd(statement.total)}
        </span>
      </div>
      {!statement.closed && (
        <p className="text-meta text-muted-foreground">{vi.billing.feeThrough.replace('{date}', through)}</p>
      )}
      {statement.horses.length === 0 ? (
        <EmptyState icon={Receipt} title={vi.billing.empty} text={vi.billing.emptyHint} />
      ) : (
        statement.horses.map((h) => (
          <Section
            key={`${h.horseId ?? 'none'}-${h.horseName}`}
            title={h.horseName}
            actions={
              <span className="tabular text-sm">
                {vi.billing.horseTotal}: <strong>{formatVnd(h.total)}</strong>
              </span>
            }
          >
            <div className="grid gap-5">
              {h.categories.map((c) => (
                <div key={c.category} className="grid gap-2">
                  <h3 className="flex items-baseline justify-between text-sm font-semibold">
                    <span>{vi.billing.categories[c.category] ?? c.category}</span>
                    <span className="tabular">{formatVnd(c.total)}</span>
                  </h3>
                  <DataTable
                    caption={`${h.horseName}: ${vi.billing.categories[c.category] ?? c.category}`}
                    data={c.lines}
                    columns={columns}
                    // Two charges can share date, description and amount (two examinations of one fee type in a day): the position keeps keys unique.
                    rowKey={(l) => `${c.lines.indexOf(l)}-${l.date}-${l.description}-${l.amount}-${l.reason ?? ''}`}
                  />
                </div>
              ))}
            </div>
          </Section>
        ))
      )}
    </div>
  );
}
