import { useState, type FormEvent } from 'react';
import { CircleAlert, CircleCheck, CircleDashed, House, MapPin, Plus, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import type { Barn, BarnArea, BarnHorse, OptionItem, Page } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { HorseAvatar } from '../components/HorseAvatar';
import { useHorsePhotos } from '../hooks/useHorsePhotos';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { notify } from '../lib/notify';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogTitle,
} from '../components/ui/alert-dialog';
import { Button } from '../components/ui/button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Badge, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { SearchSelect, type Chosen } from '../components/ui/search-select';
import { Select } from '../components/ui/select';
import { Callout, EmptyState } from '../components/ui/states';

/** Today's chores of a horse as a count with icon and text (never colour alone). */
function ChoreSummary({ done, total }: { done: number; total: number }) {
  if (total > 0 && done === total) {
    return (
      <Badge tone="eligible" icon={<CircleCheck aria-hidden />}>
        {vi.barn.allDone}
      </Badge>
    );
  }
  return (
    <Badge tone="neutral" icon={<CircleDashed aria-hidden />}>
      {vi.barn.choresDone(done, total)}
    </Badge>
  );
}

const byText = (a: string, b: string) => a.localeCompare(b, 'vi', { numeric: true, sensitivity: 'base' });

/** The placement dialog: a horse (chosen from the horses without a stall, or fixed), an area and a free slot. */
interface PlaceState {
  horse: Chosen | null;
  /** The horse is fixed (moving a horse, or placing one from the list of horses without a stall). */
  locked: boolean;
  /** The horse already has a stall that its own slot may keep. */
  placed: boolean;
  areaId: string;
  slot: string;
}

interface AreaForm {
  area: BarnArea | null;
  name: string;
  capacity: string;
}

/**
 * Barn page (BR-129, BR-163 to BR-165): every area as a grid of its stalls, empty ones as placeholders on which the
 * Club Manager picks a horse; horses without a stall are listed apart. The Club Manager also keeps the area catalog.
 */
