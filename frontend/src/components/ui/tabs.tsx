import { forwardRef, type ComponentPropsWithoutRef, type ElementRef, type ReactNode } from 'react';
import * as TabsPrimitive from '@radix-ui/react-tabs';
import { cn } from '@/lib/utils';

export const Tabs = TabsPrimitive.Root;

export const TabsList = forwardRef<ElementRef<typeof TabsPrimitive.List>, ComponentPropsWithoutRef<typeof TabsPrimitive.List>>(
  function TabsList({ className, ...props }, ref) {
    return (
      <TabsPrimitive.List
        ref={ref}
        className={cn('-mx-4 flex gap-1 overflow-x-auto overflow-y-hidden border-b px-4 pb-px [scrollbar-width:none] md:mx-0 md:px-0', className)}
        {...props}
      />
    );
  },
);

const tabClass =
  'relative inline-flex h-10 shrink-0 items-center gap-1.5 px-3 text-sm font-medium whitespace-nowrap text-muted-foreground transition-colors duration-[var(--dur-fast)] after:absolute after:inset-x-2 after:-bottom-px after:h-0.5 after:scale-x-0 after:rounded-full after:bg-primary after:transition-transform after:duration-[var(--dur-base)] after:ease-[var(--ease-out)] hover:text-foreground focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring';

export const TabsTrigger = forwardRef<ElementRef<typeof TabsPrimitive.Trigger>, ComponentPropsWithoutRef<typeof TabsPrimitive.Trigger>>(
  function TabsTrigger({ className, ...props }, ref) {
    return (
      <TabsPrimitive.Trigger
        ref={ref}
        className={cn(tabClass, 'data-[state=active]:text-foreground data-[state=active]:after:scale-x-100', className)}
        {...props}
      />
    );
  },
);

export const TabsContent = forwardRef<ElementRef<typeof TabsPrimitive.Content>, ComponentPropsWithoutRef<typeof TabsPrimitive.Content>>(
  function TabsContent({ className, ...props }, ref) {
    return <TabsPrimitive.Content ref={ref} className={cn('pt-6 outline-none', className)} {...props} />;
  },
);

interface FilterTabsProps<T extends string> {
  label: string;
  value: T;
  onChange: (value: T) => void;
  options: { value: T; label: string; count?: ReactNode }[];
  className?: string;
}

/**
 * `<Tabs asFilter>` of the kit (contract 10): switches a list without tab panels, so it is a group of
 * pressed buttons, not a tablist with `aria-controls` pointing at panels that do not exist.
 */
export function FilterTabs<T extends string>({ label, value, onChange, options, className }: FilterTabsProps<T>) {
  return (
    <div role="group" aria-label={label} className={cn('-mx-4 flex gap-1 overflow-x-auto overflow-y-hidden border-b px-4 pb-px [scrollbar-width:none] md:mx-0 md:px-0', className)}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          aria-pressed={o.value === value}
          onClick={() => onChange(o.value)}
          className={cn(tabClass, 'aria-pressed:text-foreground aria-pressed:after:scale-x-100')}
        >
          {o.label}
          {o.count !== undefined && <span className="tabular rounded-sm bg-secondary px-1.5 text-meta">{o.count}</span>}
        </button>
      ))}
    </div>
  );
}
