import { Link } from 'react-router-dom';
import { Archive, BookOpen, Plus, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import type { Horse, Readiness } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { StallLabel } from '../components/StallLabel';
import { useStalls } from '../hooks/useStalls';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { usePagedList } from '../hooks/usePagedList';
import { HorseAvatar } from '../components/HorseAvatar';
import { ReadinessBadge } from '../components/ReadinessBadge';
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
    filters: [...(canFilterHealth ? ['status', 'area'] : ['area']), ...(filterNoTrainer ? ['noTrainer'] : [])],
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
    { id: 'sex', fit: true, header: vi.horses.sex, cell: (h) => (h.sex === 'MALE' ? vi.horses.male : vi.horses.female) },
    { id: 'birth', fit: true, header: vi.horses.birthDate, sortValue: (h) => h.birthDate ?? '', cell: (h) => <span className="tabular">{h.birthDate}</span> },
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
        <ListToolbar query={list.query} onQuery={list.setQuery} placeholder={vi.horses.searchPlaceholder} total={list.load.state.data?.total}>
          {canFilterHealth && (
            <Select
              aria-label={vi.health.status}
              value={list.filters.status ?? ''}
              onChange={(e) => list.setFilter('status', e.target.value)}
              className="w-48"
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
