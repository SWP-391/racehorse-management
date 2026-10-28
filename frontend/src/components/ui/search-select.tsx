import { useCallback, useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import * as PopoverPrimitive from '@radix-ui/react-popover';
import { Check, ChevronDown, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { vi } from '@/i18n/vi';
import type { OptionItem, Page } from '@/api/types';
import { messageOf } from '../ErrorText';
import { fieldClass, Input } from './input';

export interface Chosen {
  id: number;
  label: string;
}

interface SearchSelectProps {
  /** The chosen value keeps its name, so it still shows when it is not on the loaded page (TECH-178). */
  value: Chosen | null;
  onChange: (value: Chosen | null) => void;
  /** Loads the choices for a search text; the service applies the permission and visibility of the list. */
  load: (q: string) => Promise<Page<OptionItem>>;
  id?: string;
  placeholder?: string;
  disabled?: boolean;
  /** Lets the person empty the field again (optional values). */
  clearable?: boolean;
  className?: string;
  'aria-label'?: string;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean;
}

const DEBOUNCE_MS = 250;

const fold = (text: string) =>
  text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/gi, 'd')
    .toLowerCase();

/**
 * Feeds a SearchSelect from a list the screen already holds (a catalog, the Grooms who are free): searches without
 * diacritics and shows twenty at a time, the same shape the service answers with.
 */
export function localOptions(items: OptionItem[], size = 20): (q: string) => Promise<Page<OptionItem>> {
  return async (q) => {
    const needle = fold(q.trim());
    const matching = needle === '' ? items : items.filter((o) => fold(o.label).includes(needle));
    return { items: matching.slice(0, size), page: 0, size, total: matching.length };
  };
}

/**
 * A select over a list the system already holds (owners, horses, staff, supplies…): the choices come from the
 * service, a search box appears only when they do not fit on one page, and the control keeps `{id, label}`.
 * An accessible combobox: arrow keys move, Enter chooses, Escape closes.
 */
export function SearchSelect({
  value,
  onChange,
  load,
  id,
  placeholder,
  disabled,
  clearable,
  className,
  ...aria
}: SearchSelectProps) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [page, setPage] = useState<Page<OptionItem> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [active, setActive] = useState(0);
  const listId = useId();
  const seq = useRef(0);
  const loadRef = useRef(load);
  loadRef.current = load;
  const firstSize = useRef<number | null>(null);

  const fetchOptions = useCallback(async (q: string) => {
    const mine = ++seq.current;
    setError(null);
    try {
      const result = await loadRef.current(q);
      if (mine !== seq.current) return;
      if (firstSize.current === null && q === '') firstSize.current = result.total;
      setPage(result);
      setActive(0);
    } catch (e) {
      if (mine === seq.current) setError(messageOf(e));
    }
  }, []);

  // Opening loads the first page; typing loads the matching one 250 ms after the last key.
  useEffect(() => {
    if (!open) return;
    const handle = setTimeout(() => void fetchOptions(text.trim()), text === '' ? 0 : DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [open, text, fetchOptions]);

  function choose(option: OptionItem | undefined) {
    if (!option) return;
    onChange({ id: option.id, label: option.label });
    setOpen(false);
  }

  function onKeyDown(e: KeyboardEvent) {
    const items = page?.items ?? [];
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((a) => Math.min(items.length - 1, a + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => Math.max(0, a - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      choose(items[active]);
    }
  }

  // The search box shows only when the choices do not fit one page.
  const searchable = (page?.total ?? 0) > (page?.size ?? 20) || text !== '';

  return (
    <PopoverPrimitive.Root
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) {
          setText('');
          setPage(null);
          firstSize.current = null;
        }
      }}
    >
      <div className={cn('relative min-w-0', className)}>
        <PopoverPrimitive.Trigger asChild>
          <button
            type="button"
            id={id}
            role="combobox"
            aria-expanded={open}
            aria-haspopup="listbox"
            aria-controls={open ? listId : undefined}
            disabled={disabled}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown' && !open) {
                e.preventDefault();
                setOpen(true);
              }
            }}
            className={cn(fieldClass, 'flex h-10 items-center justify-between gap-2 pr-3 text-left lg:[@media(pointer:fine)]:h-9', clearable && value && 'pr-14')}
            {...aria}
          >
            <span className={cn('truncate', !value && 'text-muted-foreground')}>{value?.label ?? placeholder ?? vi.common.choose}</span>
            <ChevronDown aria-hidden className="size-4 shrink-0 text-muted-foreground" />
          </button>
        </PopoverPrimitive.Trigger>
        {clearable && value && !disabled && (
          <button
            type="button"
            onClick={() => onChange(null)}
            aria-label={vi.common.clearChoice}
            className="absolute top-1/2 right-8 grid size-6 -translate-y-1/2 place-items-center rounded-sm text-muted-foreground hover:text-foreground"
          >
            <X aria-hidden className="size-3.5" />
          </button>
        )}
      </div>
      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content
          align="start"
          sideOffset={4}
          onKeyDown={onKeyDown}
          className="z-50 w-[var(--radix-popover-trigger-width)] min-w-56 rounded-lg border bg-popover p-1 text-popover-foreground shadow-pop"
        >
          {searchable && (
            <div className="p-1">
              <Input
                autoFocus
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder={vi.common.typeToSearch}
                aria-label={vi.common.search}
              />
            </div>
          )}
          {error ? (
            <p role="alert" className="px-3 py-2 text-sm text-status-injured-ink">
              {vi.common.optionsFailed}: {error}
            </p>
          ) : (
            <ul id={listId} role="listbox" aria-label={placeholder ?? vi.common.choose} className="max-h-64 overflow-y-auto">
              {page && page.items.length === 0 && (
                <li role="presentation" className="px-3 py-2 text-sm text-muted-foreground">
                  {vi.common.noOptions}
                </li>
              )}
              {page?.items.map((option, index) => {
                const selected = value?.id === option.id;
                return (
                  <li
                    key={option.id}
                    role="option"
                    aria-selected={selected}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => choose(option)}
                    className={cn(
                      'flex cursor-pointer items-center justify-between gap-2 rounded-md px-3 py-2 text-sm',
                      index === active && 'bg-secondary',
                    )}
                  >
                    <span className="min-w-0">
                      <span className="block truncate">{option.label}</span>
                      {option.hint && <span className="block truncate text-meta text-muted-foreground">{option.hint}</span>}
                    </span>
                    {selected && <Check aria-hidden className="size-4 shrink-0" />}
                  </li>
                );
              })}
            </ul>
          )}
          {!error && page && page.total > page.items.length && (
            <p data-testid="more-options" className="px-3 py-2 text-meta text-muted-foreground">
              {vi.common.moreOptions.replace('{n}', String(page.total - page.items.length))}
            </p>
          )}
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
}
