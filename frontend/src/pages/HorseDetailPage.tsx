import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Archive,
  Box,
  ChevronRight,
  Eye,
  CircleAlert,
  ClipboardList,
  Gauge,
  GitFork,
  HeartPulse,
  Pencil,
  RotateCcw,
  Stethoscope,
  UserCog,
  UtensilsCrossed,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import {
  ARCHIVE_REASONS,
  PEDIGREE_POSITIONS,
  type Ancestor,
  type ArchiveReason,
  type Horse,
  type PedigreePosition,
  type Readiness,
} from '../api/types';
import { useAuth } from '../auth/AuthContext';
import PedigreeEditor from '../components/PedigreeEditor';
import { HorseAvatar } from '../components/HorseAvatar';
import HealthPanel from '../components/HealthPanel';
import TrainingSection from '../components/TrainingSection';
import HorseTrainerSection from '../components/HorseTrainerSection';
import { TrainerBanner } from '../components/TrainerBanner';
import ExaminationPanel from '../components/ExaminationPanel';
import CostPanel from '../components/CostPanel';
import ThresholdsPanel from '../components/ThresholdsPanel';
import { ReadinessBadge } from '../components/ReadinessBadge';
import { useLoad } from '../hooks/useLoad';
import { vi } from '../i18n/vi';
import { Button } from '../components/ui/button';
import { Dialog, DialogContent, DialogTitle } from '../components/ui/dialog';
import HorseForm from '../components/HorseForm';
import { StallLabel } from '../components/StallLabel';
import { useStalls } from '../hooks/useStalls';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Badge, Card, CardBody, DefinitionList, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { Select } from '../components/ui/select';
import { Callout, DetailSkeleton, EmptyState } from '../components/ui/states';
import { notify } from '../lib/notify';

/** One tile of the horse page: an icon, what it is, what it holds; a button that opens a popup, or a link to a page. */
function Tile({ icon: Icon, title, hint, onClick, to }: { icon: LucideIcon; title: string; hint: string; onClick?: () => void; to?: string }) {
  const className =
    'group flex min-h-20 items-start gap-3 rounded-lg border bg-card p-4 text-left transition-colors duration-[var(--dur-fast)] hover:border-primary/40 hover:bg-secondary/50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring';
  const body = (
    <>
      <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
        <Icon aria-hidden className="size-5" />
      </span>
      <span className="grid min-w-0 flex-1 gap-0.5">
        <span className="font-medium">{title}</span>
        <span className="text-meta text-muted-foreground">{hint}</span>
      </span>
      <ChevronRight aria-hidden className="mt-1 size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
    </>
  );
  return to ? (
    <Link to={to} className={className}>
      {body}
    </Link>
  ) : (
    <button type="button" onClick={onClick} className={className}>
      {body}
    </button>
  );
}

/** The trainer who writes now, with the main one when a loan is running, and a flag when the account is inactive. */
function trainerLine(horse: Horse): string {
  const now = horse.assignedTrainer;
  if (!now) return vi.assign.noTrainer;
  const loan = horse.temporaryTrainer
    ? ` ${vi.assign.temporaryUntil(horse.temporaryTrainer.until)} · ${vi.assign.mainTrainer}: ${horse.mainTrainer?.displayName ?? vi.common.none}`
    : '';
  return `${now.displayName}${now.active ? '' : ` (${vi.assign.inactive})`}${loan}`;
}

interface Panel {
  key: string;
  /** The group of tiles it sits in: care, training or the horse's record. */
  group: string;
  label: string;
  /** One line saying what the popup holds, so a tile is understood before it is opened. */
  hint: string;
  icon: LucideIcon;
  render: () => ReactNode;
}

interface Profile {
  horse: Horse;
  pedigree: Ancestor[];
  readiness: Readiness | null;
}

