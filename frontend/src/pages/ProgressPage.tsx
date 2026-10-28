import { Link } from 'react-router-dom';
import { ClipboardList, Eye, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import { HEALTH_STATUSES, type BoardRow } from '../api/types';
import TrendChart from '../components/TrendChart';
import { HealthBadge, LockBadge } from '../components/StatusBadge';
import { useAuth } from '../auth/AuthContext';
import { usePagedList } from '../hooks/usePagedList';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { PageHeader } from '../components/ui/display';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { Checkbox } from '../components/ui/choice';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select';
import { EmptyState } from '../components/ui/states';
import { HorseAvatar } from '../components/HorseAvatar';
import { useHorsePhotos } from '../hooks/useHorsePhotos';
import { DetailPopup } from '../components/ui/detail-popup';
import { StallLabel } from '../components/StallLabel';
import { useStalls } from '../hooks/useStalls';
import { vi } from '../i18n/vi';

/** Head Trainer's progress board: status, current plan, recent sessions and trend for each horse. */
export default function ProgressPage() {
  const { me, can } = useAuth();
  const photoOf = useHorsePhotos();
  const allowed = can('VIEW_PROGRESS_BOARD');
  const list = usePagedList((query) => api.progress(query), { filters: ['status', 'trainerId'], enabled: allowed });
  // Off by default: the board shows the whole club (REQ-001); the toggle narrows it to the horses of this trainer.
  const stalls = useStalls(allowed);
  const mine = me !== null && list.filters.trainerId === String(me.id);

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  const columns: DataColumn<BoardRow>[] = [
    {
      id: 'name',
      header: vi.horses.name,
      primary: true,
      sortValue: (r) => r.horseName,
      cell: (r) => (
        <span className="flex items-start gap-2">
          <HorseAvatar id={r.horseId} name={r.horseName} photoVersion={photoOf(r.horseId)} className="size-8 shrink-0 text-sm" />
          <span className="grid gap-0.5">
          <Link to={`/horses/${r.horseId}`} className="font-display text-[1.0625rem] font-medium text-foreground hover:text-primary hover:underline">
            {r.horseName}
          </Link>
          {/* Muted meta under the name, as on the Health table: a chip beside it wrapped at 1440. */}
          {r.newUnexamined && (
            <span className="flex items-start gap-1 text-meta font-normal text-muted-foreground">
              <Eye aria-hidden className="mt-0.5 size-3.5 shrink-0" />
              {vi.health.newHorse}
            </span>
          )}
          </span>
        </span>
      ),
    },
    { id: 'stall', fit: true, header: vi.barn.location, cell: (r) => <StallLabel stalls={stalls} horseId={r.horseId} /> },
    {
      id: 'status',
      header: vi.health.status,
      hideLabelOnMobile: true,
      cell: (r) => (
        <span className="flex flex-wrap items-center gap-1.5">
          <HealthBadge status={r.status} />
          {r.locked && <LockBadge />}
        </span>
      ),
    },
    {
      id: 'trainer',
      header: vi.assign.trainerColumn,
      cell: (r) => r.assignedTrainer?.displayName ?? <span className="text-muted-foreground">{vi.assign.noTrainerFilter}</span>,
    },
    {
      id: 'plan',
      header: vi.training.currentPlan,
      cell: (r) =>
        r.currentPlan ? (
          <Link to={`/plans/${r.currentPlan.id}`} className="text-primary hover:underline">
            {r.currentPlan.title ?? vi.training.plan} ({vi.training.planStates[r.currentPlan.state]})
          </Link>
        ) : (
          <span className="text-muted-foreground">{vi.training.noPlan}</span>
        ),
    },
    {
      id: 'recent',
      header: vi.training.recentSessions,
      fit: true,
      cell: (r) =>
        r.recentSessions.length === 0 ? (
          vi.common.none
        ) : (
          <DetailPopup title={vi.training.recentSessions} subject={r.horseName} summary={<span className="tabular">{r.recentSessions.length}</span>}>
            <ul className="grid gap-2">
              {r.recentSessions.map((s) => (
                <li key={s.id} className="tabular rounded-md border px-3 py-2 text-sm">
                  <span className="font-medium">{s.date}</span>: {vi.training.types[s.type]}, {s.distanceM ?? '—'} m, {vi.training.form} {s.form ?? '—'}
                </li>
              ))}
            </ul>
          </DetailPopup>
        ),
    },
    {
      id: 'trend',
      fit: true,
      header: vi.training.trend,
      hideLabelOnMobile: true,
      cell: (r) =>
        r.trend.length === 0 ? (
          vi.common.none
        ) : (
          <DetailPopup title={vi.training.trend} subject={r.horseName} wide>
            <TrendChart sessions={r.trend} />
          </DetailPopup>
        ),
    },
  ];

  return (
    <>
      <PageHeader title={vi.training.progress} />
      <div className="mb-4">
        <ListToolbar query={list.query} onQuery={list.setQuery} placeholder={vi.training.progressSearch} total={list.load.state.data?.total}>
          <Select
            aria-label={vi.health.status}
            value={list.filters.status ?? ''}
            onChange={(e) => list.setFilter('status', e.target.value)}
            className="w-48"
          >
            <option value="">{vi.horses.anyHealth}</option>
            {HEALTH_STATUSES.map((s) => (
              <option key={s} value={s}>
                {vi.health.statuses[s]}
              </option>
            ))}
          </Select>
          {me && (
            <span className="flex min-h-10 items-center gap-2">
              <Checkbox
                id="board-mine"
                checked={mine}
                onCheckedChange={(checked) => list.setFilter('trainerId', checked === true ? String(me.id) : '')}
              />
              <Label htmlFor="board-mine">{vi.assign.onlyMine}</Label>
            </span>
          )}
        </ListToolbar>
      </div>
      <DataView
        load={list.load}
        isEmpty={(data) => data.items.length === 0}
        empty={
          list.narrowed ? (
            <EmptyState icon={ClipboardList} title={vi.common.noResults} text={vi.common.noResultsHint} />
          ) : (
            <EmptyState icon={ClipboardList} title={vi.training.progressEmpty} text={vi.training.progressEmptyHint} />
          )
        }
      >
        {(data) => (
          <div className="grid gap-3">
            <DataTable caption={vi.training.progress} data={data.items} columns={columns} rowKey={(r) => r.horseId} />
            <ListFooter data={data} onPage={list.setPage} onSize={list.setSize} />
          </div>
        )}
      </DataView>
    </>
  );
}
