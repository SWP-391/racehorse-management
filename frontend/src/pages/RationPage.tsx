import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { CircleAlert, Info, Plus, ShieldOff, Trash2, UtensilsCrossed } from 'lucide-react';
import { api } from '../api/api';
import {
  RATION_ITEM_TYPES,
  type Ration,
  type RationMeal,
  type RationMealInput,
  type RationOption,
  type RationVersion,
} from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { Button } from '../components/ui/button';
import { DataView } from '../components/ui/data-view';
import { Badge, PageHeader } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { NumberInput, TimeInput } from '../components/ui/typed-inputs';
import { LIMITS } from '../lib/limits';
import { BOUNDS, blank, collect, focusFirstInvalid, maxLength, number, type Check } from '../lib/validate';
import { Select } from '../components/ui/select';
import { SearchSelect } from '../components/ui/search-select';
import { Callout, EmptyState } from '../components/ui/states';
import { useLoad } from '../hooks/useLoad';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';

const emptyMeal = (): RationMealInput => ({
  name: '',
  time: '',
  items: [{ type: '', supplyItemId: null, quantity: '' }],
});

/** The editor starts from the current version, with quantities as text the service parses. */
function toInput(current: RationVersion | null): RationMealInput[] {
  if (current === null) return [emptyMeal()];
  return current.meals.map((m) => ({
    name: m.name,
    time: m.time,
    items: m.items.map((i) => ({ type: i.type, supplyItemId: i.supplyItemId, quantity: String(i.quantity) })),
  }));
}

/** Read-only meals of one version: time, items with type and quantity, inactive items marked (AC-252, AC-255). */
export function MealList({ meals, label = vi.ration.current }: { meals: RationMeal[]; label?: string }) {
  return (
    <ul className="grid gap-3" aria-label={label}>
      {meals.map((m) => (
        <li key={m.name} className="grid gap-2 rounded-lg border bg-card p-4">
          <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <span className="font-display text-lg font-medium">{m.name}</span>
            <span className="tabular text-meta text-muted-foreground">{m.time}</span>
          </p>
          <ul className="grid gap-1">
            {m.items.map((i) => (
              <li key={i.supplyItemId} className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                <Badge tone="neutral">{vi.ration.types[i.type]}</Badge>
                <span>{i.supplyItemName}</span>
                <span className="tabular text-muted-foreground">
                  {i.quantity} {i.unit}
                </span>
                {!i.active && <Badge tone="watch">{vi.ration.inactive}</Badge>}
              </li>
            ))}
          </ul>
        </li>
      ))}
    </ul>
  );
}

