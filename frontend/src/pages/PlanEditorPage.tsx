import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { CircleAlert, Eye, Plus, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import type { Phase } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { useLoad } from '../hooks/useLoad';
import { notify } from '../lib/notify';
import { Button } from '../components/ui/button';
import { DataView } from '../components/ui/data-view';
import { PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { Callout, DetailSkeleton, EmptyState } from '../components/ui/states';
import { vi } from '../i18n/vi';

const emptyPhase = (): Phase => ({
  name: '',
  startDate: '',
  endDate: '',
  goal: '',
  distanceM: '',
  weeklyVolumeKm: '',
  surface: '',
});

const PHASE_FIELDS = [
  ['name', vi.training.phaseName, 'text'],
  ['startDate', vi.training.start, 'date'],
  ['endDate', vi.training.end, 'date'],
  ['goal', vi.training.goal, 'text'],
  ['distanceM', vi.training.distance, 'number'],
  ['weeklyVolumeKm', vi.training.weeklyVolume, 'number'],
  ['surface', vi.training.surface, 'text'],
] as const;

/** Create or edit a plan with ordered phases (BR-030). Shows the new-horse label (BR-009). */
export default function PlanEditorPage() {
  const params = useParams();
  const planId = params.planId ? Number(params.planId) : null;
  const [horseId, setHorseId] = useState<number | null>(params.id ? Number(params.id) : null);
  const navigate = useNavigate();
  const { can } = useAuth();
  const allowed = can('MANAGE_PLANS');
  const [title, setTitle] = useState('');
  const [phases, setPhases] = useState<Phase[]>([emptyPhase()]);
  const [newHorse, setNewHorse] = useState(false);
  const [horseName, setHorseName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  const existing = useLoad(
    async () => {
      const p = await api.plan(planId!);
      setHorseId(p.horseId);
      setTitle(p.title ?? '');
      setPhases(p.phases.length ? p.phases : [emptyPhase()]);
      return p;
    },
    [planId],
    allowed && planId !== null,
  );

  useEffect(() => {
    if (horseId && can('VIEW_HEALTH')) {
      api
        .horseHealth(horseId)
        .then((h) => {
          setNewHorse(h.newUnexamined);
          setHorseName(h.horseName);
        })
        .catch((e) => notify.error(e));
    }
  }, [horseId, can]);

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  function setPhase(i: number, patch: Partial<Phase>) {
    setPhases((list) => list.map((p, j) => (j === i ? { ...p, ...patch } : p)));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setPending(true);
    try {
      const saved = planId ? await api.updatePlan(planId, title, phases) : await api.createPlan(horseId!, title, phases);
      notify.success(vi.common.saved);
      navigate(`/plans/${saved.id}`);
    } catch (err) {
      setError(messageOf(err));
      setFieldErrors(fieldsOf(err));
    } finally {
      setPending(false);
    }
  }

  const pageTitle = planId ? vi.training.plan : vi.training.newPlan;
  const crumbs = [
    { label: vi.horses.title, to: '/horses' },
    ...(horseName && horseId ? [{ label: horseName, to: `/horses/${horseId}` }] : []),
    ...(planId ? [{ label: title || vi.training.plan, to: `/plans/${planId}` }] : []),
    { label: pageTitle },
  ];
  const cancelTo = planId ? `/plans/${planId}` : horseId ? `/horses/${horseId}` : '/horses';

  const formEl = (
    <form onSubmit={submit} noValidate className="grid gap-8">
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert">
          {error}
        </Callout>
      )}
      <FormField label={vi.training.title} error={fieldErrors.title}>
        <Input value={title} onChange={(e) => setTitle(e.target.value)} />
      </FormField>
      <Section id="editor-phases" title={vi.training.phases}>
        <div className="grid gap-6">
          {phases.map((p, i) => (
            <fieldset key={i} className="grid gap-5 rounded-lg border bg-card p-5">
              <legend className="px-1 font-medium">
                {vi.training.phases} {i + 1}
              </legend>
              <div className="grid gap-5 md:grid-cols-2">
                {PHASE_FIELDS.map(([key, label, type]) => (
                  <FormField key={key} label={label} error={fieldErrors[`phases[${i}].${key}`]}>
                    <Input
                      type={type}
                      value={p[key] as string | number}
                      onChange={(e) =>
                        setPhase(i, { [key]: type === 'number' ? (e.target.value === '' ? '' : Number(e.target.value)) : e.target.value })
                      }
                    />
                  </FormField>
                ))}
              </div>
            </fieldset>
          ))}
          <div>
            <Button variant="outline" onClick={() => setPhases([...phases, emptyPhase()])}>
              <Plus aria-hidden />
              {vi.training.addPhase}
            </Button>
          </div>
        </div>
      </Section>
      <div className="flex flex-col-reverse gap-2 border-t pt-6 sm:flex-row sm:justify-end">
        <Button variant="outline" asChild>
          <Link to={cancelTo}>{vi.common.cancel}</Link>
        </Button>
        <Button type="submit" loading={pending}>
          {vi.common.save}
        </Button>
      </div>
    </form>
  );

  return (
    <>
      <PageHeader title={pageTitle} crumbs={crumbs} />
    <div className="grid max-w-3xl gap-6">
      {newHorse && (
        <Callout tone="warning" icon={Eye}>
          {vi.health.newHorse}
        </Callout>
      )}
      {planId ? (
        <DataView load={existing} skeleton={<DetailSkeleton />}>
          {() => formEl}
        </DataView>
      ) : (
        formEl
      )}
    </div>
    </>
  );
}
