import { Link } from 'react-router-dom';
import { useState } from 'react';
import { CalendarDays, ClipboardList, Plus, Video } from 'lucide-react';
import { api } from '../api/api';
import type { ScheduleItem } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { usePagedList } from '../hooks/usePagedList';
import { SessionEnd } from './SessionEnd';
import TrialVideosPanel from './TrialVideosPanel';
import { Button } from './ui/button';
import { LongText } from './ui/long-text';
import { Dialog, DialogContent, DialogTitle } from './ui/dialog';
import { DataTable, type DataColumn } from './ui/data-table';
import { DataView } from './ui/data-view';
import { ListFooter } from './ui/list-toolbar';
import { Badge, Section } from './ui/display';
import { EmptyState } from './ui/states';
import { hhmm } from '../i18n/time';
import { vi } from '../i18n/vi';

/**
 * Training on the horse page: staff see the horse's plans; a Horse Owner sees the schedule and the Head Trainer's
 * comments, read-only (PERM-012, BR-082), and watches the videos of completed trial runs (BR-106).
 */
export default function TrainingSection({ horseId, archived, canWrite = true }: { horseId: number; archived: boolean; canWrite?: boolean }) {
  const { me, can } = useAuth();
  const owner = me?.role === 'HORSE_OWNER';
  const allowed = can('VIEW_PLANS');
  const [openVideos, setOpenVideos] = useState<number | null>(null);
  // The horse page can carry other lists, so the paging parameters of these two have their own prefixes.
  const schedule = usePagedList((query) => api.schedule(horseId, query), { prefix: 's', fixed: { horse: horseId }, enabled: allowed && owner });
  const plans = usePagedList((query) => api.plansOf(horseId, query), { prefix: 'p', fixed: { horse: horseId }, enabled: allowed && !owner });

  if (!allowed) return null;

  if (owner) {
    const columns: DataColumn<ScheduleItem>[] = [
      { id: 'date', header: vi.training.date, primary: true, cell: (s) => <span className="tabular">{s.date}</span> },
      { id: 'time', header: vi.training.startTime, cell: (s) => <span className="tabular">{hhmm(s.startTime)}</span> },
      { id: 'type', header: vi.training.type, cell: (s) => vi.training.types[s.type] },
      { id: 'end', header: vi.training.endTime, cell: (s) => <SessionEnd plannedEnd={s.plannedEnd} plannedEndNextDay={s.plannedEndNextDay} endedAt={s.endedAt} date={s.date} /> },
      { id: 'state', header: vi.mySessions.state, cell: (s) => vi.training.sessionStates[s.state] },
      { id: 'comment', header: vi.training.comment, cell: (s) => (s.comment ? <LongText text={s.comment} title={vi.training.comment} subject={s.date} /> : vi.common.none) },
      {
        id: 'videos',
        header: vi.videos.title,
        actions: true,
        cell: (s) => {
          const completedTrial = s.type === 'TRIAL_RUN' && (s.state === 'COMPLETED' || s.state === 'AUTO_CLOSED');
          return completedTrial && can('WATCH_TRIAL_VIDEOS') ? (
            <Button variant="outline" size="sm" onClick={() => setOpenVideos(s.id)}>
              <Video aria-hidden />
              {vi.videos.show} ({vi.videos.count(s.videoCount)})
            </Button>
          ) : null;
        },
      },
    ];
    return (
      <Section id="training-schedule" title={vi.training.schedule2}>
        <DataView
          load={schedule.load}
          isEmpty={(data) => data.items.length === 0}
          empty={<EmptyState icon={CalendarDays} title={vi.training.noSessions} />}
        >
          {(data) => (
            <div className="grid gap-3">
              <DataTable caption={vi.training.schedule2} data={data.items} columns={columns} rowKey={(s) => s.id} />
              <ListFooter data={data} onPage={schedule.setPage} onSize={schedule.setSize} />
              <Dialog open={openVideos !== null} onOpenChange={(isOpen) => !isOpen && setOpenVideos(null)}>
                <DialogContent className="max-w-3xl" aria-describedby={undefined}>
                  <DialogTitle className="sr-only">{vi.videos.title}</DialogTitle>
                  {openVideos !== null && <TrialVideosPanel sessionId={openVideos} completed />}
                </DialogContent>
              </Dialog>
            </div>
          )}
        </DataView>
      </Section>
    );
  }

  const newPlan = can('MANAGE_PLANS') && canWrite && !archived && (
    <Button variant="outline" asChild>
      <Link to={`/horses/${horseId}/plans/new`}>
        <Plus aria-hidden />
        {vi.training.newPlan}
      </Link>
    </Button>
  );

  return (
    <DataView
      load={plans.load}
      isEmpty={(data) => data.items.length === 0}
      empty={
        <Section id="training-plans" title={vi.training.plans}>
          <EmptyState icon={ClipboardList} title={vi.training.noPlan} text={vi.training.noPlanHint} action={newPlan || undefined} />
        </Section>
      }
    >
      {(data) => (
        <Section id="training-plans" title={vi.training.plans} actions={newPlan || undefined}>
          <ul className="m-0 grid list-none divide-y rounded-lg border bg-card p-0">
            {data.items.map((p) => (
              <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
                <Link to={`/plans/${p.id}`} className="font-display text-[1.0625rem] font-medium hover:text-primary hover:underline">
                  {p.title ?? `${vi.training.plan} #${p.id}`}
                </Link>
                <Badge tone={p.state === 'ACTIVE' ? 'primary' : 'neutral'}>{vi.training.planStates[p.state]}</Badge>
              </li>
            ))}
          </ul>
          <ListFooter data={data} onPage={plans.setPage} onSize={plans.setSize} />
        </Section>
      )}
    </DataView>
  );
}
