import { forwardRef, useEffect, useState, type InputHTMLAttributes } from 'react';
import { vi } from '@/i18n/vi';
import { MONEY_UNITS, fromDong, groupDong, toDong } from '@/lib/money';
import { Input } from './input';
import { Select } from './select';

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'value' | 'onChange' | 'min' | 'max' | 'step'> & {
  /** Whole dong as text ('' = empty); this is also what `onChange` gives, so the service and the checks see dong. */
  value: string;
  onChange: (dong: string) => void;
  /** A deduction (a negative amount) is typed only where the caller allows it (BR-207). */
  allowNegative?: boolean;
};

/**
 * An amount of money: a number, a unit (đồng, nghìn đồng by default, triệu đồng) and the full amount under it
 * ("= 1.500.000 VND"). The value is whole dong, so no API changes (DEC-070).
 */
export const MoneyInput = forwardRef<HTMLInputElement, Props>(function MoneyInput(
  { value, onChange, allowNegative, 'aria-invalid': invalid, ...props },
  ref,
) {
  const [shown, setShown] = useState(() => fromDong(value));
  const dong = toDong(shown.text, shown.unit);

  // The parent changed the amount (a reset, a loaded record): show it, unless it is what the field already holds.
  useEffect(() => {
    if (dong !== value) setShown(fromDong(value));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  const update = (text: string, unit: number) => {
    const clean = allowNegative ? text : text.replace(/-/g, '');
    setShown({ text: clean, unit: unit as typeof shown.unit });
    onChange(toDong(clean, unit));
  };

  const full = groupDong(dong);
  return (
    <div className="grid gap-1.5">
      <div className="flex gap-2">
        <Input
          ref={ref}
          type="text"
          inputMode={allowNegative ? 'text' : 'decimal'}
          autoComplete="off"
          data-unit="VND"
          value={shown.text}
          aria-invalid={invalid}
          onChange={(e) => update(e.target.value, shown.unit)}
          className="min-w-0 flex-1"
          {...props}
        />
        <Select
          aria-label={vi.money.unit}
          value={String(shown.unit)}
          onChange={(e) => update(shown.text, Number(e.target.value))}
          className="w-36 shrink-0"
        >
          {MONEY_UNITS.map((u) => (
            <option key={u} value={u}>
              {vi.money.units[u]}
            </option>
          ))}
        </Select>
      </div>
      <p className="tabular m-0 min-h-5 text-meta text-muted-foreground" aria-live="polite">
        {full ? vi.money.preview(full) : dong ? vi.money.notWhole : ''}
      </p>
    </div>
  );
});
