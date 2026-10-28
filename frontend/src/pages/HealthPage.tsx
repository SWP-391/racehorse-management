import { Link } from 'react-router-dom';
import { Bandage, CircleCheck, Eye, BookOpen as HorseIcon, Lock, ShieldAlert, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import { HEALTH_STATUSES, type HorseHealth } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { HorseAvatar } from '../components/HorseAvatar';
import { useHorsePhotos } from '../hooks/useHorsePhotos';
import { StallLabel } from '../components/StallLabel';
import { useStalls } from '../hooks/useStalls';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { usePagedList } from '../hooks/usePagedList';
import { HealthBadge, LockBadge } from '../components/StatusBadge';
import { Button } from '../components/ui/button';
import { PageHeader, Section } from '../components/ui/display';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { Select } from '../components/ui/select';
import { Chart } from '../components/chart/Chart';
import { StatCard } from '../components/ui/stat-card';
import { EmptyState, StatsSkeleton } from '../components/ui/states';

const STATUS_CARD = {
  ELIGIBLE: { tone: 'eligible', icon: CircleCheck },
  UNDER_OBSERVATION: { tone: 'watch', icon: Eye },
  INJURED: { tone: 'injured', icon: Bandage },
  QUARANTINED: { tone: 'neutral', icon: ShieldAlert },
} as const;

/** Herd health map: status, lock and activity of every managed horse (BR-010, PERM-020). */
export default function HealthPage() {
  const { can } = useAuth();
  const photoOf = useHorsePhotos();
  const allowed = can('VIEW_HEALTH');
  const list = usePagedList((query) => api.herdHealth(query), { filters: ['status', 'activity'], enabled: allowed });
  const stalls = useStalls(allowed);
  const summary = useLoad(() => api.healthSummary(), [], allowed);

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  const columns: DataColumn<HorseHealth>[] = [
    {
      id: 'name',
      header: vi.horses.name,
      primary: true,
      sortValue: (h) => h.horseName,
      cell: (h) => (
        <span className="inline-flex flex-wrap items-center gap-x-2 gap-y-1">
          <HorseAvatar id={h.horseId} name={h.horseName} photoVersion={photoOf(h.horseId)} className="size-8 shrink-0 text-sm" />
          <Link to={`/horses/${h.horseId}`} className="font-display text-[1.0625rem] font-medium text-foreground hover:text-primary hover:underline">
            {h.horseName}
          </Link>
          {h.newUnexamined && (
            <span className="inline-flex basis-full items-center gap-1 text-meta text-muted-foreground">
              <Eye aria-hidden className="size-3.5" />
              {vi.health.newHorse}
            </span>
          )}
        </span>
      ),
    },
    { id: 'stall', fit: true, header: vi.barn.location, cell: (h) => <StallLabel stalls={stalls} horseId={h.horseId} /> },
    { id: 'status', header: vi.health.status, sortValue: (h) => vi.health.statuses[h.status] ?? h.status, cell: (h) => <HealthBadge status={h.status} /> },
    {
      id: 'lock',
      header: vi.health.lock,
      cell: (h) =>
        h.lock ? <LockBadge>{`${vi.health.lockKinds[h.lock.kind]}: ${h.lock.reason}`}</LockBadge> : <span className="text-muted-foreground">{vi.health.noLock}</span>,
    },
    { id: 'activity', header: vi.health.activity, cell: (h) => vi.health.activities[h.activity] },
  ];

  return (
    <>
      <PageHeader title={vi.health.title} description={vi.health.subtitle} />
      <DataView
        load={summary}
        skeleton={<StatsSkeleton count={6} />}
        isEmpty={(data) => data.total === 0}
        empty={<EmptyState icon={HorseIcon} title={vi.health.empty} text={vi.health.emptyHint} action={
          <Button variant="outline" asChild>
            <Link to="/horses">{vi.health.registerHorse}</Link>
          </Button>
        } />}
      >
        {(counts) => (
          <div className="grid gap-8">
            <div className="grid grid-cols-3 gap-2 sm:gap-3 xl:grid-cols-6">
              <StatCard label={vi.health.total} value={counts.total} icon={HorseIcon} />
              {HEALTH_STATUSES.map((s) => (
                <StatCard
                  key={s}
                  label={vi.health.statuses[s]}
                  value={counts.byStatus[s] ?? 0}
                  icon={STATUS_CARD[s].icon}
                  tone={STATUS_CARD[s].tone}
                />
              ))}
              <StatCard label={vi.health.lockedCount} value={counts.locked} icon={Lock} tone="locked" />
            </div>
            <Section title={vi.health.distribution} id="health-distribution">
              <Chart
                kind="hbar"
                integer
                title={vi.health.distribution}
                height={180}
                categories={HEALTH_STATUSES.map((s) => vi.health.statuses[s])}
                series={[{ name: vi.health.horses, data: HEALTH_STATUSES.map((s) => counts.byStatus[s] ?? 0) }]}
              />
            </Section>
            <div className="grid gap-4">
              <ListToolbar query={list.query} onQuery={list.setQuery} placeholder={vi.health.searchPlaceholder} total={list.load.state.data?.total}>
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
                <Select
                  aria-label={vi.health.activity}
                  value={list.filters.activity ?? ''}
                  onChange={(e) => list.setFilter('activity', e.target.value)}
                  className="w-44"
                >
                  <option value="">{vi.health.anyActivity}</option>
                  {Object.entries(vi.health.activities).map(([code, label]) => (
                    <option key={code} value={code}>
                      {label}
                    </option>
                  ))}
                </Select>
              </ListToolbar>
              <DataView
                load={list.load}
                isEmpty={(data) => data.items.length === 0}
                empty={<EmptyState icon={HorseIcon} title={vi.common.noResults} text={vi.common.noResultsHint} />}
              >
                {(data) => (
                  <div className="grid gap-3">
                    <DataTable caption={vi.health.title} data={data.items} columns={columns} rowKey={(h) => h.horseId} />
                    <ListFooter data={data} onPage={list.setPage} onSize={list.setSize} />
                  </div>
                )}
              </DataView>
            </div>
          </div>
        )}
      </DataView>
    </>
  );
}
