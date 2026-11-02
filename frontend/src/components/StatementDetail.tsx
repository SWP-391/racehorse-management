import { useState } from 'react';
import { Receipt } from 'lucide-react';
import type { Statement } from '../api/types';
import { vi } from '../i18n/vi';
import { formatVnd } from '../lib/utils';
import { DataTable, type DataColumn } from './ui/data-table';
import { FormField } from './ui/form-field';
import { Select } from './ui/select';
import { EmptyState } from './ui/states';

interface Row {
  key: string;
  date: string;
  horse: string;
  horseKey: string;
  category: string;
  description: string;
  reason: string | null;
  quantity: number;
  unitPrice: number;
  amount: number;
}

/**
 * The statement as one flat table, one row per line, filterable by horse and by category (BR-203). The subtotal of a
 * filter is a total the service already computed (a horse group, a category total or a horse's category group): the
 * client sums nothing.
 */
export function StatementDetail({ statement }: { statement: Statement }) {
  const [horse, setHorse] = useState('');
  const [category, setCategory] = useState('');
  const horseKey = (h: { horseId: number | null; horseName: string }) => `${h.horseId ?? 'none'}`;

  const rows: Row[] = statement.horses.flatMap((h) =>
    h.categories.flatMap((c) =>
      c.lines.map((l, i) => ({
        key: `${horseKey(h)}-${c.category}-${i}`,
        date: l.date,
        horse: h.horseName,
        horseKey: horseKey(h),
        category: c.category,
        description: l.description,
        reason: l.reason,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        amount: l.amount,
      })),
    ),
  );
  const shown = rows.filter((r) => (!horse || r.horseKey === horse) && (!category || r.category === category));

  let subtotal = statement.total;
  if (horse && category) {
    subtotal =
      statement.horses.find((h) => horseKey(h) === horse)?.categories.find((c) => c.category === category)?.total ?? 0;
  } else if (horse) {
    subtotal = statement.horses.find((h) => horseKey(h) === horse)?.total ?? 0;
  } else if (category) {
    subtotal = statement.categories.find((c) => c.category === category)?.total ?? 0;
  }

  const columns: DataColumn<Row>[] = [
    { id: 'date', header: vi.billing.date, primary: true, cell: (r) => <span className="tabular">{r.date}</span> },
    { id: 'horse', header: vi.billing.detail.horse, cell: (r) => r.horse },
    { id: 'category', header: vi.billing.detail.category, cell: (r) => vi.billing.categories[r.category] ?? r.category },
    {
      id: 'description',
      header: vi.billing.description,
      cell: (r) => (
        <span>
          {r.description}
          {r.reason ? <span className="block text-meta text-muted-foreground">{r.reason}</span> : null}
        </span>
      ),
    },
    { id: 'quantity', header: vi.billing.quantity, cell: (r) => <span className="tabular">{r.quantity}</span> },
    { id: 'unitPrice', header: vi.billing.unitPrice, cell: (r) => <span className="tabular">{formatVnd(r.unitPrice)}</span> },
    { id: 'amount', header: vi.billing.amount, cell: (r) => <span className="tabular font-medium">{formatVnd(r.amount)}</span> },
  ];

  return (
    <div className="grid gap-4" data-testid="statement-detail">
      <div className="flex flex-wrap items-end gap-4">
        <FormField label={vi.billing.detail.horse} className="w-56">
          <Select value={horse} onChange={(e) => setHorse(e.target.value)}>
            <option value="">{vi.billing.detail.allHorses}</option>
            {statement.horses.map((h) => (
              <option key={horseKey(h)} value={horseKey(h)}>
                {h.horseName}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField label={vi.billing.detail.category} className="w-64">
          <Select value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">{vi.billing.detail.allCategories}</option>
            {statement.categories.map((c) => (
              <option key={c.category} value={c.category}>
                {vi.billing.categories[c.category] ?? c.category}
              </option>
            ))}
          </Select>
        </FormField>
        <p className="tabular ml-auto text-sm">
          {horse || category ? vi.billing.detail.subtotal : vi.billing.total}: <strong>{formatVnd(subtotal)}</strong>
        </p>
      </div>
      {shown.length === 0 ? (
        <EmptyState
          icon={Receipt}
          title={vi.billing.detail.noLines}
          text={`${vi.billing.detail.noLinesHint} ${vi.billing.detail.statementTotal}: ${formatVnd(statement.total)}.`}
        />
      ) : (
        <DataTable caption={vi.billing.tabs.statements} data={shown} columns={columns} rowKey={(r) => r.key} />
      )}
    </div>
  );
}
