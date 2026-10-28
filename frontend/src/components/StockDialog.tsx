import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { CircleAlert } from 'lucide-react';
import { api } from '../api/api';
import type { ExpectedReceipt, Page, StockMovement, SupplyItem } from '../api/types';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';
import { clubDateTime } from '../lib/utils';
import { fieldsOf, messageOf } from './ErrorText';
import { Button } from './ui/button';
import { DataTable, type DataColumn } from './ui/data-table';
import { PagerBar } from './PagerBar';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from './ui/dialog';
import { FormField } from './ui/form-field';
import { Input } from './ui/input';
import { Select } from './ui/select';
import { Callout } from './ui/states';

type Tab = 'receipt' | 'adjust' | 'price' | 'expected' | 'history';
const TABS: Tab[] = ['receipt', 'adjust', 'price', 'expected', 'history'];

const isWhole = (v: string, min: number) => /^\d+$/.test(v.trim()) && Number(v) >= min;
const money = (n: number) => new Intl.NumberFormat('vi-VN').format(n);

/**
 * The Club Manager's stock actions for one supply item (PERM-049): receive a delivery, adjust to a counted quantity
 * with a reason, set the price, record or cancel an expected delivery, and read the movements. The quantity is never
 * typed into the item itself, only moved by these actions (BR-171, BR-177).
 */
