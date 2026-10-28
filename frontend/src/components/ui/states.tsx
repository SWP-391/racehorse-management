import type { HTMLAttributes, ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { CircleAlert, RotateCw } from 'lucide-react';
import { cn } from '@/lib/utils';
import { vi } from '@/i18n/vi';
import { Button } from './button';

/** Shimmering placeholder shaped like the content it stands for (motion.md § Rules: 1.2 s linear). */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden
      className={cn(
        'rounded-md bg-[linear-gradient(90deg,var(--muted)_0%,var(--card)_50%,var(--muted)_100%)] bg-[length:200%_100%] animate-[shimmer_1.2s_linear_infinite]',
        className,
      )}
      {...props}
    />
  );
}

/** Skeleton layouts per page type, so nothing jumps when the content lands (CLS < 0.1). */
export function ListSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <div role="status" aria-label={vi.common.loading} className="grid gap-0 rounded-lg border bg-card">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-4 border-b px-4 py-3 last:border-b-0">
          <Skeleton className="h-4 w-1/4" />
          <Skeleton className="hidden h-4 w-1/6 md:block" />
          <Skeleton className="hidden h-4 w-1/5 md:block" />
          <Skeleton className="ml-auto h-4 w-16" />
        </div>
      ))}
    </div>
  );
}

export function DetailSkeleton() {
  return (
    <div role="status" aria-label={vi.common.loading} className="grid gap-6">
      <div className="grid gap-3">
        <Skeleton className="h-9 w-2/5" />
        <Skeleton className="h-4 w-3/5" />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-12" />
        ))}
      </div>
    </div>
  );
}

export function StatsSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div role="status" aria-label={vi.common.loading} className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} className="h-28" />
      ))}
    </div>
  );
}

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  text?: ReactNode;
  /** The next step that makes data appear (ui-kit contract 2). */
  action?: ReactNode;
  className?: string;
}

export function EmptyState({ icon: Icon, title, text, action, className }: EmptyStateProps) {
  return (
    <div className={cn('grid justify-items-center gap-3 rounded-lg border border-dashed px-6 py-12 text-center', className)}>
      <span className="grid size-11 place-items-center rounded-full bg-secondary text-muted-foreground">
        <Icon aria-hidden className="size-5" />
      </span>
      <p className="font-display text-lg font-medium">{title}</p>
      {text && <p className="max-w-[48ch] text-sm text-muted-foreground">{text}</p>}
      {action && <div className="pt-1">{action}</div>}
    </div>
  );
}

/** Failed load: the service's message and a Retry (ui-kit contract 2). */
export function ErrorState({ message, onRetry, className }: { message: string; onRetry?: () => void; className?: string }) {
  return (
    <div
      role="alert"
      className={cn('flex flex-col gap-3 rounded-lg border border-destructive/30 bg-status-injured px-4 py-4 sm:flex-row sm:items-center', className)}
    >
      <CircleAlert aria-hidden className="size-5 shrink-0 text-status-injured-ink" />
      <div className="grid flex-1 gap-0.5 text-status-injured-ink">
        <p className="font-medium">{vi.common.loadFailed}</p>
        <p className="text-sm">{message}</p>
      </div>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RotateCw aria-hidden />
          {vi.common.retry}
        </Button>
      )}
    </div>
  );
}

/** Inline refusal or warning next to a form or action (replaces the old `.error` / `.notice` lines). */
export function Callout({
  tone = 'info',
  icon: Icon,
  children,
  className,
  role,
}: {
  tone?: 'info' | 'warning' | 'danger' | 'success';
  icon?: LucideIcon;
  children: ReactNode;
  className?: string;
  role?: 'alert' | 'status';
}) {
  const tones = {
    info: 'border-border bg-secondary text-foreground',
    warning: 'border-warning/50 bg-status-watch text-status-watch-ink',
    danger: 'border-destructive/30 bg-status-injured text-status-injured-ink',
    success: 'border-success/30 bg-status-eligible text-status-eligible-ink',
  } as const;
  return (
    <div role={role} className={cn('flex items-start gap-2.5 rounded-md border px-3 py-2.5 text-sm', tones[tone], className)}>
      {Icon && <Icon aria-hidden className="mt-0.5 size-4 shrink-0" />}
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
