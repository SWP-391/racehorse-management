import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react';
import { CircleAlert } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Label } from './label';

interface FormFieldProps {
  label: ReactNode;
  /** One control (Input, Select, Textarea, …); it receives id, aria-describedby and aria-invalid. */
  children: ReactElement;
  description?: ReactNode;
  /** The service's field message (ApiError.fields) or a client check; announced with the control. */
  error?: string | null;
  className?: string;
  id?: string;
}

/** Label + control + description + error, wired for screen readers (ui-kit § Inputs, TECH-074). */
export function FormField({ label, children, description, error, className, id }: FormFieldProps) {
  const auto = useId();
  const controlId = id ?? (isValidElement<{ id?: string }>(children) ? children.props.id : undefined) ?? auto;
  const descriptionId = description ? `${controlId}-description` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const describedBy = [descriptionId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={cn('grid min-w-0 content-start gap-1.5', className)}>
      <Label htmlFor={controlId}>{label}</Label>
      {cloneElement(children as ReactElement<Record<string, unknown>>, {
        id: controlId,
        'aria-describedby': describedBy,
        'aria-invalid': error ? true : undefined,
      })}
      {description && (
        <p id={descriptionId} className="text-meta text-muted-foreground">
          {description}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="flex items-start gap-1.5 text-meta text-status-injured-ink">
          <CircleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}
