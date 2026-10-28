import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table';
import { ArrowDown, ArrowUp, ArrowUpDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { vi } from '@/i18n/vi';
import { Button } from './button';

/** True while `ref`'s box scrolls sideways, so a keyboard user can focus and scroll it (axe: scrollable-region-focusable). */
function useScrolls<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [scrolls, setScrolls] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => setScrolls(el.scrollWidth > el.clientWidth + 1);
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    if (el.firstElementChild) ro.observe(el.firstElementChild);
    return () => ro.disconnect();
  }, []);
  return { ref, scrolls };
}

export interface DataColumn<T> {
  id: string;
  header: string;
  cell: (row: T) => ReactNode;
  /** Sort key; omit for unsortable columns. */
  sortValue?: (row: T) => string | number;
  /** Title of the row's card below `md` (usually the name column). */
  primary?: boolean;
  /** Right-aligned action cell; on phones it sits under the card. */
  actions?: boolean;
  className?: string;
  /** A shorter term for the card layout, when the table header is long. */
  cardLabel?: string;
  /** Short values (dates, codes, badges): the column takes only the width its content needs at `xl`, and the free width goes to the text columns. */
  fit?: boolean;
  /** Hide the column's label on the phone card (e.g. when the value is a self-describing badge). */
  hideLabelOnMobile?: boolean;
}

interface DataTableProps<T> {
  data: T[];
  columns: DataColumn<T>[];
  rowKey: (row: T) => string | number;
  /** Accessible name of the table (rendered as a visually hidden caption). */
  caption?: string;
  /** Rows per page; omit to show every row. */
  pageSize?: number;
  rowClassName?: (row: T) => string | undefined;
  className?: string;
}

/**
 * The kit's one table (ui-kit § Data): optional sort and pagination, row actions, and below `md` the same
 * semantic table restyled as a ruled card list (one DOM, so links, rows and cells are never duplicated).
 */
