import { useState, type FormEvent } from 'react';
import { CircleAlert, CircleCheck, CircleOff, CircleX, Clock, Package, PencilLine, Plus, RotateCcw, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import {
  SUPPLY_CATEGORIES,
  type ProposalState,
  type RestockProposal,
  type SupplyCategory,
  type SupplyItem,
} from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { vi } from '../i18n/vi';
import { usePagedList } from '../hooks/usePagedList';
import { notify } from '../lib/notify';
import { Button } from '../components/ui/button';
import { ConfirmButton } from '../components/ui/confirm-button';
import { LongText } from '../components/ui/long-text';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Badge, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { Select } from '../components/ui/select';
import { Callout, EmptyState } from '../components/ui/states';
import { clubDateTime } from '../lib/utils';
import { StockDialog } from '../components/StockDialog';

type ItemForm = {
  id: number | null;
  name: string;
  category: SupplyCategory | '';
  unit: string;
  price: string;
  openingQuantity: string;
  active: boolean;
};
const NEW_ITEM: ItemForm = { id: null, name: '', category: '', unit: '', price: '', openingQuantity: '', active: true };

const isWhole = (v: string, min: number) => /^\d+$/.test(v.trim()) && Number(v) >= min;
const money = (n: number) => new Intl.NumberFormat('vi-VN').format(n);

/** Cần nhập and Hết hàng carry an icon and text, not colour alone (BR-175). */
function StockBadge({ state }: { state: NonNullable<SupplyItem['state']> }) {
  const label = vi.supplies.stockStates[state];
  if (state === 'OUT') return <Badge tone="injured" icon={<CircleX aria-hidden />}>{label}</Badge>;
  if (state === 'RESTOCK') return <Badge tone="watch" icon={<CircleAlert aria-hidden />}>{label}</Badge>;
  return <Badge tone="eligible" icon={<CircleCheck aria-hidden />}>{label}</Badge>;
}

function StateBadge({ state }: { state: ProposalState }) {
  const label = vi.supplies.states[state] ?? state;
  if (state === 'APPROVED') return <Badge tone="eligible" icon={<CircleCheck aria-hidden />}>{label}</Badge>;
  if (state === 'REJECTED') return <Badge tone="injured" icon={<CircleX aria-hidden />}>{label}</Badge>;
  return <Badge tone="watch" icon={<Clock aria-hidden />}>{label}</Badge>;
}

function ActiveBadge({ active }: { active: boolean }) {
  return active ? (
    <Badge tone="eligible" icon={<CircleCheck aria-hidden />}>{vi.supplies.active}</Badge>
  ) : (
    <Badge tone="neutral" icon={<CircleOff aria-hidden />}>{vi.supplies.inactive}</Badge>
  );
}

/**
 * Supplies (BR-130 to BR-132). Club Manager: catalog (add, edit, deactivate) and pending proposals (approve, or reject
 * with a reason). Groom: active items with "Đề xuất bổ sung" and their own proposals with the state and reject reason.
 */
export default function SuppliesPage() {
  const { can } = useAuth();
  const allowed = can('VIEW_SUPPLIES');
  const manage = can('MANAGE_SUPPLIES');
  const propose = can('PROPOSE_RESTOCK');
  const decide = can('DECIDE_RESTOCK');

  const items = usePagedList((query) => api.supplies(query), { filters: ['category'], enabled: allowed });
  // The pending list of the Club Manager and the Groom's own list are paged apart from the catalog (prefixes p and m).
  const pending = usePagedList((query) => api.proposals(query), { prefix: 'p', fixed: { state: 'PENDING' }, enabled: allowed && decide });
  const proposals = usePagedList((query) => api.proposals(query), { prefix: 'm', enabled: allowed && propose });

  const [item, setItem] = useState<ItemForm | null>(null);
  const [proposing, setProposing] = useState<SupplyItem | null>(null);
  const [stockingId, setStockingId] = useState<number | null>(null);
  // The dialog reads the item from the reloaded list, so its stock and price stay current after each action.
  const stocking = stockingId === null ? null : (items.load.state.data?.items.find((i) => i.id === stockingId) ?? null);
  const [quantity, setQuantity] = useState('');
  const [note, setNote] = useState('');
  const [rejecting, setRejecting] = useState<RestockProposal | null>(null);
  const [reason, setReason] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  function resetErrors() {
    setFieldErrors({});
    setFormError(null);
  }

  /** Runs a dialog's write: field errors beside the field, any other refusal inline, success toast and reload. */
  async function submit(errors: Record<string, string>, write: () => Promise<unknown>, done: () => void) {
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) return;
    setSaving(true);
    try {
      await write();
      notify.success(vi.common.saved);
      done();
      await Promise.all([items.load.reload(), decide ? pending.load.reload() : undefined, propose ? proposals.load.reload() : undefined]);
    } catch (e) {
      const fields = fieldsOf(e);
      setFieldErrors(fields);
      if (Object.keys(fields).length === 0) setFormError(messageOf(e));
    } finally {
      setSaving(false);
    }
  }

  function saveItem(e: FormEvent) {
    e.preventDefault();
    if (!item) return;
    const errors: Record<string, string> = {};
    if (item.name.trim() === '') errors.name = vi.supplies.nameRequired;
    if (item.category === '') errors.category = vi.supplies.categoryRequired;
    if (item.unit.trim() === '') errors.unit = vi.supplies.unitRequired;
    if (item.id === null) {
      if (item.price.trim() === '' || Number.isNaN(Number(item.price)) || Number(item.price) < 0) errors.price = vi.supplies.priceRequired;
      if (item.openingQuantity.trim() !== '' && !isWhole(item.openingQuantity, 0)) errors.openingQuantity = vi.supplies.quantityInvalid;
    }
    const input = {
      name: item.name.trim(),
      category: item.category,
      unit: item.unit.trim(),
      active: item.active,
      ...(item.id === null
        ? { price: Number(item.price), openingQuantity: item.openingQuantity.trim() === '' ? 0 : Number(item.openingQuantity) }
        : {}),
    };
    void submit(
      errors,
      () => (item.id === null ? api.createSupply(input) : api.updateSupply(item.id, input)),
      () => setItem(null),
    );
  }

  function saveProposal(e: FormEvent) {
    e.preventDefault();
    if (!proposing) return;
    const errors: Record<string, string> = {};
    if (!isWhole(quantity, 1)) errors.quantity = vi.supplies.proposeQuantityInvalid;
    void submit(
      errors,
      () => api.proposeRestock(proposing.id, Number(quantity), note.trim()),
      () => setProposing(null),
    );
  }

  function saveRejection(e: FormEvent) {
    e.preventDefault();
    if (!rejecting) return;
    const errors: Record<string, string> = {};
    if (reason.trim() === '') errors.reason = vi.supplies.reasonRequired;
    void submit(
      errors,
      () => api.rejectProposal(rejecting.id, reason.trim()),
      () => setRejecting(null),
    );
  }

  async function act(key: string, write: () => Promise<unknown>) {
    setBusy(key);
    try {
      await write();
      notify.success(vi.common.saved);
    } catch (e) {
      notify.error(e);
    } finally {
      await Promise.all([items.load.reload(), decide ? pending.load.reload() : undefined, propose ? proposals.load.reload() : undefined]);
      setBusy(null);
    }
  }

  const toggleActive = (i: SupplyItem) =>
    act(`item:${i.id}`, () =>
      api.updateSupply(i.id, { name: i.name, category: i.category, unit: i.unit, active: !i.active }),
    );

  const nameCell = (name: string) => <span className="font-display text-[1.0625rem] font-medium text-foreground">{name}</span>;
  const qty = (n: number, unit: string) => (
    <span className="tabular">
      {n} {unit}
    </span>
  );

  const catalogColumns: DataColumn<SupplyItem>[] = [
    { id: 'name', header: vi.supplies.name, primary: true, sortValue: (i) => i.name, cell: (i) => nameCell(i.name) },
    { id: 'category', header: vi.supplies.category, cell: (i) => vi.supplies.categories[i.category] },
    { id: 'quantity', header: vi.supplies.quantity, cell: (i) => qty(i.quantity ?? 0, i.unit) },
    { id: 'price', header: vi.supplies.price, cell: (i) => <span className="tabular">{money(i.unitPrice ?? 0)}</span> },
    {
      id: 'cover',
      header: vi.supplies.daysOfCover,
      cell: (i) => (
        <span className="grid gap-1">
          <span className="tabular">{i.daysOfCover === undefined ? vi.supplies.noDailyUse : vi.supplies.daysOfCoverValue(i.daysOfCover)}</span>
          {i.state && <StockBadge state={i.state} />}
        </span>
      ),
    },
    { id: 'status', header: vi.supplies.status, cell: (i) => <ActiveBadge active={i.active} /> },
    {
      id: 'actions',
      header: vi.supplies.actions,
      actions: true,
      cell: (i) => (
        <>
          <Button
            variant="outline"
            size="sm"
            aria-label={vi.supplies.stock.openOf(i.name)}
            onClick={() => setStockingId(i.id)}
          >
            {vi.supplies.stock.open}
          </Button>
          <Button
            variant="outline"
            size="sm"
            aria-label={vi.supplies.editOf(i.name)}
            onClick={() => {
              resetErrors();
              setItem({ id: i.id, name: i.name, category: i.category, unit: i.unit, price: '', openingQuantity: '', active: i.active });
            }}
          >
            <PencilLine aria-hidden />
            {vi.supplies.edit}
          </Button>
          <ConfirmButton
            destructive={i.active}
            aria-label={i.active ? vi.supplies.deactivateOf(i.name) : vi.supplies.reactivateOf(i.name)}
            title={i.active ? vi.supplies.deactivateOf(i.name) : vi.supplies.reactivateOf(i.name)}
            description={i.active ? vi.supplies.deactivateText : vi.supplies.reactivateText}
            confirmLabel={i.active ? vi.supplies.deactivate : vi.supplies.reactivate}
            loading={busy === `item:${i.id}`}
            disabled={busy !== null}
            onConfirm={() => toggleActive(i)}
          >
            {i.active ? <CircleOff aria-hidden /> : <RotateCcw aria-hidden />}
            {i.active ? vi.supplies.deactivate : vi.supplies.reactivate}
          </ConfirmButton>
        </>
      ),
    },
  ];

  const groomItemColumns: DataColumn<SupplyItem>[] = [
    { id: 'name', header: vi.supplies.name, primary: true, sortValue: (i) => i.name, cell: (i) => nameCell(i.name) },
    { id: 'category', header: vi.supplies.category, cell: (i) => vi.supplies.categories[i.category] },
    { id: 'unit', header: vi.supplies.unit, cell: (i) => i.unit },
    ...(propose
      ? [
          {
            id: 'actions',
            header: vi.supplies.actions,
            actions: true,
            cell: (i: SupplyItem) => (
              <Button
                variant="outline"
                size="sm"
                aria-label={vi.supplies.proposeOf(i.name)}
                onClick={() => {
                  resetErrors();
                  setQuantity('');
                  setNote('');
                  setProposing(i);
                }}
              >
                {vi.supplies.propose}
              </Button>
            ),
          } satisfies DataColumn<SupplyItem>,
        ]
      : []),
  ];

  const pendingColumns: DataColumn<RestockProposal>[] = [
    { id: 'item', header: vi.supplies.item, primary: true, cell: (p) => nameCell(p.itemName) },
    { id: 'by', header: vi.supplies.proposedBy, cell: (p) => p.groomName },
    { id: 'quantity', header: vi.supplies.proposedQuantity, cell: (p) => qty(p.quantity, p.unit) },
    { id: 'note', header: vi.supplies.noteColumn, cell: (p) => (p.note ? <LongText text={p.note} title={vi.supplies.noteColumn} subject={p.itemName} /> : vi.common.none) },
    { id: 'at', header: vi.supplies.proposedAt, cell: (p) => <span className="tabular">{clubDateTime(p.createdAt)}</span> },
    {
      id: 'actions',
      header: vi.supplies.actions,
      actions: true,
      cell: (p) => (
        <>
          <ConfirmButton
            aria-label={vi.supplies.approveOf(p.itemName)}
            title={vi.supplies.approveOf(p.itemName)}
            description={vi.supplies.approveText}
            confirmLabel={vi.supplies.approve}
            loading={busy === `approve:${p.id}`}
            disabled={busy !== null}
            onConfirm={() => act(`approve:${p.id}`, () => api.approveProposal(p.id))}
          >
            <CircleCheck aria-hidden />
            {vi.supplies.approve}
          </ConfirmButton>
          <Button
            variant="destructive-outline"
            size="sm"
            aria-label={vi.supplies.rejectOf(p.itemName)}
            onClick={() => {
              resetErrors();
              setReason('');
              setRejecting(p);
            }}
          >
            <CircleX aria-hidden />
            {vi.supplies.reject}
          </Button>
        </>
      ),
    },
  ];

  const mineColumns: DataColumn<RestockProposal>[] = [
    { id: 'item', header: vi.supplies.item, primary: true, cell: (p) => nameCell(p.itemName) },
    { id: 'quantity', header: vi.supplies.proposedQuantity, cell: (p) => qty(p.quantity, p.unit) },
    { id: 'at', header: vi.supplies.proposedAt, cell: (p) => <span className="tabular">{clubDateTime(p.createdAt)}</span> },
    {
      id: 'state',
      header: vi.supplies.state,
      cell: (p) => (
        <span className="grid gap-1">
          <StateBadge state={p.state} />
          {p.state === 'REJECTED' && p.decisionReason && (
            <span className="text-meta text-muted-foreground">{vi.supplies.reasonLabel(p.decisionReason)}</span>
          )}
          {p.state !== 'PENDING' && p.decidedByName && (
            <span className="text-meta text-muted-foreground">{vi.supplies.decidedBy(p.decidedByName, clubDateTime(p.decidedAt))}</span>
          )}
        </span>
      ),
    },
  ];

  const errorCallout = formError && (
    <Callout tone="danger" icon={CircleAlert} role="alert">
      {formError}
    </Callout>
  );

  return (
    <>
      <PageHeader
        title={vi.supplies.title}
        description={manage ? vi.supplies.descriptionManager : vi.supplies.descriptionGroom}
        actions={
          manage ? (
            <Button
              onClick={() => {
                resetErrors();
                setItem(NEW_ITEM);
              }}
            >
              <Plus aria-hidden />
              {vi.supplies.add}
            </Button>
          ) : undefined
        }
      />
      <div className="grid gap-10">
        {decide && (
          <Section title={vi.supplies.pending}>
            <DataView
              load={pending.load}
              isEmpty={(data) => data.items.length === 0}
              empty={<EmptyState icon={Package} title={vi.supplies.pendingEmpty} text={vi.supplies.pendingEmptyHint} />}
            >
              {(data) => (
                <div className="grid gap-3">
                  <DataTable caption={vi.supplies.pending} data={data.items} columns={pendingColumns} rowKey={(p) => p.id} />
                  <ListFooter data={data} onPage={pending.setPage} onSize={pending.setSize} />
                </div>
              )}
            </DataView>
          </Section>
        )}

        <Section title={manage ? vi.supplies.catalog : vi.supplies.items}>
          <div className="mb-4">
            <ListToolbar query={items.query} onQuery={items.setQuery} placeholder={vi.supplies.searchPlaceholder} total={items.load.state.data?.total}>
              <Select
                aria-label={vi.supplies.category}
                value={items.filters.category ?? ''}
                onChange={(e) => items.setFilter('category', e.target.value)}
                className="w-48"
              >
                <option value="">{vi.supplies.anyCategory}</option>
                {SUPPLY_CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {vi.supplies.categories[c]}
                  </option>
                ))}
              </Select>
            </ListToolbar>
          </div>
          <DataView
            load={items.load}
            isEmpty={(data) => data.items.length === 0}
            empty={
              items.narrowed ? (
                <EmptyState icon={Package} title={vi.common.noResults} text={vi.common.noResultsHint} />
              ) : (
                <EmptyState
                  icon={Package}
                  title={vi.supplies.empty}
                  text={manage ? vi.supplies.emptyHintManager : vi.supplies.emptyHintGroom}
                />
              )
            }
          >
            {(data) => (
              <div className="grid gap-3">
                <DataTable
                  caption={manage ? vi.supplies.catalog : vi.supplies.items}
                  data={data.items}
                  columns={manage ? catalogColumns : groomItemColumns}
                  rowKey={(i) => i.id}
                  rowClassName={(i) => (i.active ? undefined : 'text-muted-foreground')}
                />
                <ListFooter data={data} onPage={items.setPage} onSize={items.setSize} />
              </div>
            )}
          </DataView>
        </Section>

        {propose && (
          <Section title={vi.supplies.mine} description={vi.supplies.mineNote}>
            <DataView
              load={proposals.load}
              isEmpty={(data) => data.items.length === 0}
              empty={<EmptyState icon={Package} title={vi.supplies.mineEmpty} text={vi.supplies.mineEmptyHint} />}
            >
              {(data) => (
                <div className="grid gap-3">
                  <DataTable caption={vi.supplies.mine} data={data.items} columns={mineColumns} rowKey={(p) => p.id} />
                  <ListFooter data={data} onPage={proposals.setPage} onSize={proposals.setSize} />
                </div>
              )}
            </DataView>
          </Section>
        )}
      </div>

      <Dialog open={item !== null} onOpenChange={(open) => !open && setItem(null)}>
        <DialogContent>
          {item && (
            <form className="grid gap-4" noValidate onSubmit={saveItem}>
              <DialogHeader>
                <DialogTitle>{item.id === null ? vi.supplies.addTitle : vi.supplies.editTitle}</DialogTitle>
                <DialogDescription>{vi.supplies.itemDescription}</DialogDescription>
              </DialogHeader>
              {errorCallout}
              <FormField label={vi.supplies.name} error={fieldErrors.name}>
                <Input value={item.name} onChange={(e) => setItem({ ...item, name: e.target.value })} autoComplete="off" />
              </FormField>
              <div className="grid gap-4 sm:grid-cols-2">
                <FormField label={vi.supplies.category} error={fieldErrors.category}>
                  <Select value={item.category} onChange={(e) => setItem({ ...item, category: e.target.value as SupplyCategory | '' })}>
                    <option value="">{vi.supplies.chooseCategory}</option>
                    {SUPPLY_CATEGORIES.map((c) => (
                      <option key={c} value={c}>
                        {vi.supplies.categories[c]}
                      </option>
                    ))}
                  </Select>
                </FormField>
                <FormField label={vi.supplies.unit} error={fieldErrors.unit}>
                  <Input value={item.unit} onChange={(e) => setItem({ ...item, unit: e.target.value })} autoComplete="off" />
                </FormField>
              </div>
              {item.id === null ? (
                <div className="grid gap-4 sm:grid-cols-2">
                  <FormField label={vi.supplies.price} description={vi.supplies.priceHint} error={fieldErrors.price}>
                    <Input
                      type="number"
                      inputMode="decimal"
                      min={0}
                      step="any"
                      value={item.price}
                      onChange={(e) => setItem({ ...item, price: e.target.value })}
                    />
                  </FormField>
                  <FormField label={vi.supplies.openingQuantity} description={vi.supplies.openingHint} error={fieldErrors.openingQuantity}>
                    <Input
                      type="number"
                      inputMode="numeric"
                      min={0}
                      value={item.openingQuantity}
                      onChange={(e) => setItem({ ...item, openingQuantity: e.target.value })}
                    />
                  </FormField>
                </div>
              ) : (
                <p className="text-meta text-muted-foreground">{vi.supplies.quantityByMovement}</p>
              )}
              <DialogFooter>
                <Button variant="outline" onClick={() => setItem(null)}>
                  {vi.common.cancel}
                </Button>
                <Button type="submit" loading={saving}>
                  {vi.common.save}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>

      <StockDialog
        item={stocking}
        onClose={() => setStockingId(null)}
        onChanged={() => void items.load.reload()}
      />

      <Dialog open={proposing !== null} onOpenChange={(open) => !open && setProposing(null)}>
        <DialogContent>
          {proposing && (
            <form className="grid gap-4" noValidate onSubmit={saveProposal}>
              <DialogHeader>
                <DialogTitle>{vi.supplies.proposeTitle(proposing.name)}</DialogTitle>
                <DialogDescription>{vi.supplies.proposeDescription}</DialogDescription>
              </DialogHeader>
              {errorCallout}
              <FormField label={vi.supplies.proposeQuantity(proposing.unit)} error={fieldErrors.quantity} className="sm:max-w-64">
                <Input type="number" inputMode="numeric" min={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
              </FormField>
              <FormField label={vi.supplies.note} error={fieldErrors.note}>
                <Input value={note} onChange={(e) => setNote(e.target.value)} autoComplete="off" />
              </FormField>
              <DialogFooter>
                <Button variant="outline" onClick={() => setProposing(null)}>
                  {vi.common.cancel}
                </Button>
                <Button type="submit" loading={saving}>
                  {vi.supplies.send}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={rejecting !== null} onOpenChange={(open) => !open && setRejecting(null)}>
        <DialogContent>
          {rejecting && (
            <form className="grid gap-4" noValidate onSubmit={saveRejection}>
              <DialogHeader>
                <DialogTitle>{vi.supplies.rejectTitle}</DialogTitle>
                <DialogDescription>{vi.supplies.rejectDescription(rejecting.itemName)}</DialogDescription>
              </DialogHeader>
              {errorCallout}
              <FormField label={vi.supplies.reason} error={fieldErrors.reason}>
                <Input value={reason} onChange={(e) => setReason(e.target.value)} autoComplete="off" />
              </FormField>
              <DialogFooter>
                <Button variant="outline" onClick={() => setRejecting(null)}>
                  {vi.common.cancel}
                </Button>
                <Button type="submit" variant="destructive" loading={saving}>
                  {vi.supplies.confirmReject}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
