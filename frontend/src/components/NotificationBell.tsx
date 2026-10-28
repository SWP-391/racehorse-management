import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bell, BellOff, Check } from 'lucide-react';
import { api } from '../api/api';
import { vi } from '../i18n/vi';
import { cn } from '@/lib/utils';
import { useLoad } from '../hooks/useLoad';
import { notify } from '../lib/notify';
import { Button } from './ui/button';
import { Pagination } from './ui/data-table';
import { Popover, PopoverClose, PopoverContent, PopoverTrigger } from './ui/menu';
import { StaggerList } from './ui/stagger-list';
import { ErrorState, ListSkeleton } from './ui/states';

const POLL_MS = 30_000;

/** In-app notifications, polled every 30 seconds (NFR-006, TECH-019). */
export default function NotificationBell() {
  const [page, setPage] = useState(0);
  const inbox = useLoad(() => api.notifications({ page }), [page]);
  const { state, reload } = inbox;
  const data = state.data;

  useEffect(() => {
    const timer = setInterval(() => void reload(), POLL_MS);
    return () => clearInterval(timer);
  }, [reload]);

  async function markRead(id: number) {
    try {
      await api.markRead(id);
      await reload();
    } catch (e) {
      notify.error(e);
    }
  }

  // Unknown is not zero (ui-kit contract 7): no count, and no "empty" message, until the first answer has arrived.
  const label = data ? `${vi.notifications.title} (${data.unread})` : vi.notifications.title;

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={label}>
          <Bell aria-hidden />
          {data && data.unread > 0 && (
            <span
              aria-hidden
              className="absolute top-1 right-1 grid h-4 min-w-4 place-items-center rounded-full bg-destructive px-1 text-[0.625rem] leading-none font-semibold text-destructive-foreground tabular-nums"
            >
              {data.unread > 9 ? '9+' : data.unread}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent aria-label={vi.notifications.title} role="dialog">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <p className="font-display text-lg font-medium">{vi.notifications.title}</p>
          {data && data.unread > 0 && <span className="text-meta text-muted-foreground tabular-nums">{data.unread}</span>}
        </div>
        {state.status === 'error' && (
          <ErrorState className="m-3" message={state.error} onRetry={() => void reload()} />
        )}
        {data === null ? (
          state.status === 'error' ? null : <ListSkeleton rows={3} />
        ) : data.items.length === 0 ? (
          <div className="grid justify-items-center gap-2 px-4 py-8 text-center text-sm text-muted-foreground">
            <BellOff aria-hidden className="size-5" />
            <p>{vi.notifications.empty}</p>
          </div>
        ) : (
          <StaggerList className="max-h-[min(60dvh,28rem)] divide-y overflow-y-auto">
            {data.items.map((n) => (
              <div key={n.id} className={cn('flex items-start gap-3 px-4 py-3', !n.read && 'bg-accent/40')}>
                <span
                  aria-hidden
                  className={cn('mt-1.5 size-2 shrink-0 rounded-full', n.read ? 'bg-transparent' : 'bg-primary')}
                />
                <div className={cn('min-w-0 flex-1 text-sm', !n.read && 'font-medium')}>
                  {n.link ? (
                    <PopoverClose asChild>
                      <Link to={n.link} className="hover:underline">
                        {n.message}
                      </Link>
                    </PopoverClose>
                  ) : (
                    n.message
                  )}
                </div>
                {!n.read && (
                  <Button variant="ghost" size="sm" className="-my-1 shrink-0" onClick={() => void markRead(n.id)}>
                    <Check aria-hidden />
                    {vi.notifications.markRead}
                  </Button>
                )}
              </div>
            ))}
          </StaggerList>
        )}
        {data && data.total > data.size && (
          <div className="border-t p-3">
            <Pagination
              page={data.page + 1}
              total={Math.ceil(data.total / data.size)}
              onPage={(n) => setPage(n - 1)}
            />
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}
