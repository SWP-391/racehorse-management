import { forwardRef, useEffect, useState, type ComponentPropsWithoutRef, type ElementRef, type HTMLAttributes, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import * as AvatarPrimitive from '@radix-ui/react-avatar';
import * as SeparatorPrimitive from '@radix-ui/react-separator';
import { cva, type VariantProps } from 'class-variance-authority';
import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { cn } from '@/lib/utils';

/** A card is for objects (an identity card, a stat, a form block), not for every section (docs/ui-direction.md § Layout). */
export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('rounded-lg border bg-card text-card-foreground', className)} {...props} />;
}

export function CardHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('grid gap-1 border-b px-5 py-4', className)} {...props} />;
}

export function CardBody({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-5 py-4', className)} {...props} />;
}

export const badgeVariants = cva(
  'inline-flex w-fit max-w-full min-w-0 items-center gap-1 rounded-sm px-1.5 py-0.5 text-meta leading-tight font-medium xl:whitespace-nowrap [&_svg]:size-3.5 [&_svg]:shrink-0',
  {
    variants: {
      tone: {
        neutral: 'bg-secondary text-secondary-foreground',
        outline: 'border bg-card text-muted-foreground',
        primary: 'bg-accent text-accent-foreground',
        eligible: 'bg-status-eligible text-status-eligible-ink',
        watch: 'bg-status-watch text-status-watch-ink',
        injured: 'bg-status-injured text-status-injured-ink',
        quarantine: 'bg-status-quarantine text-status-quarantine-ink',
        locked: 'bg-status-locked text-status-locked-ink',
        simulated: 'bg-status-simulated text-status-simulated-ink',
      },
    },
    defaultVariants: { tone: 'neutral' },
  },
);

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {
  icon?: ReactNode;
}

export function Badge({ className, tone, icon, children, ...props }: BadgeProps) {
  return (
    <span className={cn(badgeVariants({ tone }), className)} {...props}>
      {icon}
      {children}
    </span>
  );
}

export const Avatar = forwardRef<ElementRef<typeof AvatarPrimitive.Root>, ComponentPropsWithoutRef<typeof AvatarPrimitive.Root>>(
  function Avatar({ className, ...props }, ref) {
    return (
      <AvatarPrimitive.Root
        ref={ref}
        className={cn('relative flex size-9 shrink-0 overflow-hidden rounded-full border bg-secondary', className)}
        {...props}
      />
    );
  },
);

export const AvatarFallback = forwardRef<
  ElementRef<typeof AvatarPrimitive.Fallback>,
  ComponentPropsWithoutRef<typeof AvatarPrimitive.Fallback>
>(function AvatarFallback({ className, ...props }, ref) {
  return (
    <AvatarPrimitive.Fallback
      ref={ref}
      className={cn('flex size-full items-center justify-center font-display text-sm font-semibold text-foreground', className)}
      {...props}
    />
  );
});

export const Separator = forwardRef<ElementRef<typeof SeparatorPrimitive.Root>, ComponentPropsWithoutRef<typeof SeparatorPrimitive.Root>>(
  function Separator({ className, orientation = 'horizontal', decorative = true, ...props }, ref) {
    return (
      <SeparatorPrimitive.Root
        ref={ref}
        orientation={orientation}
        decorative={decorative}
        className={cn('shrink-0 bg-border', orientation === 'horizontal' ? 'h-px w-full' : 'h-full w-px', className)}
        {...props}
      />
    );
  },
);

export interface Crumb {
  label: string;
  to?: string;
}

export function Breadcrumb({ items, className }: { items: Crumb[]; className?: string }) {
  return (
    <nav aria-label="breadcrumb" className={cn('min-w-0', className)}>
      <ol className="flex min-w-0 items-center gap-1 overflow-hidden whitespace-nowrap text-meta text-muted-foreground">
        {/* The last crumb (where you are) keeps its room; earlier ones truncate first, so the trail stays on one line. */}
        {items.map((c, i) => (
          <li
            key={`${c.label}-${i}`}
            className={cn('flex min-w-0 items-center gap-1', i === items.length - 1 ? 'max-w-[70%] shrink-0' : 'shrink')}
          >
            {i > 0 && <ChevronRight aria-hidden className="size-3.5 shrink-0" />}
            {c.to && i < items.length - 1 ? (
              <Link to={c.to} className="truncate rounded-sm hover:text-foreground hover:underline">
                {c.label}
              </Link>
            ) : (
              <span aria-current={i === items.length - 1 ? 'page' : undefined} className="truncate text-foreground">
                {c.label}
              </span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}

interface PageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  /** Primary action last; on phones the actions stack under the title, the primary one full width. */
  actions?: ReactNode;
  meta?: ReactNode;
  crumbs?: Crumb[];
  className?: string;
}

/** Page title (the route's focus target), one-line description and actions; a hairline closes it. */
export const TOPBAR_CRUMBS_ID = 'topbar-crumbs';

/** The shell's top strip, once it is in the document (absent in isolated page tests). */
function useTopbarTarget() {
  const [target, setTarget] = useState<HTMLElement | null>(null);
  useEffect(() => setTarget(document.getElementById(TOPBAR_CRUMBS_ID)), []);
  return target;
}

export function PageHeader({ title, description, actions, meta, crumbs, className }: PageHeaderProps) {
  const target = useTopbarTarget();
  // At ≥ lg the trail sits in the top strip (a root page shows its own name there); on phones it stays here.
  const trail = crumbs && crumbs.length > 1 ? crumbs : typeof title === 'string' ? [{ label: title }] : [];
  return (
    <header data-page-header className={cn('mb-6 grid grid-cols-[minmax(0,1fr)] gap-4 border-b pb-5 md:mb-8', className)}>
      {target && trail.length > 0 && createPortal(<Breadcrumb items={trail} />, target)}
      {crumbs && crumbs.length > 1 && <Breadcrumb items={crumbs} className={target ? 'lg:hidden' : undefined} />}
      <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
        <div className="grid min-w-0 gap-1.5">
          <h1 tabIndex={-1} className="text-h1-sm outline-none md:text-h1">
            {title}
          </h1>
          {description && <p className="max-w-[68ch] text-muted-foreground">{description}</p>}
          {meta && <div className="flex flex-wrap items-center gap-2 pt-1">{meta}</div>}
        </div>
        {actions && (
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:flex-wrap sm:items-center xl:justify-end [&>*:last-child]:w-full sm:[&>*:last-child]:w-auto">
            {actions}
          </div>
        )}
      </div>
    </header>
  );
}

/** A ruled section: an H2 with a hairline above, not a boxed card (docs/ui-direction.md § Layout archetype). */
export function Section({
  title,
  actions,
  children,
  className,
  id,
  description,
}: {
  title: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  id?: string;
  description?: ReactNode;
}) {
  return (
    <section data-section aria-labelledby={id} className={cn('grid gap-4 border-t pt-5', className)}>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="grid gap-1">
          <h2 id={id} className="text-h2">
            {title}
          </h2>
          {description && <p className="text-sm text-muted-foreground">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
      {children}
    </section>
  );
}

/** Label/value pairs, two columns on wide screens. */
export function DefinitionList({ items, className }: { items: { term: ReactNode; value: ReactNode }[]; className?: string }) {
  return (
    <dl className={cn('grid gap-x-6 gap-y-3 sm:grid-cols-2', className)}>
      {items.map((item, i) => (
        <div key={i} className="grid min-w-0 gap-0.5 border-b pb-2.5">
          <dt className="text-meta text-muted-foreground">{item.term}</dt>
          <dd className="m-0 min-w-0 break-words">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
