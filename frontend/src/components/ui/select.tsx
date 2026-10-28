import { forwardRef, type SelectHTMLAttributes } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import { fieldClass } from './input';

/**
 * Styled native `<select>` (TECH-072): keeps the system picker on phones and every
 * `selectOptions` / `selectOption` test working. Use `<option>` children as usual.
 */
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { className, children, ...props },
  ref,
) {
  return (
    <span className={cn('relative block min-w-0', className)}>
      <select ref={ref} className={cn(fieldClass, 'h-10 appearance-none pr-9 lg:[@media(pointer:fine)]:h-9')} {...props}>
        {children}
      </select>
      <ChevronDown
        aria-hidden
        className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground"
      />
    </span>
  );
});