function Editor({
  horseId,
  current,
  options,
  onSaved,
}: {
  horseId: number;
  current: RationVersion | null;
  options: RationOption[];
  onSaved: (r: Ration) => void;
}) {
  const [meals, setMeals] = useState<RationMealInput[]>(() => toInput(current));
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  // A saved version replaces what the editor shows.
  useEffect(() => setMeals(toInput(current)), [current]);

  const patchMeal = (i: number, patch: Partial<RationMealInput>) =>
    setMeals((all) => all.map((m, k) => (k === i ? { ...m, ...patch } : m)));
  const patchItem = (i: number, j: number, patch: Partial<RationMealInput['items'][number]>) =>
    setMeals((all) =>
      all.map((m, k) => (k === i ? { ...m, items: m.items.map((it, l) => (l === j ? { ...it, ...patch } : it)) } : m)),
    );

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    // The service's rules (RationService.validate), named on every field at once before anything is sent (BR-138, BR-139).
    const checks: Record<string, Check> = {};
    const seen = new Set<string>();
    meals.forEach((m, i) => {
      const at = `meals[${i}]`;
      const key = m.name.trim().toLowerCase();
      checks[`${at}.name`] = blank(m.name)
        ? 'Bắt buộc nhập tên bữa.'
        : seen.has(key)
          ? `Tên bữa "${m.name.trim()}" bị trùng trong khẩu phần.`
          : maxLength(m.name, LIMITS.mealName);
      seen.add(key);
      checks[`${at}.time`] = blank(m.time) || !/^\d{1,2}:\d{2}$/.test(m.time.trim()) ? 'Giờ ăn không hợp lệ (HH:mm).' : null;
      const used = new Set<number>();
      m.items.forEach((it, j) => {
        const item = `${at}.items[${j}]`;
        checks[`${item}.type`] = blank(it.type) ? 'Chọn loại thành phần (Ngũ cốc, Cỏ hoặc Vitamin).' : null;
        checks[`${item}.supplyItemId`] =
          it.supplyItemId === null ? 'Vật tư không tồn tại.' : used.has(it.supplyItemId) ? 'Vật tư bị trùng trong bữa.' : null;
        if (it.supplyItemId !== null) used.add(it.supplyItemId);
        checks[`${item}.quantity`] = number(it.quantity, { positive: true, max: BOUNDS.quantity.max, required: true });
      });
    });
    const refused = collect(checks);
    setFields(refused);
    if (Object.keys(refused).length > 0) {
      focusFirstInvalid(e.currentTarget as HTMLFormElement);
      return;
    }
    setPending(true);
    try {
      onSaved(await api.saveRation(horseId, meals));
      notify.success(vi.ration.saved);
    } catch (err) {
      setError(messageOf(err));
      setFields(fieldsOf(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-label={vi.ration.edit} className="grid gap-5">
      <p className="text-meta text-muted-foreground">{vi.ration.editHint}</p>
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert">
          {error}
        </Callout>
      )}
      {meals.map((m, i) => (
        <fieldset key={i} className="grid gap-4 rounded-lg border p-4">
          <legend className="px-1 font-display text-lg font-medium">{`${vi.ration.meal} ${i + 1}`}</legend>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label={vi.ration.mealName} error={fields[`meals[${i}].name`]}>
              <Input maxLength={LIMITS.mealName} value={m.name} onChange={(e) => patchMeal(i, { name: e.target.value })} />
            </FormField>
            <FormField label={vi.ration.mealTime} error={fields[`meals[${i}].time`]}>
              <TimeInput value={m.time} onChange={(v) => patchMeal(i, { time: v })} />
            </FormField>
          </div>
          {fields[`meals[${i}].items`] && (
            <p role="alert" className="text-meta text-status-injured-ink">
              {fields[`meals[${i}].items`]}
            </p>
          )}
          {m.items.map((it, j) => {
            const at = `meals[${i}].items[${j}]`;
            const unit = options.find((o) => o.id === it.supplyItemId)?.unit;
            return (
              <div key={j} className="grid gap-3 rounded-md bg-secondary/40 p-3 sm:grid-cols-[1fr_1.4fr_1fr_auto] sm:items-start">
                <FormField label={`${vi.ration.itemType} ${j + 1}`} error={fields[`${at}.type`]}>
                  <Select
                    aria-label={`${vi.ration.itemType} ${i + 1}.${j + 1}`}
                    value={it.type}
                    onChange={(e) => patchItem(i, j, { type: e.target.value as RationMealInput['items'][number]['type'] })}
                  >
                    <option value="">{vi.ration.chooseType}</option>
                    {RATION_ITEM_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {vi.ration.types[t]}
                      </option>
                    ))}
                  </Select>
                </FormField>
                <FormField label={`${vi.ration.supplyItem} ${j + 1}`} error={fields[`${at}.supplyItemId`]}>
                  {/* The feed items are the Club Manager's catalog: chosen from it with a search, never typed (BR-186). */}
                  <SearchSelect
                    aria-label={`${vi.ration.supplyItem} ${i + 1}.${j + 1}`}
                    value={
                      it.supplyItemId === null
                        ? null
                        : { id: it.supplyItemId, label: options.find((o) => o.id === it.supplyItemId)?.name ?? `#${it.supplyItemId}` }
                    }
                    placeholder={vi.ration.chooseSupply}
                    load={(q) => api.options('feed', { q })}
                    onChange={(chosen) => patchItem(i, j, { supplyItemId: chosen ? chosen.id : null })}
                  />
                </FormField>
                <FormField
                  label={`${vi.ration.quantity} ${j + 1}${unit ? ` (${unit})` : ''}`}
                  error={fields[`${at}.quantity`]}
                >
                  <NumberInput
                    min={0}
                    max={BOUNDS.quantity.max}
                    step="any"
                    unit={unit ?? 'đơn vị'}
                    unitInLabel
                    aria-label={`${vi.ration.quantity} ${i + 1}.${j + 1}`}
                    value={it.quantity}
                    onChange={(v) => patchItem(i, j, { quantity: v })}
                  />
                </FormField>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="sm:mt-6"
                  aria-label={`${vi.ration.removeItem} ${i + 1}.${j + 1}`}
                  disabled={m.items.length === 1}
                  onClick={() => patchMeal(i, { items: m.items.filter((_, l) => l !== j) })}
                >
                  <Trash2 aria-hidden />
                </Button>
              </div>
            );
          })}
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => patchMeal(i, { items: [...m.items, { type: '', supplyItemId: null, quantity: '' }] })}
            >
              <Plus aria-hidden />
              {vi.ration.addItem}
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={meals.length === 1}
              onClick={() => setMeals((all) => all.filter((_, k) => k !== i))}
            >
              {vi.ration.removeMeal}
            </Button>
          </div>
        </fieldset>
      ))}
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" onClick={() => setMeals((all) => [...all, emptyMeal()])}>
          <Plus aria-hidden />
          {vi.ration.addMeal}
        </Button>
        <Button type="submit" loading={pending}>
          {vi.ration.save}
        </Button>
      </div>
    </form>
  );
}

