import type { ReactNode } from 'react';
import { Search, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { vi } from '@/i18n/vi';
import type { Page } from '@/api/types';
import { Button } from './button';
import { Input } from './input';
import { PAGE_SIZES, Pagination } from './data-table';

interface ListToolbarProps {
  /** The text in the box and its change handler (from `usePagedList`). */
  query: string;
  onQuery: (text: string) => void;
  placeholder?: string;
  /** Accessible name of the box; defaults to the placeholder. */
  label?: string;
  /** Filter controls (selects), placed after the box. */
  children?: ReactNode;
  /** The answer of the list, for the result count. */
  total?: number;
  className?: string;
}

/** Search box with a clear button, the filters of the list and a polite result count (TECH-177). */
export function ListToolbar({ query, onQuery, placeholder, label, children, total, className }: ListToolbarProps) {
  const name = label ?? placeholder ?? vi.common.search;
  return (
    <div className={cn('flex flex-wrap items-end gap-3', className)} role="search">
      <div className="relative min-w-[14rem] flex-1 sm:max-w-sm">
        <Search aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          type="search"
          value={query}
          onChange={(e) => onQuery(e.target.value)}
          placeholder={placeholder ?? vi.common.search}
          aria-label={name}
          className="pr-9 pl-9 [&::-webkit-search-cancel-button]:hidden"
        />
        {query && (
          <Button
            variant="ghost"
            size="icon"
            onClick={() => onQuery('')}
            aria-label={vi.common.clearSearch}
            className="absolute top-1/2 right-1 size-7 -translate-y-1/2"
          >
            <X aria-hidden />
          </Button>
        )}
      </div>
      {children}
      {total !== undefined && (
        <p aria-live="polite" className="ml-auto text-meta text-muted-foreground tabular-nums">
          {vi.common.resultCount(total)}
        </p>
      )}
    </div>
  );
}

interface ListFooterProps {
  data: Page<unknown>;
  onPage: (page: number) => void;
  /** Rows per page (10, 20, 50, 100); omit where the list's size is fixed. */
  onSize?: (size: number) => void;
}

/** "21–25 / 25" with numbered pages and the page size; nothing when everything fits on the smallest page. */
export function ListFooter({ data, onPage, onSize }: ListFooterProps) {
  const pages = Math.max(1, Math.ceil(data.total / data.size));
  if (pages <= 1 && (!onSize || data.total <= PAGE_SIZES[0])) return null;
  const from = data.total === 0 ? 0 : data.page * data.size + 1;
  const to = Math.min(data.total, from + data.items.length - 1);
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <span className="text-meta text-muted-foreground tabular-nums">{vi.common.rangeOf(from, to, data.total)}</span>
      <Pagination page={data.page + 1} total={pages} onPage={(n) => onPage(n - 1)} size={data.size} onSize={onSize} />
    </div>
  );
}
