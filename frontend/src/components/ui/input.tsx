import { forwardRef, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

export const fieldClass =
  'w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm text-foreground transition-[border-color,box-shadow] duration-[var(--dur-fast)] placeholder:text-muted-foreground hover:border-muted-foreground/60 focus-visible:border-ring focus-visible:outline-2 focus-visible:outline-offset-0 focus-visible:outline-ring/40 disabled:cursor-not-allowed disabled:bg-secondary disabled:opacity-70 aria-[invalid=true]:border-destructive';

/** A text field never takes more than this unless the page states the service's own limit (input-validation). */
export const TEXT_MAX = 255;
export const TEXTAREA_MAX = 2000;
const TEXT_TYPES = [undefined, 'text', 'search', 'email', 'tel', 'url'];

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, type, maxLength, ...props },
  ref,
) {
  return (
    <input
      ref={ref}
      type={type}
      maxLength={maxLength ?? (TEXT_TYPES.includes(type) ? TEXT_MAX : undefined)}
      className={cn(
        fieldClass,
        'h-10 lg:[@media(pointer:fine)]:h-9',
        type === 'file' &&
          'h-auto py-2 file:mr-3 file:rounded-sm file:border-0 file:bg-secondary file:px-3 file:py-1 file:text-sm file:font-medium',
        className,
      )}
      {...props}
    />
  );
});

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea(
  { className, ...props },
  ref,
) {
  return <textarea ref={ref} maxLength={props.maxLength ?? TEXTAREA_MAX} className={cn(fieldClass, 'min-h-24 py-2 leading-relaxed', className)} {...props} />;
});