/**
 * The approved ration of one horse (REQ-018): everyone with the permission reads it; the Veterinarian composes it.
 * Staff also see the version history; the Owner sees the current version of own horses only (BR-142).
 */
export default function RationPage() {
  const id = Number(useParams().id);
  const { can } = useAuth();
  const allowed = can('VIEW_RATION');
  const compose = can('COMPOSE_RATION');
  const load = useLoad(() => api.ration(id), [id], allowed);
  const options = useLoad(() => api.rationOptions(), [], allowed && compose);

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  const ration = load.state.data;
  const crumbs = [
    { label: vi.horses.title, to: '/horses' },
    ...(ration ? [{ label: ration.horseName, to: `/horses/${id}` }] : []),
    { label: vi.ration.title },
  ];

  return (
    <>
      <PageHeader
        title={`${vi.ration.title}${ration ? ` — ${ration.horseName}` : ''}`}
        description={vi.ration.description}
        crumbs={crumbs}
      />
      <DataView load={load}>
        {(r) => (
          <div className="grid gap-8">
            {r.archived && (
              <Callout tone="info" icon={Info}>
                {vi.ration.archivedNotice}
              </Callout>
            )}
            {r.current === null ? (
              <EmptyState
                icon={UtensilsCrossed}
                title={vi.ration.none}
                text={compose && !r.archived ? vi.ration.prompt : vi.ration.noneHint}
              />
            ) : (
              <section aria-label={vi.ration.current} className="grid gap-3">
                <h2 className="text-h2">{vi.ration.current}</h2>
                <p className="text-meta text-muted-foreground">
                  {vi.ration.version(r.current.version)} · {vi.ration.effective(r.current.effectiveDate)} ·{' '}
                  {vi.ration.savedBy(r.current.savedByName)}
                </p>
                <MealList meals={r.current.meals} />
              </section>
            )}
            {compose && !r.archived && (
              <section aria-label={vi.ration.edit} className="grid gap-3">
                <h2 className="text-h2">{vi.ration.edit}</h2>
                {options.state.status === 'error' ? (
                  <Callout tone="danger" icon={CircleAlert} role="alert">
                    {options.state.error}
                  </Callout>
                ) : (
                  <Editor horseId={id} current={r.current} options={options.state.data ?? []} onSaved={load.set} />
                )}
              </section>
            )}
            {r.history !== null && (
              <section aria-label={vi.ration.history} className="grid gap-3">
                <h2 className="text-h2">{vi.ration.history}</h2>
                {r.history.length <= 1 ? (
                  <p className="text-sm text-muted-foreground">{vi.ration.historyEmpty}</p>
                ) : (
                  <ul className="grid gap-4">
                    {r.history.slice(1).map((v) => (
                      <li key={v.version} className="grid gap-2">
                        <p className="text-meta text-muted-foreground">
                          {vi.ration.version(v.version)} · {vi.ration.effective(v.effectiveDate)} ·{' '}
                          {vi.ration.savedBy(v.savedByName)}
                        </p>
                        <MealList meals={v.meals} label={vi.ration.version(v.version)} />
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            )}
          </div>
        )}
      </DataView>
    </>
  );
}
