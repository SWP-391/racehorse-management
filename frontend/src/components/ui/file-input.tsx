import { forwardRef, useState, type ChangeEvent, type InputHTMLAttributes } from 'react';
import { Paperclip } from 'lucide-react';
import { cn } from '@/lib/utils';
import { vi } from '@/i18n/vi';

/**
 * File picker in the app's language: the real <input type="file"> covers a button-looking face, so the browser's
 * own "Choose Files / No file chosen" (in the browser's language) never shows. The input keeps its label and its
 * focus, so FormField, `getByLabel(...).setInputFiles` and keyboard use work unchanged.
 */
export const FileInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { buttonLabel?: string }>(
  function FileInput({ className, buttonLabel = vi.common.chooseFile, onChange, ...props }, ref) {
    const [names, setNames] = useState<string[]>([]);
    const handle = (e: ChangeEvent<HTMLInputElement>) => {
      setNames(Array.from(e.target.files ?? []).map((f) => f.name));
      onChange?.(e);
    };
    return (
      <span
        className={cn(
          'relative flex min-h-10 min-w-0 items-center gap-3 rounded-md border border-dashed border-input bg-card px-3 py-2 text-sm transition-colors duration-[var(--dur-fast)] focus-within:border-ring focus-within:outline-2 focus-within:outline-ring/40 hover:border-muted-foreground/60',
          className,
        )}
      >
        <span aria-hidden className="inline-flex shrink-0 items-center gap-1.5 rounded-sm bg-secondary px-2.5 py-1 font-medium">
          <Paperclip className="size-4" />
          {buttonLabel}
        </span>
        <span aria-hidden className="min-w-0 truncate text-muted-foreground">
          {names.length === 0 ? vi.common.noFileChosen : names.join(', ')}
        </span>
        <input
          ref={ref}
          type="file"
          className="absolute inset-0 size-full cursor-pointer opacity-0"
          onChange={handle}
          {...props}
        />
      </span>
    );
  },
);