export default function BarnPage() {
  const { can } = useAuth();
  const photoOf = useHorsePhotos();
  const allowed = can('VIEW_BARN');
  const manageStalls = can('MANAGE_STALLS');
  const manageAreas = can('MANAGE_AREAS');
  const [place, setPlace] = useState<PlaceState | null>(null);
  const [placeErrors, setPlaceErrors] = useState<Record<string, string>>({});
  const [placeError, setPlaceError] = useState<string | null>(null);
  const [areaForm, setAreaForm] = useState<AreaForm | null>(null);
  const [areaErrors, setAreaErrors] = useState<Record<string, string>>({});
  const [areaError, setAreaError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [clearing, setClearing] = useState<{ horse: BarnHorse; where: string } | null>(null);
  const [deleting, setDeleting] = useState<BarnArea | null>(null);

  const load = useLoad(() => api.barn(), [], allowed);
  const barn: Barn | null = load.state.data;

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  // ---- placing a horse -----------------------------------------------------------------------------------

  function openPlace(next: PlaceState) {
    setPlace(next);
    setPlaceErrors({});
    setPlaceError(null);
  }

  /** The unplaced horses as a page of options, so the shared select can search them (BR-186). */
  async function unplacedPage(q: string): Promise<Page<OptionItem>> {
    const needle = q.trim().toLowerCase();
    const items = (barn?.unplaced ?? [])
      .filter((h) => needle === '' || h.horseName.toLowerCase().includes(needle) || h.registrationNumber.toLowerCase().includes(needle))
      .map((h) => ({ id: h.horseId, label: h.horseName, hint: h.registrationNumber }));
    return { items, page: 0, size: Math.max(items.length, 1), total: items.length };
  }

  async function savePlace(e: FormEvent) {
    e.preventDefault();
    if (!place) return;
    const errors: Record<string, string> = {};
    if (!place.horse) errors.horse = vi.barn.horseRequired;
    if (place.areaId === '') errors.area = vi.barn.areaRequired;
    if (place.slot === '') errors.slot = vi.barn.slotRequired;
    setPlaceErrors(errors);
    setPlaceError(null);
    if (Object.keys(errors).length > 0 || !place.horse) return;
    setSaving(true);
    try {
      await api.setStall(place.horse.id, { areaId: Number(place.areaId), slot: Number(place.slot) });
      notify.success(vi.common.saved);
      setPlace(null);
      await load.reload();
    } catch (err) {
      setPlaceErrors(fieldsOf(err));
      // A stall held by another horse names that horse in the service's message; keep it visible in the dialog.
      setPlaceError(messageOf(err));
    } finally {
      setSaving(false);
    }
  }

  async function clearStall(h: BarnHorse) {
    try {
      await api.clearStall(h.horseId);
      notify.success(vi.common.saved);
    } catch (err) {
      notify.error(err);
    } finally {
      setClearing(null);
      await load.reload();
    }
  }

  // ---- the area catalog -------------------------------------------------------------------------------------

  function openArea(area: BarnArea | null) {
    setAreaForm({ area, name: area?.name ?? '', capacity: area ? String(area.capacity) : '6' });
    setAreaErrors({});
    setAreaError(null);
  }

  async function saveArea(e: FormEvent) {
    e.preventDefault();
    if (!areaForm) return;
    const errors: Record<string, string> = {};
    const capacity = Number(areaForm.capacity);
    if (areaForm.name.trim() === '') errors.name = vi.barn.areaNameRequired;
    if (!Number.isInteger(capacity) || capacity < 1 || capacity > 200) errors.capacity = vi.barn.capacityInvalid;
    setAreaErrors(errors);
    setAreaError(null);
    if (Object.keys(errors).length > 0) return;
    setSaving(true);
    try {
      const input = { name: areaForm.name.trim(), capacity };
      if (areaForm.area) await api.updateArea(areaForm.area.id, input);
      else await api.createArea(input);
      notify.success(vi.common.saved);
      setAreaForm(null);
      await load.reload();
    } catch (err) {
      setAreaErrors(fieldsOf(err));
      setAreaError(messageOf(err));
    } finally {
      setSaving(false);
    }
  }

  async function deleteArea(area: BarnArea) {
    try {
      await api.deleteArea(area.id);
      notify.success(vi.common.saved);
    } catch (err) {
      notify.error(err);
    } finally {
      setDeleting(null);
      await load.reload();
    }
  }

  // ---- views ----------------------------------------------------------------------------------------------------

  const chosenArea = barn?.areas.find((a) => String(a.id) === place?.areaId) ?? null;

  const unplacedColumns: DataColumn<BarnHorse>[] = [
    {
      id: 'horse',
      header: vi.barn.horse,
      primary: true,
      sortValue: (h) => h.horseName,
      cell: (h) => (
        <span className="flex items-start gap-2">
          <HorseAvatar id={h.horseId} name={h.horseName} photoVersion={photoOf(h.horseId)} className="size-8 shrink-0 text-sm" />
          <span className="grid gap-0.5">
          <span className="font-display text-[1.0625rem] font-medium text-foreground">{h.horseName}</span>
          {h.legacyStall && (
            <span className="flex flex-wrap items-center gap-2 text-meta text-muted-foreground">
              <Badge tone="watch" icon={<CircleAlert aria-hidden />}>
                {vi.barn.needsPlacement}
              </Badge>
              {vi.barn.legacy(h.legacyStall)}
            </span>
          )}
          </span>
        </span>
      ),
    },
    { id: 'reg', header: vi.barn.registration, cell: (h) => <span className="tabular">{h.registrationNumber}</span> },
    { id: 'chores', header: vi.barn.todayChores, cell: (h) => <ChoreSummary done={h.choresDone} total={h.choresTotal} /> },
    ...(manageStalls
      ? [
          {
            id: 'actions',
            header: vi.barn.actions,
            actions: true,
            cell: (h: BarnHorse) => (
              <Button
                variant="outline"
                size="sm"
                aria-label={vi.barn.assignFor(h.horseName)}
                onClick={() =>
                  openPlace({ horse: { id: h.horseId, label: h.horseName }, locked: true, placed: false, areaId: '', slot: '' })
                }
              >
                <MapPin aria-hidden />
                {vi.barn.assign}
              </Button>
            ),
          } satisfies DataColumn<BarnHorse>,
        ]
      : []),
  ];

  function slotCard(area: BarnArea, slot: BarnArea['slots'][number]) {
    const h = slot.horse;
    if (!h) {
      return manageStalls ? (
        <li key={slot.slot}>
          <Button
            variant="outline"
            className="h-full min-h-24 w-full border-dashed"
            aria-label={vi.barn.emptySlotFor(area.name, slot.slot)}
            onClick={() => openPlace({ horse: null, locked: false, placed: false, areaId: String(area.id), slot: String(slot.slot) })}
          >
            <span className="grid gap-1 text-center">
              <span className="font-medium">{vi.barn.stallOf(slot.slot)}</span>
              <span className="inline-flex items-center justify-center gap-1 text-meta text-muted-foreground">
                <Plus aria-hidden className="size-3.5" /> {vi.barn.emptySlot}
              </span>
            </span>
          </Button>
        </li>
      ) : (
        <li key={slot.slot} className="grid min-h-24 content-center gap-1 rounded-md border border-dashed px-3 py-2 text-center">
          <span className="font-medium">{vi.barn.stallOf(slot.slot)}</span>
          <span className="text-meta text-muted-foreground">{vi.barn.emptySlot}</span>
        </li>
      );
    }
    return (
      <li key={slot.slot} className="grid min-h-24 content-start gap-2 rounded-md border bg-card px-3 py-2">
        <span className="flex items-baseline justify-between gap-2">
          <span className="text-meta text-muted-foreground">{vi.barn.stallOf(slot.slot)}</span>
          <span className="tabular text-meta text-muted-foreground">{h.registrationNumber}</span>
        </span>
        <span className="flex items-center gap-2">
          <HorseAvatar id={h.horseId} name={h.horseName} photoVersion={photoOf(h.horseId)} className="size-8 shrink-0 text-sm" />
          <span className="font-display text-[1.0625rem] font-medium text-foreground">{h.horseName}</span>
        </span>
        <ChoreSummary done={h.choresDone} total={h.choresTotal} />
        {manageStalls && (
          <span className="flex flex-wrap gap-1">
            <Button
              variant="ghost"
              size="sm"
              className="-ml-3"
              aria-label={vi.barn.moveFor(h.horseName)}
              onClick={() =>
                openPlace({
                  horse: { id: h.horseId, label: h.horseName },
                  locked: true,
                  placed: true,
                  areaId: String(area.id),
                  slot: String(slot.slot),
                })
              }
            >
              {vi.barn.move}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              aria-label={vi.barn.clearFor(h.horseName)}
              onClick={() => setClearing({ horse: h, where: vi.barn.stallFull(area.name, slot.slot) })}
            >
              {vi.barn.clear}
            </Button>
          </span>
        )}
      </li>
    );
  }

  return (
    <>
      <PageHeader
        title={vi.barn.title}
        description={vi.barn.description}
        actions={
          manageAreas ? (
            <Button onClick={() => openArea(null)}>
              <Plus aria-hidden /> {vi.barn.addArea}
            </Button>
          ) : undefined
        }
      />
      <DataView
        load={load}
        isEmpty={(b) => b.areas.length === 0 && b.unplaced.length === 0}
        empty={<EmptyState icon={House} title={vi.barn.empty} text={manageAreas ? vi.barn.emptyHint : vi.barn.emptyHintStaff} />}
      >
        {(b) => (
          <div className="grid gap-10">
            {b.areas.length === 0 && (
              <EmptyState icon={House} title={vi.barn.empty} text={manageAreas ? vi.barn.emptyHint : vi.barn.emptyHintStaff} />
            )}
            {b.areas.map((area) => {
              const used = area.slots.filter((s) => s.horse).length;
              return (
                <Section
                  key={area.id}
                  title={area.name}
                  description={manageAreas || manageStalls ? vi.barn.occupied(used, area.capacity) : vi.barn.horseCount(used)}
                  actions={
                    manageAreas ? (
                      <>
                        <Button variant="outline" size="sm" aria-label={vi.barn.editArea(area.name)} onClick={() => openArea(area)}>
                          {vi.barn.editAreaTitle}
                        </Button>
                        <Button variant="ghost" size="sm" aria-label={vi.barn.deleteArea(area.name)} onClick={() => setDeleting(area)}>
                          {vi.barn.deleteTitle}
                        </Button>
                      </>
                    ) : undefined
                  }
                >
                  <ul aria-label={area.name} className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                    {area.slots.map((slot) => slotCard(area, slot))}
                  </ul>
                </Section>
              );
            })}
            {b.unplaced.length > 0 && (
              <Section title={vi.barn.withoutStall} description={vi.barn.withoutStallNote}>
                <DataTable
                  caption={vi.barn.withoutStall}
                  data={[...b.unplaced].sort((x, y) => byText(x.horseName, y.horseName))}
                  columns={unplacedColumns}
                  rowKey={(h) => h.horseId}
                />
              </Section>
            )}
          </div>
        )}
      </DataView>

      <Dialog open={place !== null} onOpenChange={(open) => !open && setPlace(null)}>
        <DialogContent>
          {place && (
            <form className="grid gap-4" noValidate onSubmit={(e) => void savePlace(e)}>
              <DialogHeader>
                <DialogTitle>
                  {place.locked && place.horse
                    ? place.placed
                      ? vi.barn.dialogTitleMove(place.horse.label)
                      : vi.barn.dialogTitleAssign(place.horse.label)
                    : vi.barn.dialogTitleSlot}
                </DialogTitle>
                <DialogDescription>{vi.barn.dialogDescription}</DialogDescription>
              </DialogHeader>
              {placeError && Object.keys(placeErrors).length === 0 && (
                <Callout tone="danger" icon={CircleAlert} role="alert">
                  {placeError}
                </Callout>
              )}
              {!place.locked && (
                <FormField label={vi.barn.chooseHorse} error={placeErrors.horse}>
                  <SearchSelect
                    value={place.horse}
                    placeholder={vi.barn.chooseHorsePlaceholder}
                    load={unplacedPage}
                    onChange={(chosen) => setPlace({ ...place, horse: chosen })}
                  />
                </FormField>
              )}
              <FormField label={vi.barn.area} error={placeErrors.area}>
                <Select value={place.areaId} onChange={(e) => setPlace({ ...place, areaId: e.target.value, slot: '' })}>
                  <option value="">{vi.barn.chooseArea}</option>
                  {barn?.areas.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField label={vi.barn.slot} error={placeErrors.slot}>
                <Select value={place.slot} disabled={chosenArea === null} onChange={(e) => setPlace({ ...place, slot: e.target.value })}>
                  <option value="">{vi.barn.chooseSlot}</option>
                  {chosenArea?.slots.map((s) => {
                    const mine = s.horse !== null && s.horse.horseId === place.horse?.id;
                    return (
                      <option key={s.slot} value={s.slot} disabled={s.horse !== null && !mine}>
                        {s.horse !== null && !mine ? `${vi.barn.slotTaken(s.slot)} (${s.horse.horseName})` : vi.barn.stallOf(s.slot)}
                      </option>
                    );
                  })}
                </Select>
              </FormField>
              <DialogFooter>
                <Button variant="outline" type="button" onClick={() => setPlace(null)}>
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

      <Dialog open={areaForm !== null} onOpenChange={(open) => !open && setAreaForm(null)}>
        <DialogContent>
          {areaForm && (
            <form className="grid gap-4" noValidate onSubmit={(e) => void saveArea(e)}>
              <DialogHeader>
                <DialogTitle>{areaForm.area ? vi.barn.editAreaTitle : vi.barn.createAreaTitle}</DialogTitle>
                <DialogDescription>{vi.barn.capacityHint}</DialogDescription>
              </DialogHeader>
              {areaError && Object.keys(areaErrors).length === 0 && (
                <Callout tone="danger" icon={CircleAlert} role="alert">
                  {areaError}
                </Callout>
              )}
              <FormField label={vi.barn.areaName} error={areaErrors.name}>
                <Input value={areaForm.name} onChange={(e) => setAreaForm({ ...areaForm, name: e.target.value })} autoComplete="off" />
              </FormField>
              <FormField label={vi.barn.capacity} error={areaErrors.capacity}>
                <Input
                  type="number"
                  min={1}
                  max={200}
                  value={areaForm.capacity}
                  onChange={(e) => setAreaForm({ ...areaForm, capacity: e.target.value })}
                />
              </FormField>
              <DialogFooter>
                <Button variant="outline" type="button" onClick={() => setAreaForm(null)}>
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

      <AlertDialog open={clearing !== null} onOpenChange={(open) => !open && setClearing(null)}>
        <AlertDialogContent>
          <AlertDialogTitle>{vi.barn.clearTitle}</AlertDialogTitle>
          <AlertDialogDescription>{clearing && vi.barn.clearDescription(clearing.horse.horseName, clearing.where)}</AlertDialogDescription>
          <AlertDialogFooter>
            <AlertDialogCancel>{vi.common.cancel}</AlertDialogCancel>
            <AlertDialogAction destructive onClick={() => clearing && void clearStall(clearing.horse)}>
              {vi.barn.confirmClear}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogTitle>{vi.barn.deleteTitle}</AlertDialogTitle>
          <AlertDialogDescription>{deleting && vi.barn.deleteDescription(deleting.name)}</AlertDialogDescription>
          <AlertDialogFooter>
            <AlertDialogCancel>{vi.common.cancel}</AlertDialogCancel>
            <AlertDialogAction destructive onClick={() => deleting && void deleteArea(deleting)}>
              {vi.barn.confirmDelete}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
