import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Megaphone, MessageSquarePlus, MessagesSquare, Search } from 'lucide-react';
import { api } from '../../api/api';
import type { ThreadRow } from '../../api/types';
import { Avatar } from '../ChatAvatar';
import { Button } from '../ui/button';
import { Badge } from '../ui/display';
import { DataView } from '../ui/data-view';
import { EmptyState } from '../ui/states';
import { useLoad } from '../../hooks/useLoad';
import { vi } from '../../i18n/vi';
import { cn } from '@/lib/utils';
import { clubDate, clubTime } from '../../lib/utils';
import { INBOX_CHANGED } from './events';

const POLL_MS = 30_000;
const PAGE_SIZE = 20;

/** Today shows the time, an older chat the date. */
function when(instant: string): string {
  return clubDate(instant) === clubDate(new Date().toISOString()) ? clubTime(instant) : clubDate(instant).slice(0, 5);
}

/** The chats of the signed-in person, one per person, newest activity first, searchable and paged. */
export function ThreadList({
  activeKey,
  onOpen,
  onNew,
  onFeedback,
}: {
  activeKey: string | null;
  onOpen: (key: string) => void;
  onNew: () => void;
  /** Only a person who is not a Club Manager can send feedback to the Club Manager. */
  onFeedback?: () => void;
}) {
  const t = vi.inbox;
  const [page, setPage] = useState(0);
  const [text, setText] = useState('');
  const [q, setQ] = useState('');
  const list = useLoad(() => api.threads({ page, size: PAGE_SIZE, q }), [page, q]);
  const { reload } = list;

  // The search waits for a pause in typing and returns to the first page.
  useEffect(() => {
    const timer = setTimeout(() => {
      setQ(text.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [text]);

  useEffect(() => {
    const timer = setInterval(() => {
      if (document.visibilityState !== 'hidden') void reload();
    }, POLL_MS);
    const ask = () => void reload();
    window.addEventListener(INBOX_CHANGED, ask);
    return () => {
      clearInterval(timer);
      window.removeEventListener(INBOX_CHANGED, ask);
    };
  }, [reload]);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="grid gap-3 border-b px-4 pt-4 pb-3">
        <div className="flex items-center justify-between gap-2">
          <h1 tabIndex={-1} className="text-xl font-semibold whitespace-nowrap outline-none">
            {t.title}
          </h1>
          <div className="flex items-center gap-2">
            {onFeedback && (
              <Button size="sm" variant="outline" className="border-amber-300 bg-amber-50 text-amber-950 hover:bg-amber-100" onClick={onFeedback}>
                <Megaphone aria-hidden />
                {t.feedback}
              </Button>
            )}
            <Button size="icon" aria-label={t.newMessage} title={t.newMessage} onClick={onNew}>
              <MessageSquarePlus aria-hidden />
            </Button>
          </div>
        </div>
        <label className="relative block">
          <span className="sr-only">{t.search}</span>
          <Search aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
          <input
            type="search"
            value={text}
            maxLength={100}
            placeholder={t.search}
            onChange={(e) => setText(e.target.value)}
            className="h-10 w-full rounded-full border border-input bg-secondary/60 pr-4 pl-9 text-sm placeholder:text-muted-foreground focus-visible:border-ring focus-visible:outline-2 focus-visible:outline-ring/40"
          />
        </label>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        <DataView
          load={list}
          isEmpty={(p) => p.items.length === 0}
          empty={
            q ? (
              <p className="px-4 py-8 text-center text-sm text-muted-foreground">{t.noMatch}</p>
            ) : (
              <EmptyState icon={MessagesSquare} title={t.empty} text={t.emptyText} action={<Button onClick={onNew}>{t.newMessage}</Button>} />
            )
          }
        >
          {(p) => (
            <ul aria-label={t.list} className="m-0 list-none p-0">
              {p.items.map((r: ThreadRow) => (
                <li key={r.key}>
                  <button
                    type="button"
                    aria-current={r.key === activeKey ? 'true' : undefined}
                    onClick={() => onOpen(r.key)}
                    className={cn(
                      'flex w-full items-center gap-3 border-b px-4 py-3 text-left transition-colors hover:bg-secondary/60 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring',
                      r.key === activeKey && 'bg-accent/60',
                    )}
                  >
                    <Avatar name={r.name} />
                    <span className="grid min-w-0 flex-1 grid-cols-[minmax(0,1fr)] gap-0.5">
                      <span className="flex items-baseline gap-2">
                        <span className={cn('min-w-0 flex-1 truncate', r.unread > 0 ? 'font-semibold' : 'font-medium')}>{r.name}</span>
                        <span className={cn('shrink-0 text-meta tabular-nums', r.unread > 0 ? 'font-medium text-foreground' : 'text-muted-foreground')}>
                          {when(r.lastAt)}
                        </span>
                      </span>
                      <span className="flex items-center gap-2">
                        {r.hasFeedback && <Megaphone aria-label={t.feedbackLabel} className="size-3.5 shrink-0 text-amber-600" />}
                        <span className={cn('min-w-0 flex-1 truncate text-sm', r.unread > 0 ? 'font-medium text-foreground' : 'text-muted-foreground')}>
                          {r.preview}
                        </span>
                        {r.unread > 0 && <Badge tone="primary">{r.unread}</Badge>}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
              {p.total > PAGE_SIZE && (
                <li>
                  <nav aria-label={t.pager} className="flex items-center justify-between gap-2 px-3 py-2">
                    <Button variant="ghost" size="icon" aria-label={t.previous} disabled={page === 0} onClick={() => setPage(page - 1)}>
                      <ChevronLeft aria-hidden />
                    </Button>
                    <span className="text-meta text-muted-foreground tabular-nums">{t.pageOf(page + 1, Math.ceil(p.total / PAGE_SIZE))}</span>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={t.next}
                      disabled={(page + 1) * PAGE_SIZE >= p.total}
                      onClick={() => setPage(page + 1)}
                    >
                      <ChevronRight aria-hidden />
                    </Button>
                  </nav>
                </li>
              )}
            </ul>
          )}
        </DataView>
      </div>
    </div>
  );
}
