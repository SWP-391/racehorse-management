import { useState, type FormEvent } from 'react';
import { CircleCheck, CircleOff, PencilLine, Plus, Ruler, RotateCcw } from 'lucide-react';
import { api } from '../api/api';
import type { SupplyUnit } from '../api/types';
import type { Load } from '../hooks/useLoad';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';
import { unitName, UNIT_NAME_MAX } from '../lib/validate';
import { fieldsOf, messageOf } from './ErrorText';
import { Button } from './ui/button';
import { DataTable, type DataColumn } from './ui/data-table';
import { DataView } from './ui/data-view';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Badge, Section } from './ui/display';
import { FormField } from './ui/form-field';
import { Input } from './ui/input';
import { Callout, EmptyState } from './ui/states';

/** The catalog of units of supplies, for the Club Manager (BR-194, BR-195): add, rename, deactivate, reactivate, no delete. */
/** `onChanged` reloads what shows unit names (the item list), because a rename reaches every item at once. */
export function SupplyUnitsSection({ units, onChanged }: { units: Load<SupplyUnit[]>; onChanged?: () => void }) {
  // `target` is the unit being renamed, null when adding; `open` tells whether the dialog is shown.
  const [dialog, setDialog] = useState<{ target: SupplyUnit | null } | null>(null);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [busy, setBusy] = useState<number | null>(null);

  function openDialog(target: SupplyUnit | null) {
    setDialog({ target });
    setName(target?.name ?? '');
    setError(null);
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!dialog) return;
    const refused = unitName(name);
    if (refused) {
      setError(refused);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      if (dialog.target) await api.renameSupplyUnit(dialog.target.id, name);
      else await api.addSupplyUnit(name);
      notify.success(vi.common.saved);
      setDialog(null);
      await units.reload();
      onChanged?.();
    } catch (err) {
      setError(fieldsOf(err).name ?? messageOf(err));
    } finally {
      setSaving(false);
    }
  }

  async function toggle(u: SupplyUnit) {
    setBusy(u.id);
    try {
      await api.setSupplyUnitActive(u.id, !u.active);
      notify.success(vi.common.saved);
    } catch (err) {
      notify.error(err);
    } finally {
      await units.reload();
      onChanged?.();
      setBusy(null);
    }
  }

  const columns: DataColumn<SupplyUnit>[] = [
    { id: 'name', header: vi.supplies.units.name, primary: true, cell: (u) => u.name },
    { id: 'items', header: vi.supplies.units.items, cell: (u) => <span className="tabular">{u.itemCount}</span> },
    {
      id: 'state',
      header: vi.supplies.units.state,
      cell: (u) =>
        u.active ? (
          <Badge tone="eligible" icon={<CircleCheck aria-hidden />}>{vi.supplies.units.active}</Badge>
        ) : (
          <Badge tone="neutral" icon={<CircleOff aria-hidden />}>{vi.supplies.units.inactive}</Badge>
        ),
    },
    {
      id: 'actions',
      header: vi.supplies.units.actions,
      actions: true,
      cell: (u) => (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" aria-label={vi.supplies.units.renameOf(u.name)} onClick={() => openDialog(u)}>
            <PencilLine aria-hidden />
            {vi.supplies.units.rename}
          </Button>
          <Button
            size="sm"
            variant="outline"
            loading={busy === u.id}
            aria-label={u.active ? vi.supplies.units.deactivateOf(u.name) : vi.supplies.units.activateOf(u.name)}
            onClick={() => void toggle(u)}
          >
            {u.active ? <CircleOff aria-hidden /> : <RotateCcw aria-hidden />}
            {u.active ? vi.supplies.units.deactivate : vi.supplies.units.activate}
          </Button>
        </div>
      ),
    },
  ];

  return (
    <Section
      title={vi.supplies.units.title}
      description={vi.supplies.units.description}
      actions={
        <Button onClick={() => openDialog(null)}>
          <Plus aria-hidden />
          {vi.supplies.units.add}
        </Button>
      }
    >
      <DataView
        load={units}
        isEmpty={(list) => list.length === 0}
        empty={<EmptyState icon={Ruler} title={vi.supplies.units.empty} text={vi.supplies.units.emptyHint} />}
      >
        {(list) => <DataTable caption={vi.supplies.units.title} data={list} columns={columns} rowKey={(u) => u.id} />}
      </DataView>

      <Dialog open={dialog !== null} onOpenChange={(open) => !open && setDialog(null)}>
        <DialogContent>
          {dialog && (
            <form className="grid gap-4" noValidate onSubmit={save} aria-label={dialog.target ? vi.supplies.units.rename : vi.supplies.units.add}>
              <DialogHeader>
                <DialogTitle>{dialog.target ? vi.supplies.units.renameOf(dialog.target.name) : vi.supplies.units.add}</DialogTitle>
                <DialogDescription>{vi.supplies.units.description}</DialogDescription>
              </DialogHeader>
              {error && !unitName(name) && (
                <Callout tone="danger" role="alert">
                  {error}
                </Callout>
              )}
              <FormField label={vi.supplies.units.name} error={unitName(name) && error ? error : undefined}>
                <Input maxLength={UNIT_NAME_MAX + 10} value={name} onChange={(e) => setName(e.target.value)} autoComplete="off" />
              </FormField>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setDialog(null)}>
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
    </Section>
  );
}