/** Profile, pedigree, and (Club Manager) edit, pedigree editing, archive and restore. Archived horses are read-only. */
export default function HorseDetailPage() {
  const id = Number(useParams().id);
  const { can, me } = useAuth();
  const [editingPedigree, setEditingPedigree] = useState(false);
  const [reason, setReason] = useState<ArchiveReason | ''>('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  /** Which panel's popup is open (null: none). The panel mounts only while open, so its data loads on demand. */
  const [openPanel, setOpenPanel] = useState<string | null>(null);
  const [editingProfile, setEditingProfile] = useState(false);
  const stalls = useStalls(can('VIEW_BARN'));

  const profile = useLoad<Profile>(async () => {
    const horse = await api.horse(id);
    const pedigree = await api.pedigree(id);
    const readiness = can('VIEW_READINESS') ? (await api.readiness(id)).readiness : null;
    return { horse, pedigree, readiness };
  }, [id]);

  function report(e: unknown) {
    if (e instanceof ApiError) {
      setError(e.message);
      setFieldErrors(e.fields);
    } else {
      setError(vi.common.unexpectedError);
    }
  }

  function startPedigreeEdit() {
    setEditingPedigree(true);
  }

  async function archive(current: Profile) {
    setError(null);
    setFieldErrors({});
    setPending(true);
    try {
      profile.set({ ...current, horse: await api.archiveHorse(id, reason || null, note) });
      notify.success(vi.common.saved);
    } catch (e) {
      report(e);
    } finally {
      setPending(false);
    }
  }

  async function restore(current: Profile) {
    setError(null);
    setPending(true);
    try {
      profile.set({ ...current, horse: await api.restoreHorse(id) });
      notify.success(vi.common.saved);
    } catch (e) {
      report(e);
    } finally {
      setPending(false);
    }
  }

  return (
    <DataView load={profile} skeleton={<DetailSkeleton />}>
      {(data) => {
        const { horse, pedigree, readiness } = data;
        const manage = can('MANAGE_HORSES') && !horse.archived;
        const byPosition = Object.fromEntries(pedigree.map((a) => [a.position, a]));
        // The service lists all six positions; "empty" means none of them names a horse or a free-text ancestor.
        const pedigreeEmpty = pedigree.every((a) => !a.horse && !a.name);
        const editPedigree = (
          <Button variant="outline" size="sm" onClick={() => void startPedigreeEdit()}>
            {vi.pedigree.edit}
          </Button>
        );
        const pedigreeColumns: DataColumn<PedigreePosition>[] = [
          { id: 'position', header: vi.pedigree.position, primary: true, cell: (p) => vi.pedigree.positions[p] },
          {
            id: 'ancestor',
            header: vi.pedigree.ancestor,
            hideLabelOnMobile: true,
            cell: (p) => {
              const a = byPosition[p];
              return a?.horse ? (
                <Link to={`/horses/${a.horse.id}`} className="text-primary hover:underline">
                  {a.horse.name}
                </Link>
              ) : (
                (a?.name ?? vi.pedigree.unknown)
              );
            },
          },
        ];

        // A tile opens a popup that shows the panel and lets the person change it there. Which tiles exist follows the
        // person's permissions, so no tile opens an empty popup.
        const notOwner = me?.role !== 'HORSE_OWNER';
        const panels: Panel[] = [
          ...(can('VIEW_HEALTH')
            ? [{ key: 'health', group: 'care', label: vi.health.section, hint: vi.horses.hints.health, icon: HeartPulse, render: () => <HealthPanel horseId={id} archived={horse.archived} canWrite={horse.canWrite} /> }]
            : []),
          ...(can('VIEW_EXAMINATIONS') || can('VIEW_READINESS')
            ? [{ key: 'exams', group: 'care', label: vi.vet.examinations, hint: vi.horses.hints.exams, icon: Stethoscope, render: () => <ExaminationPanel horseId={id} archived={horse.archived} /> }]
            : []),
          { key: 'training', group: 'training', label: vi.training.plans, hint: vi.horses.hints.training, icon: ClipboardList, render: () => <TrainingSection horseId={id} archived={horse.archived} canWrite={horse.canWrite} /> },
          ...(notOwner && can('VIEW_PLANS')
            ? [{ key: 'thresholds', group: 'training', label: vi.sim.thresholds, hint: vi.horses.hints.thresholds, icon: Gauge, render: () => <ThresholdsPanel horseId={id} canWrite={horse.canWrite} /> }]
            : []),
          ...(can('ASSIGN_TRAINER') && !horse.archived
            ? [{ key: 'trainer', group: 'training', label: vi.assign.trainer, hint: vi.horses.hints.trainer, icon: UserCog, render: () => <HorseTrainerSection horse={horse} onChanged={(next) => profile.set({ ...data, horse: next })} /> }]
            : []),
          { key: 'pedigree', group: 'record', label: vi.pedigree.title, hint: vi.horses.hints.pedigree, icon: GitFork, render: () => (
                <Section
                  title={vi.pedigree.title}
                  id="pedigree-title"
                  actions={
                    manage && !editingPedigree && !pedigreeEmpty && editPedigree
                  }
                >
                  {editingPedigree ? (
                    <PedigreeEditor
                      pedigree={pedigree}
                      horseId={id}
                      onCancel={() => setEditingPedigree(false)}
                      onSave={async (ancestors) => {
                        profile.set({ ...data, pedigree: await api.updatePedigree(id, ancestors) });
                        notify.success(vi.common.saved);
                        setEditingPedigree(false);
                      }}
                    />
                  ) : pedigreeEmpty ? (
                    <EmptyState icon={GitFork} title={vi.pedigree.empty} action={manage ? editPedigree : undefined} />
                  ) : (
                    <DataTable
                      caption={vi.pedigree.title}
                      data={[...PEDIGREE_POSITIONS]}
                      columns={pedigreeColumns}
                      rowKey={(p) => p}
                    />
                  )}
                </Section>
          ) },
          ...(can('VIEW_BILLING')
            ? [{ key: 'costs', group: 'record', label: vi.billing.horsePanel, hint: vi.horses.hints.costs, icon: Wallet, render: () => <CostPanel horseId={id} /> }]
            : []),
          ...(can('ARCHIVE_HORSES')
            ? [{ key: 'archive', group: 'record', label: vi.horses.archived, hint: horse.archived ? vi.horses.hints.restore : vi.horses.hints.archive, icon: Archive, render: () => (
                  <Section title={vi.horses.archived} id="archive-title">
                    {horse.archived ? (
                      <div>
                        <Button variant="outline" loading={pending} onClick={() => void restore(data)}>
                          <RotateCcw aria-hidden />
                          {vi.horses.restore}
                        </Button>
                      </div>
                    ) : (
                      <div className="grid max-w-xl gap-4">
                        <FormField label={vi.horses.archiveReason} error={fieldErrors.reason}>
                          <Select value={reason} onChange={(e) => setReason(e.target.value as ArchiveReason | '')}>
                            <option value="">{vi.horses.chooseReason}</option>
                            {ARCHIVE_REASONS.map((r) => (
                              <option key={r} value={r}>
                                {vi.horses.reasons[r]}
                              </option>
                            ))}
                          </Select>
                        </FormField>
                        <FormField label={vi.horses.note}>
                          <Input value={note} onChange={(e) => setNote(e.target.value)} />
                        </FormField>
                        <div>
                          <Button variant="destructive-outline" loading={pending} onClick={() => void archive(data)}>
                            {vi.horses.archive}
                          </Button>
                        </div>
                      </div>
                    )}
                  </Section>
            ) }]
            : []),
        ];
        const active = openPanel === 'profile' ? undefined : panels.find((p) => p.key === openPanel);
        const links = [
          ...(can('VIEW_INJURY_MARKS') ? [{ to: `/horses/${id}/injuries`, group: 'care', label: vi.injury.link, hint: vi.horses.hints.injuries, icon: Box }] : []),
          ...(can('VIEW_RATION') ? [{ to: `/horses/${id}/ration`, group: 'care', label: vi.ration.link, hint: vi.horses.hints.ration, icon: UtensilsCrossed }] : []),
        ];
        const showTrainer = can('VIEW_PLANS') && notOwner;

        return (
          <>
            <PageHeader
              title={horse.name}
              crumbs={[{ label: vi.horses.title, to: '/horses' }, { label: horse.name }]}
              actions={
                manage && (
                  <Button
                    variant="outline"
                    onClick={() => {
                      setEditingProfile(true);
                      setOpenPanel('profile');
                    }}
                  >
                    <Pencil aria-hidden />
                    {vi.horses.edit}
                  </Button>
                )
              }
            />

            <Card className="mb-6">
              <CardBody className="flex flex-col gap-5 sm:flex-row sm:items-center">
                <HorseAvatar id={horse.id} name={horse.name} photoVersion={horse.photoVersion} variant="full" className="size-32 shrink-0 text-4xl" />
                <div className="grid min-w-0 flex-1 gap-3">
                  <div className="grid gap-0.5">
                    <p className="text-meta text-muted-foreground">{vi.horses.registrationNumber}</p>
                    <p className="tabular break-all text-xl font-medium">{horse.registrationNumber}</p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    {horse.archived && (
                      <Badge tone="outline" icon={<Archive aria-hidden />}>
                        {vi.horses.archived}
                      </Badge>
                    )}
                    {readiness && <ReadinessBadge value={readiness} />}
                    <span className="inline-flex items-center gap-1 rounded-sm border px-1.5 py-0.5 text-meta">
                      <StallLabel stalls={stalls} horseId={horse.id} />
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {horse.sex === 'MALE' ? vi.horses.male : vi.horses.female} · {horse.owner.displayName}
                    {showTrainer && ` · ${trainerLine(horse)}`}
                  </p>
                </div>
                <Button
                  className="shrink-0"
                  onClick={() => {
                    setEditingProfile(false);
                    setOpenPanel('profile');
                  }}
                >
                  <Eye aria-hidden />
                  {vi.horses.viewProfile}
                </Button>
              </CardBody>
            </Card>

            <div className="grid min-w-0 gap-6">
              {horse.archived && (
                <Callout tone="warning" icon={Archive} role="status">
                  {vi.horses.readOnly} {vi.horses.archiveReason}: {horse.archiveReason && vi.horses.reasons[horse.archiveReason]}
                </Callout>
              )}
              {error && Object.keys(fieldErrors).length === 0 && (
                <Callout tone="danger" icon={CircleAlert} role="alert">
                  {error}
                </Callout>
              )}
              {can('MANAGE_PLANS') && !horse.archived && !horse.canWrite && (
                <TrainerBanner trainer={horse.assignedTrainer} until={horse.temporaryTrainer?.until} />
              )}

              {(['care', 'training', 'record'] as const).map((group) => {
                const tiles = panels.filter((p) => p.group === group);
                const pages = links.filter((l) => l.group === group);
                if (tiles.length + pages.length === 0) return null;
                return (
                  <section key={group} aria-labelledby={`group-${group}`} className="grid gap-3">
                    <h2 id={`group-${group}`} className="text-h3">
                      {vi.horses.groups[group]}
                    </h2>
                    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                      {tiles.map((panel) => (
                        <Tile key={panel.key} icon={panel.icon} title={panel.label} hint={panel.hint} onClick={() => setOpenPanel(panel.key)} />
                      ))}
                      {pages.map((l) => (
                        <Tile key={l.to} icon={l.icon} title={l.label} hint={l.hint} to={l.to} />
                      ))}
                    </div>
                  </section>
                );
              })}
            </div>

            <Dialog open={active !== undefined} onOpenChange={(open) => !open && setOpenPanel(null)}>
              <DialogContent className="max-w-4xl" aria-describedby={undefined}>
                <DialogTitle className="sr-only">{active?.label}</DialogTitle>
                <div className="grid gap-6 pr-6 [&_[data-section]]:border-t-0 [&_[data-section]]:pt-0">{active?.render()}</div>
              </DialogContent>
            </Dialog>

            <Dialog
              open={openPanel === 'profile'}
              onOpenChange={(open) => {
                if (!open) {
                  setOpenPanel(null);
                  setEditingProfile(false);
                }
              }}
            >
              <DialogContent className="max-w-3xl" aria-describedby={undefined}>
                <DialogTitle>{editingProfile ? vi.horses.edit : horse.name}</DialogTitle>
                {editingProfile && manage ? (
                  <HorseForm
                    horseId={id}
                    onCancel={() => setEditingProfile(false)}
                    onSaved={async (_saved, photoFailed) => {
                      void photoFailed;
                      await profile.reload();
                      setEditingProfile(false);
                    }}
                  />
                ) : (
                  <div className="grid gap-5">
                    <div className="flex items-center gap-4">
                      <HorseAvatar id={horse.id} name={horse.name} photoVersion={horse.photoVersion} variant="full" className="size-20 text-2xl" />
                      <p className="tabular text-lg">{horse.registrationNumber}</p>
                    </div>
                    <DefinitionList
                      items={[
                        { term: vi.horses.registrationNumber, value: <span className="tabular">{horse.registrationNumber}</span> },
                        { term: vi.horses.sex, value: horse.sex === 'MALE' ? vi.horses.male : vi.horses.female },
                        { term: vi.horses.birthDate, value: <span className="tabular">{horse.birthDate}</span> },
                        { term: vi.horses.coatColour, value: horse.coatColour ?? vi.common.none },
                        { term: vi.horses.breed, value: horse.breed ?? vi.common.none },
                        { term: vi.horses.owner, value: horse.owner.displayName },
                        ...(showTrainer ? [{ term: vi.assign.trainer, value: trainerLine(horse) }] : []),
                        { term: vi.barn.location, value: <StallLabel stalls={stalls} horseId={horse.id} /> },
                      ]}
                    />
                    {manage && (
                      <div className="flex justify-end">
                        <Button onClick={() => setEditingProfile(true)}>
                          <Pencil aria-hidden />
                          {vi.horses.edit}
                        </Button>
                      </div>
                    )}
                  </div>
                )}
              </DialogContent>
            </Dialog>
          </>
        );
      }}
    </DataView>
  );
}
