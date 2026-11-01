import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';
import { DATE_LIMITS } from '@/lib/validate';
import { Input } from './input';

type Base = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'min' | 'max' | 'step' | 'value' | 'onChange'> & {
  value: string | number;
  onChange: (value: string) => void;
};

interface NumberInputProps extends Base {
  /** The rule's bounds travel with the control, so a number field cannot be written without them (input-validation). */
  min: number;
  max: number;
  /** Whole numbers only (step 1) unless `step` says otherwise. */
  integer?: boolean;
  step?: number | 'any';
  /** Shown after the value and exposed as `data-unit`: m, min, bpm, km/h, kg, VND… (DEC-026). */
  unit?: string;
  /** The label already names the unit, "Cự ly (m)": keep `data-unit` for tests and tools but draw no second one. */
  unitInLabel?: boolean;
}

/** A number field with its range, step and unit; the wheel never changes the value by accident. */
export const NumberInput = forwardRef<HTMLInputElement, NumberInputProps>(function NumberInput(
  { min, max, integer, step, unit, unitInLabel, value, onChange, onKeyDown, className, ...props },
  ref,
) {
  const input = (
    <Input
      ref={ref}
      type="number"
      inputMode={integer ? 'numeric' : 'decimal'}
      min={min}
      max={max}
      step={step ?? (integer ? 1 : 'any')}
      data-unit={unit}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onWheel={(e) => e.currentTarget.blur()}
      // The browser would let a number field hold "1e3" or "+5"; a whole-number field never takes those keys (BR-197).
      onKeyDown={(e) => {
        if (integer && ['e', 'E', '+', '.', ','].includes(e.key)) e.preventDefault();
        onKeyDown?.(e);
      }}
      className={cn(unit && !unitInLabel && 'pr-14', className)}
      {...props}
    />
  );
  if (!unit || unitInLabel) return input;
  return (
    <div className="relative">
      {input}
      <span aria-hidden className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-meta text-muted-foreground">
        {unit}
      </span>
    </div>
  );
});

interface DateInputProps extends Base {
  min?: string;
  max?: string;
}

/** A day field. The bounds default to the plausible years, and the page narrows them (not in the future, after the start…). */
export const DateInput = forwardRef<HTMLInputElement, DateInputProps>(function DateInput(
  { min = DATE_LIMITS.min, max = DATE_LIMITS.max, value, onChange, ...props },
  ref,
) {
  return <Input ref={ref} type="date" min={min} max={max} value={value} onChange={(e) => onChange(e.target.value)} {...props} />;
});
