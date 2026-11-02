import { Link } from 'react-router-dom';
import { Archive, BookOpen, Plus, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import { READINESS_VALUES, type Horse, type OptionItem, type Readiness } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { StallLabel } from '../components/StallLabel';
import { useStalls } from '../hooks/useStalls';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { usePagedList } from '../hooks/usePagedList';
import { HorseAvatar } from '../components/HorseAvatar';
import { ReadinessBadge } from '../components/ReadinessBadge';
import { HealthBadge } from '../components/StatusBadge';
import { Button } from '../components/ui/button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Badge, PageHeader } from '../components/ui/display';
import { Checkbox } from '../components/ui/choice';
import { Label } from '../components/ui/label';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { Select } from '../components/ui/select';
import { EmptyState, Skeleton } from '../components/ui/states';

/** Horses the user may see: all for staff with PERM-001, own horses for a Horse Owner (BR-006). */
export default function HorsesPage() {
  const { can, me } = useAuth();
  const allowed = can('VIEW_HORSES');
  // A Horse Owner sees only their own horses (BR-006): the owner column would repeat their own name.
  const ownView = me?.role === 'HORSE_OWNER';
  const showReadiness = can('VIEW_READINESS');
  const canFilterHealth = can('VIEW_HEALTH');
  const staffView = can('VIEW_PLANS') && !ownView;
  const filterNoTrainer = can('ASSIGN_TRAINER');
  const list = usePagedList((query) => api.horses(query), {
    filters: [
      ...(canFilterHealth ? ['status', 'area'] : ['area']),
      ...(filterNoTrainer ? ['noTrainer'] : []),
      ...(showReadiness && !ownView ? ['readiness'] : []),
      ...(staffView ? ['trainerId'] : []),
    ],
    enabled: allowed,
  });
  const stalls = useStalls(allowed);
  const readiness = useLoad(
    () =>
      api
        .readinessList()
        .then((list) => Object.fromEntries(list.map((r) => [r.horseId, r.readiness])) as Record<number, Readiness>)
        .catch(() => ({}) as Record<number, Readiness>),
    [],
    allowed && showReadiness,
  );

  const areas = useLoad(() => api.areas().catch(() => []), [], allowed);
  // The Head Trainers to filter by (BR-209); a refused or failed call leaves the select with its "all" choice only.
  const trainers = useLoad(
    () => api.options('trainers', { size: 100 }).then((p) => p.items).catch(() => [] as OptionItem[]),
    [],
    allowed && staffView && filterNoTrainer,
  );

  if (!allowed) return <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />;

  const register = can('MANAGE_HORSES') && (
    <Button asChild>
      <Link to="/horses/new">
        <Plus aria-hidden />
        {vi.horses.register}
      </Link>
    </Button>
  );

  const columns: DataColumn<Horse>[] = [
    {
      id: 'name',
      header: vi.horses.name,
      primary: true,
      sortValue: (h) => h.name,
      cell: (h) => (
        <span className="flex items-center gap-3">
          <HorseAvatar id={h.id} name={h.name} photoVersion={h.photoVersion} />
          <Link to={`/horses/${h.id}`} className="font-display text-[1.0625rem] font-medium text-foreground hover:text-primary hover:underline">
            {h.name}
          </Link>
        </span>
      ),
    },
    { id: 'reg', fit: true, header: vi.horses.registrationNumber, cardLabel: vi.horses.registrationShort, sortValue: (h) => h.registrationNumber, cell: (h) => <span className="tabular">{h.registrationNumber}</span> },
    { id: 'stall', fit: true, header: vi.barn.location, cell: (h) => <StallLabel stalls={stalls} horseId={h.id} /> },
    // Sex and birth date stay on the horse page; the staff list uses the room for health and readiness (BR-209).
    ...(staffView
      ? []
      : [
          { id: 'sex', fit: true, header: vi.horses.sex, cell: (h: Horse) => (h.sex === 'MALE' ? vi.horses.male : vi.horses.female) } satisfies DataColumn<Horse>,
          { id: 'birth', fit: true, header: vi.horses.birthDate, sortValue: (h: Horse) => h.birthDate ?? '', cell: (h: Horse) => <span className="tabular">{h.birthDate}</span> } satisfies DataColumn<Horse>,
        ]),
    ...(canFilterHealth && staffView
      ? [
          {
            id: 'health',
            fit: true,
            header: vi.health.status,
            cell: (h: Horse) => (h.healthStatus ? <HealthBadge status={h.healthStatus} /> : vi.common.none),
          } satisfies DataColumn<Horse>,
        ]
      : []),
    ...(ownView
      ? []
      : [{ id: 'owner', header: vi.horses.owner, sortValue: (h: Horse) => h.owner.displayName, cell: (h: Horse) => h.owner.displayName } satisfies DataColumn<Horse>]),
    ...(staffView
      ? [
          {
            id: 'trainer',
            header: vi.assign.trainerColumn,
            sortValue: (h: Horse) => h.assignedTrainer?.displayName ?? '',
            cell: (h: Horse) =>
              h.assignedTrainer ? (
                <span>
                  {h.assignedTrainer.displayName}
                  {!h.assignedTrainer.active && (
                    <Badge tone="outline" className="ml-2">
                      {vi.assign.inactive}
                    </Badge>
                  )}
                </span>
              ) : (
                <span className="text-muted-foreground">{vi.assign.noTrainerFilter}</span>
              ),
          } satisfies DataColumn<Horse>,
        ]
      : []),
    ...(showReadiness
      ? [
          {
            id: 'readiness', fit: true,
            header: vi.readiness.label,
            cell: (h: Horse) => {
              // Unknown is not "none": while readiness loads, a skeleton slot, never a dash (ui-kit contract 7).
              const map = readiness.state.data;
              if (!map) return <Skeleton className="h-5 w-24" />;
              return map[h.id] ? <ReadinessBadge value={map[h.id]} /> : vi.common.none;
            },
          } satisfies DataColumn<Horse>,
        ]
      : []),
    {
      id: 'archived', fit: true,
      header: '',
      hideLabelOnMobile: true,
      cell: (h) =>
        h.archived ? (
          <Badge tone="outline" icon={<Archive aria-hidden />}>
            {vi.horses.archived}
          </Badge>
        ) : null,
    },
  ];

  return (
    <>
      <PageHeader title={vi.horses.title} description={ownView ? vi.horses.ownSubtitle : vi.horses.subtitle} actions={register} />
      <div className="mb-4">
        <ListToolbar query={list.query} onQuery={list.setQuery} placeholder={ownView ? vi.horses.searchPlaceholderOwn : vi.horses.searchPlaceholder} total={list.load.state.data?.total}>
          {canFilterHealth && (
            <Select
              aria-label={vi.health.status}
              value={list.filters.status ?? ''}
              onChange={(e) => list.setFilter('status', e.target.value)}
              className="w-60"
            >
              <option value="">{vi.horses.anyHealth}</option>
              {Object.entries(vi.health.statuses).map(([code, label]) => (
                <option key={code} value={code}>
                  {label}
                </option>
              ))}
            </Select>
          )}
          <Select aria-label={vi.horses.areaFilter} value={list.filters.area ?? ''} onChange={(e) => list.setFilter('area', e.target.value)} className="w-48">
            <option value="">{vi.horses.anyArea}</option>
            {areas.state.data?.map((a) => (
              <option key={a.id} value={a.name}>
                {a.name}
              </option>
            ))}
          </Select>
          {showReadiness && !ownView && (
            <Select
              aria-label={vi.readiness.label}
              value={list.filters.readiness ?? ''}
              onChange={(e) => list.setFilter('readiness', e.target.value)}
              className="w-52"
            >
              <option value="">{vi.readiness.any}</option>
              {READINESS_VALUES.map((r) => (
                <option key={r} value={r}>
                  {vi.readiness.values[r]}
                </option>
              ))}
            </Select>
          )}
          {staffView && !filterNoTrainer && me && (
            // A Head Trainer may not list the other trainers (403), so the filter is "only mine" (BR-209).
            <span className="flex min-h-10 items-center gap-2">
              <Checkbox
                id="horses-mine"
                checked={list.filters.trainerId === String(me.id)}
                onCheckedChange={(checked) => list.setFilter('trainerId', checked === true ? String(me.id) : '')}
              />
              <Label htmlFor="horses-mine">{vi.assign.onlyMine}</Label>
            </span>
          )}
          {staffView && filterNoTrainer && (
            <Select
              aria-label={vi.assign.trainerColumn}
              value={list.filters.trainerId ?? ''}
              onChange={(e) => list.setFilter('trainerId', e.target.value)}
              className="w-52"
            >
              <option value="">{vi.assign.anyTrainer}</option>
              {(trainers.state.data ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.label}
                </option>
              ))}
            </Select>
          )}
          {filterNoTrainer && (
            <span className="flex min-h-10 items-center gap-2">
              <Checkbox
                id="horses-no-trainer"
                checked={list.filters.noTrainer === 'true'}
                onCheckedChange={(checked) => list.setFilter('noTrainer', checked === true ? 'true' : '')}
              />
              <Label htmlFor="horses-no-trainer">{vi.assign.noTrainerFilter}</Label>
            </span>
          )}
        </ListToolbar>
      </div>
      <DataView
        load={list.load}
        isEmpty={(data) => data.items.length === 0}
        empty={
          list.narrowed ? (
            <EmptyState icon={BookOpen} title={vi.common.noResults} text={vi.common.noResultsHint} />
          ) : (
            <EmptyState icon={BookOpen} title={vi.horses.empty} text={vi.horses.emptyHint} action={register} />
          )
        }
      >
        {(data) => (
          <div className="grid gap-3">
            <DataTable
              caption={vi.horses.title}
              data={data.items}
              columns={columns}
              rowKey={(h) => h.id}
              rowClassName={(h) => (h.archived ? 'text-muted-foreground' : undefined)}
            />
            <ListFooter data={data} onPage={list.setPage} onSize={list.setSize} />
          </div>
        )}
      </DataView>
    </>
  );
}