export function DataTable<T>({ data, columns, rowKey, caption, pageSize, rowClassName, className }: DataTableProps<T>) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const defs: ColumnDef<T>[] = columns.map((c) => ({
    id: c.id,
    header: c.header,
    accessorFn: c.sortValue ?? (() => 0),
    enableSorting: Boolean(c.sortValue),
    sortDescFirst: false,
    meta: c,
  }));
  const table = useReactTable({
    data,
    columns: defs,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    ...(pageSize ? { getPaginationRowModel: getPaginationRowModel(), initialState: { pagination: { pageSize, pageIndex: 0 } } } : {}),
    getRowId: (row) => String(rowKey(row)),
  });
  const rows = pageSize ? table.getPaginationRowModel().rows : table.getRowModel().rows;
  const scroller = useScrolls<HTMLDivElement>();

  return (
    <div className={cn('grid gap-3', className)}>
      <div
        ref={scroller.ref}
        {...(scroller.scrolls ? { tabIndex: 0, role: 'group', 'aria-label': caption } : {})}
        className="xl:overflow-x-auto xl:rounded-lg xl:border xl:bg-card"
      >
        <table role="table" className="w-full border-collapse text-sm">
          {caption && <caption className="sr-only">{caption}</caption>}
          <thead className="hidden xl:table-header-group">
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id} className="border-b bg-secondary/60">
                {hg.headers.map((h) => {
                  const meta = h.column.columnDef.meta as DataColumn<T>;
                  const sorted = h.column.getIsSorted();
                  return (
                    <th
                      key={h.id}
                      scope="col"
                      aria-sort={sorted === 'asc' ? 'ascending' : sorted === 'desc' ? 'descending' : undefined}
                      className={cn(
                        'h-10 px-4 text-left align-middle text-xs font-semibold text-muted-foreground',
                        meta.fit ? 'leading-tight' : 'whitespace-nowrap',
                        meta.actions && 'text-right',
                        meta.fit && 'xl:w-px',
                      )}
                    >
                      {h.column.getCanSort() ? (
                        <button
                          type="button"
                          onClick={h.column.getToggleSortingHandler()}
                          className="-mx-1.5 inline-flex items-center gap-1 rounded-sm px-1.5 py-1 hover:text-foreground"
                          aria-label={vi.common.sortBy(meta.header)}
                        >
                          {meta.header}
                          {sorted === 'asc' ? (
                            <ArrowUp aria-hidden className="size-3.5" />
                          ) : sorted === 'desc' ? (
                            <ArrowDown aria-hidden className="size-3.5" />
                          ) : (
                            <ArrowUpDown aria-hidden className="size-3.5 opacity-50" />
                          )}
                        </button>
                      ) : (
                        meta.header
                      )}
                    </th>
                  );
                })}
              </tr>
            ))}
          </thead>
          <tbody role="rowgroup" className="block divide-y rounded-lg border bg-card xl:table-row-group xl:rounded-none xl:border-0">
            {rows.map((row) => (
              <tr
                role="row"
                key={row.id}
                className={cn(
                  'grid gap-x-4 gap-y-2 px-4 py-3.5 transition-colors duration-[var(--dur-fast)] sm:grid-cols-2 xl:table-row xl:p-0 xl:hover:bg-secondary/40',
                  rowClassName?.(row.original),
                )}
              >
                {row.getVisibleCells().map((cell) => {
                  const meta = cell.column.columnDef.meta as DataColumn<T>;
                  return (
                    <td
                      role="cell"
                      key={cell.id}
                      data-label={meta.primary || meta.actions || meta.hideLabelOnMobile ? undefined : (meta.cardLabel ?? meta.header)}
                      className={cn(
                        'grid min-w-0 grid-cols-[minmax(9rem,40%)_minmax(0,1fr)] items-baseline gap-x-3 align-middle xl:table-cell xl:h-11 xl:px-4 xl:py-2',
                        'before:text-meta before:text-muted-foreground before:content-[attr(data-label)] xl:before:hidden',
                        meta.primary && 'block font-medium sm:col-span-2 xl:font-normal',
                        meta.actions && 'flex flex-wrap gap-2 pt-1 max-xl:-ml-3 sm:col-span-2 xl:table-cell xl:max-w-[16rem] xl:pt-2 xl:text-right',
                        meta.hideLabelOnMobile && 'block',
                        meta.primary && 'xl:min-w-[9.5rem]',
                        meta.fit && 'xl:w-px xl:whitespace-nowrap',
                        meta.className,
                      )}
                    >
                      {/* One wrapper, so the phone card's label | value grid never splits a cell's content. */}
                      <div className={cn('min-w-0', meta.actions && 'contents xl:flex xl:flex-wrap xl:justify-end xl:gap-1')}>
                        {/* Called, not handed to flexRender: flexRender mounts a function as a component, and the column
                            functions are new on every render, so each render would remount the cell (and drop focus). */}
                        {meta.cell(row.original)}
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {pageSize && table.getPageCount() > 1 && (
        <Pagination
          page={table.getState().pagination.pageIndex + 1}
          total={table.getPageCount()}
          onPage={(n) => table.setPageIndex(n - 1)}
        />
      )}
    </div>
  );
}

export const PAGE_SIZES = [10, 20, 50, 100] as const;

/** 1-based page numbers to show: the first, the last and a window around the current page, with gaps as `null`. */
export function pageNumbers(page: number, total: number): (number | null)[] {
  const keep = new Set([1, total, page - 1, page, page + 1].filter((n) => n >= 1 && n <= total));
  const out: (number | null)[] = [];
  let last = 0;
  for (const n of [...keep].sort((a, b) => a - b)) {
    if (n - last > 1) out.push(null);
    out.push(n);
    last = n;
  }
  return out;
}

/**
 * Numbered pager (1 2 3 … n) with previous/next, and, when `onSize` is given, the rows-per-page choice
 * (10, 20, 50, 100). `page` is 1-based; `onPage` receives a 1-based page.
 */
export function Pagination({
  page,
  total,
  onPage,
  size,
  onSize,
}: {
  page: number;
  total: number;
  onPage: (page: number) => void;
  size?: number;
  onSize?: (size: number) => void;
}) {
  return (
    <nav aria-label={vi.common.pageOf(page, total)} className="flex flex-wrap items-center justify-end gap-x-4 gap-y-2">
      {onSize && size !== undefined && (
        <label className="flex items-center gap-2 text-meta text-muted-foreground">
          {vi.common.perPage}
          <select
            value={size}
            onChange={(e) => onSize(Number(e.target.value))}
            className="h-8 rounded-md border bg-background px-2 text-sm text-foreground"
          >
            {PAGE_SIZES.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </label>
      )}
      <div className="flex items-center gap-1">
        <Button variant="outline" size="icon" onClick={() => onPage(page - 1)} disabled={page <= 1} aria-label={vi.common.previousPage}>
          <ChevronLeft aria-hidden />
        </Button>
        {pageNumbers(page, total).map((n, i) =>
          n === null ? (
            <span key={`gap-${i}`} aria-hidden className="px-1 text-muted-foreground">
              …
            </span>
          ) : (
            <Button
              key={n}
              variant={n === page ? 'default' : 'outline'}
              size="icon"
              onClick={() => onPage(n)}
              aria-current={n === page ? 'page' : undefined}
              aria-label={vi.common.goToPage(n)}
              className="tabular text-sm"
            >
              {n}
            </Button>
          ),
        )}
        <Button variant="outline" size="icon" onClick={() => onPage(page + 1)} disabled={page >= total} aria-label={vi.common.nextPage}>
          <ChevronRight aria-hidden />
        </Button>
      </div>
    </nav>
  );
}
