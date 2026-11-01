import { useState, type ReactNode } from 'react';
import { Eye, Inbox } from 'lucide-react';
import { vi } from '@/i18n/vi';
import { Button } from './button';
import { EmptyState, ErrorState, Skeleton } from './states';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from './dialog';

interface DetailPopupProps {
  /** What the popup shows, e.g. "Xu hướng phong độ"; it names the eye button and titles the popup. */
  title: string;
  /** Names the row, so each eye button tells which row it opens ("… — Bạch Mã"). */
  subject?: string;
  /** What the cell shows in short, next to the eye button (a count, a status); optional. */
  summary?: ReactNode;
  children: ReactNode;
  /** Nothing to show: the popup says so with the kit empty state instead of an empty frame (BR-198). */
  empty?: boolean;
  /** What is empty, e.g. "Chưa có buổi tập nào"; defaults to the popup title. */
  emptyText?: string;
  /** The data of the popup is still loading, or failed with `error` (a retry runs `onRetry`) (AC-398). */
  loading?: boolean;
  error?: string;
  onRetry?: () => void;
  /** Wider popup for tables and charts. */
  wide?: boolean;
}

/**
 * A long cell value (a chart, a list of sessions, a description) kept out of a narrow column: the cell shows an eye
 * button, and the value opens in a popup. The content mounts only while the popup is open.
 */
export function DetailPopup({ title, subject, summary, children, empty, emptyText, loading, error, onRetry, wide }: DetailPopupProps) {
  const [open, setOpen] = useState(false);
  const name = subject ? `${title} — ${subject}` : title;
  return (
    <>
      <span className="inline-flex items-center gap-2">
        {summary}
        <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)} aria-label={`${vi.common.view}: ${name}`}>
          <Eye aria-hidden />
          {vi.common.view}
        </Button>
      </span>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className={wide ? 'max-w-3xl' : 'max-w-xl'}>
          <DialogHeader>
            <DialogTitle>{name}</DialogTitle>
            <DialogDescription className="sr-only">{name}</DialogDescription>
          </DialogHeader>
          {error ? (
            <ErrorState message={error} onRetry={onRetry} />
          ) : loading ? (
            <Skeleton className="h-24" aria-label={vi.common.loading} />
          ) : empty ? (
            <EmptyState icon={Inbox} title={emptyText ?? name} />
          ) : (
            children
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