export function StockDialog({ item, onClose, onChanged }: { item: SupplyItem | null; onClose: () => void; onChanged: () => void }) {
  const s = vi.supplies.stock;
  const [tab, setTab] = useState<Tab>('receipt');
  const [quantity, setQuantity] = useState('');
  const [note, setNote] = useState('');
  const [price, setPrice] = useState('');
  const [date, setDate] = useState('');
  const [expectedId, setExpectedId] = useState<number | null>(null);
  const [expected, setExpected] = useState<ExpectedReceipt[]>([]);
  const [movements, setMovements] = useState<Page<StockMovement> | null>(null);
  const [movementPage, setMovementPage] = useState(0);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const loadExpected = useCallback(async (id: number) => {
    setExpected(await api.expectedReceipts(id));
  }, []);

  // A reload of the list after each action gives the dialog a fresh item: only another item resets the form and the tab.
  const itemId = item?.id;
  useEffect(() => {
    if (itemId === undefined) return;
    setTab('receipt');
    setQuantity('');
    setNote('');
    setDate('');
    setExpectedId(null);
    setFieldErrors({});
    setError(null);
    setMovements(null);
    setMovementPage(0);
    loadExpected(itemId).catch(() => setExpected([]));
  }, [itemId, loadExpected]);

  const unitPrice = item?.unitPrice;
  useEffect(() => {
    setPrice(String(unitPrice ?? ''));
  }, [itemId, unitPrice]);

  useEffect(() => {
    if (item && tab === 'history') {
      api.stockMovements(item.id, movementPage).then(setMovements).catch((e) => setError(messageOf(e)));
    }
  }, [item, tab, movementPage]);

  async function run(errors: Record<string, string>, write: () => Promise<unknown>, done?: () => void) {
    setFieldErrors(errors);
    setError(null);
    if (Object.keys(errors).length > 0 || !item) return;
    setPending(true);
    try {
      await write();
      notify.success(vi.common.saved);
      done?.();
      onChanged();
      await loadExpected(item.id);
      if (tab === 'history') {
        setMovementPage(0);
        setMovements(await api.stockMovements(item.id, 0));
      }
    } catch (e) {
      const fields = fieldsOf(e);
      setFieldErrors(fields);
      if (Object.keys(fields).length === 0) setError(messageOf(e));
    } finally {
      setPending(false);
    }
  }

  function choose(next: Tab) {
    setTab(next);
    setFieldErrors({});
    setError(null);
  }

  function receive(e: FormEvent) {
    e.preventDefault();
    const errors: Record<string, string> = {};
    if (!isWhole(quantity, 1)) errors.quantity = s.receiptQuantityInvalid;
    void run(errors, () => api.receiveStock(item!.id, Number(quantity), note.trim(), expectedId ?? undefined), () => {
      setQuantity('');
      setNote('');
      setExpectedId(null);
    });
  }

  function adjust(e: FormEvent) {
    e.preventDefault();
    const errors: Record<string, string> = {};
    if (!isWhole(quantity, 0)) errors.newQuantity = s.adjustQuantityInvalid;
    if (note.trim() === '') errors.reason = s.reasonRequired;
    void run(errors, () => api.adjustStock(item!.id, Number(quantity), note.trim()), () => {
      setQuantity('');
      setNote('');
    });
  }

  function savePrice(e: FormEvent) {
    e.preventDefault();
    const errors: Record<string, string> = {};
    if (price.trim() === '' || Number.isNaN(Number(price)) || Number(price) < 0) errors.price = s.priceInvalid;
    void run(errors, () => api.setSupplyPrice(item!.id, Number(price)));
  }

  function expect(e: FormEvent) {
    e.preventDefault();
    const errors: Record<string, string> = {};
    if (!date) errors.expectedDate = s.dateRequired;
    if (!isWhole(quantity, 1)) errors.quantity = s.receiptQuantityInvalid;
    void run(errors, () => api.expectReceipt(item!.id, date, Number(quantity)), () => {
      setDate('');
      setQuantity('');
    });
  }

  const columns: DataColumn<StockMovement>[] = [
    { id: 'at', header: s.when, primary: true, cell: (m) => <span className="tabular">{clubDateTime(m.createdAt)}</span> },
    { id: 'kind', header: s.kind, cell: (m) => s.kinds[m.kind] ?? m.kind },
    { id: 'delta', header: s.delta, cell: (m) => <span className="tabular">{m.delta > 0 ? `+${m.delta}` : m.delta}</span> },
    { id: 'balance', header: s.balance, cell: (m) => <span className="tabular">{m.balanceAfter}</span> },
    { id: 'price', header: s.unitPrice, cell: (m) => <span className="tabular">{money(m.unitPrice)}</span> },
    { id: 'by', header: s.by, cell: (m) => m.actorName ?? vi.common.none },
    { id: 'reason', header: s.reason, cell: (m) => m.reason ?? vi.common.none },
  ];

  const errorCallout = error && (
    <Callout tone="danger" icon={CircleAlert} role="alert">
      {error}
    </Callout>
  );

  return (
    <Dialog open={item !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-3xl">
        {item && (
          <div className="grid gap-4">
            <DialogHeader>
              <DialogTitle>{s.title(item.name)}</DialogTitle>
              <DialogDescription>{s.description(item.quantity ?? 0, item.unit)}</DialogDescription>
            </DialogHeader>
            <div role="group" aria-label={s.tabsLabel} className="flex flex-wrap gap-2">
              {TABS.map((t) => (
                <Button key={t} variant={tab === t ? 'default' : 'outline'} size="sm" aria-pressed={tab === t} onClick={() => choose(t)}>
                  {s.tabs[t]}
                </Button>
              ))}
            </div>
            {errorCallout}

            {tab === 'receipt' && (
              <form className="grid gap-4" noValidate onSubmit={receive}>
                <FormField label={s.receiptQuantity(item.unit)} error={fieldErrors.quantity} className="sm:max-w-64">
                  <Input type="number" inputMode="numeric" min={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
                </FormField>
                {expected.length > 0 && (
                  <FormField label={s.fulfils} description={s.fulfilsHint}>
                    <Select
                      value={expectedId ?? ''}
                      onChange={(e) => setExpectedId(e.target.value === '' ? null : Number(e.target.value))}
                    >
                      <option value="">{s.noExpected}</option>
                      {expected.map((x) => (
                        <option key={x.id} value={x.id}>
                          {s.expectedLabel(x.expectedDate, x.quantity, item.unit)}
                        </option>
                      ))}
                    </Select>
                  </FormField>
                )}
                <FormField label={s.note} error={fieldErrors.note}>
                  <Input value={note} onChange={(e) => setNote(e.target.value)} autoComplete="off" />
                </FormField>
                <div>
                  <Button type="submit" loading={pending}>
                    {s.receive}
                  </Button>
                </div>
              </form>
            )}

            {tab === 'adjust' && (
              <form className="grid gap-4" noValidate onSubmit={adjust}>
                <FormField label={s.adjustQuantity(item.unit)} description={s.adjustHint} error={fieldErrors.newQuantity} className="sm:max-w-64">
                  <Input type="number" inputMode="numeric" min={0} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
                </FormField>
                <FormField label={s.reason} error={fieldErrors.reason}>
                  <Input value={note} onChange={(e) => setNote(e.target.value)} autoComplete="off" />
                </FormField>
                <div>
                  <Button type="submit" loading={pending}>
                    {s.adjust}
                  </Button>
                </div>
              </form>
            )}

            {tab === 'price' && (
              <form className="grid gap-4" noValidate onSubmit={savePrice}>
                <FormField label={s.price} description={s.priceHint} error={fieldErrors.price} className="sm:max-w-64">
                  <Input type="number" inputMode="decimal" min={0} step="any" value={price} onChange={(e) => setPrice(e.target.value)} />
                </FormField>
                <div>
                  <Button type="submit" loading={pending}>
                    {s.setPrice}
                  </Button>
                </div>
              </form>
            )}

            {tab === 'expected' && (
              <div className="grid gap-4">
                <form className="grid gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end" noValidate onSubmit={expect}>
                  <FormField label={s.expectedDate} error={fieldErrors.expectedDate}>
                    <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
                  </FormField>
                  <FormField label={s.receiptQuantity(item.unit)} error={fieldErrors.quantity}>
                    <Input type="number" inputMode="numeric" min={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
                  </FormField>
                  <Button type="submit" loading={pending}>
                    {s.addExpected}
                  </Button>
                </form>
                {expected.length === 0 ? (
                  <p className="text-meta text-muted-foreground">{s.noExpectedYet}</p>
                ) : (
                  <ul className="grid gap-2">
                    {expected.map((x) => (
                      <li key={x.id} className="flex items-center justify-between gap-3 rounded-md border border-border px-3 py-2">
                        <span className="tabular">{s.expectedLabel(x.expectedDate, x.quantity, item.unit)}</span>
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={s.cancelExpectedOf(x.expectedDate)}
                          disabled={pending}
                          onClick={() => void run({}, () => api.cancelExpectedReceipt(item.id, x.id))}
                        >
                          {s.cancelExpected}
                        </Button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}

            {tab === 'history' &&
              (movements === null ? (
                <p className="text-meta text-muted-foreground">{vi.common.loading}</p>
              ) : movements.items.length === 0 ? (
                <p className="text-meta text-muted-foreground">{s.noMovements}</p>
              ) : (
                <>
                  <DataTable caption={s.history} data={movements.items} columns={columns} rowKey={(m) => m.id} />
                  <PagerBar page={movements.page} size={movements.size} total={movements.total} onPage={setMovementPage} />
                </>
              ))}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
