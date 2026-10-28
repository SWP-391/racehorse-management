import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { cn } from '@/lib/utils';
import CountUp from '../effects/CountUp';

interface StatCardProps {
  label: string;
  /** A count of persisted records the page already loaded; never a computed score (BR-042). */
  value: number;
  icon?: LucideIcon;
  hint?: ReactNode;
  tone?: 'neutral' | 'eligible' | 'watch' | 'injured' | 'locked';
  /** Makes the whole card a link to the filtered list. */
  to?: string;
}

const iconTone = {
  neutral: 'bg-secondary text-muted-foreground',
  eligible: 'bg-status-eligible text-status-eligible-ink',
  watch: 'bg-status-watch text-status-watch-ink',
  injured: 'bg-status-injured text-status-injured-ink',
  locked: 'bg-status-locked text-status-locked-ink',
} as const;

/** KPI tile: the number counts up once on first load (React Bits CountUp, motion.md § Effects layer). */
export function StatCard({ label, value, icon: Icon, hint, tone = 'neutral', to }: StatCardProps) {
  const body = (
    <>
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm text-muted-foreground">{label}</p>
        {Icon && (
          <span className={cn('hidden size-8 shrink-0 place-items-center rounded-md sm:grid', iconTone[tone])}>
            <Icon aria-hidden className="size-4" />
          </span>
        )}
      </div>
      <p className="font-display text-stat leading-none font-medium tabular-nums">
        <CountUp to={value} duration={0.4} trigger="load" />
      </p>
      {hint && <p className="text-meta text-muted-foreground">{hint}</p>}
    </>
  );
  const cls = 'grid min-w-0 content-between gap-3 rounded-lg border bg-card p-4';
  return to ? (
    <Link to={to} className={cn(cls, 'transition-colors duration-[var(--dur-fast)] hover:border-muted-foreground/50 hover:bg-secondary/40')}>
      {body}
    </Link>
  ) : (
    <div className={cls}>{body}</div>
  );
}
