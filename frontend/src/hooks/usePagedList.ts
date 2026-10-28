import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { ListQuery } from '../api/types';
import { PAGE_SIZES } from '../components/ui/data-table';
import { useLoad, type Load } from './useLoad';

/** What every paged answer carries; a `Page<T>` has it, and so has the chore day list. */
interface PageLike {
  page: number;
  size: number;
  total: number;
}

interface Options<D> {
  /** Names of the filters this list has; their values live in the URL next to `q` and `page`. */
  filters?: string[];
  size?: number;
  enabled?: boolean;
  /** Prefix of the URL parameters, for a page that shows two lists. */
  prefix?: string;
  /** Extra values sent to the service but kept out of the URL (a horse id, a date range owned by the page). */
  fixed?: ListQuery;
  /** How many rows an answer holds; default `items.length`. */
  rows?: (data: D) => number;
}

export interface PagedList<D extends PageLike> {
  load: Load<D>;
  /** The text in the search box (the list searches 300 ms after typing stops). */
  query: string;
  setQuery: (text: string) => void;
  filters: Record<string, string>;
  setFilter: (name: string, value: string) => void;
  /** Several filters in one step (react-router does not queue two URL updates made in a row). */
  setFilters: (patch: Record<string, string>) => void;
  /** 0-based. */
  page: number;
  setPage: (page: number) => void;
  /** Rows per page (10, 20, 50, 100); returns to page 0. */
  setSize: (size: number) => void;
  /** True once a search or a filter narrows the list, so an empty answer reads "no match" and not "nothing yet". */
  narrowed: boolean;
}

const DEBOUNCE_MS = 300;

/** The page size in the URL when it is one of the offered sizes, else the list's default. */
function validSize(text: string | null, fallback: number): number {
  const n = Number(text);
  return (PAGE_SIZES as readonly number[]).includes(n) ? n : fallback;
}

function validPage(text: string | null): number {
  const n = Number(text);
  return Number.isInteger(n) && n >= 0 && text !== null && text !== '' ? n : 0;
}

/**
 * The one paging hook (TECH-177): search, filters and page live in the URL (reload and Back keep the place, invalid
 * values fall back to defaults), a change of search or filter returns to page 0, and the previous page stays on screen
 * while the next one loads. The service reduces the list to what the caller may see before it counts.
 */
export function usePagedList<D extends PageLike>(fetcher: (query: ListQuery) => Promise<D>, options: Options<D> = {}): PagedList<D> {
  const { filters: filterNames = [], size: defaultSize = 20, enabled = true, prefix = '', fixed, rows } = options;
  const [params, setParams] = useSearchParams();
  const key = (name: string) => prefix + name;
  const page = validPage(params.get(key('page')));
  const q = params.get(key('q')) ?? '';
  const size = validSize(params.get(key('size')), defaultSize);
  const filterKey = filterNames.join('|');
  const filters = useMemo(
    () => Object.fromEntries(filterNames.map((n) => [n, params.get(prefix + n) ?? ''])),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [params, prefix, filterKey],
  );
  const [query, setQueryText] = useState(q);

  const update = useCallback(
    (patch: Record<string, string | number>) =>
      setParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          for (const [name, value] of Object.entries(patch)) {
            if (value === '' || value === 0) next.delete(prefix + name);
            else next.set(prefix + name, String(value));
          }
          return next;
        },
        { replace: true },
      ),
    [setParams, prefix],
  );

  const pending = useRef<ReturnType<typeof setTimeout> | null>(null);
  const setQuery = useCallback(
    (text: string) => {
      setQueryText(text);
      if (pending.current) clearTimeout(pending.current);
      pending.current = setTimeout(() => update({ q: text.trim(), page: 0 }), DEBOUNCE_MS);
    },
    [update],
  );
  useEffect(
    () => () => {
      if (pending.current) clearTimeout(pending.current);
    },
    [],
  );
  // Back/forward or a link changes the URL: the box follows it.
  useEffect(() => {
    setQueryText((current) => (current.trim() === q ? current : q));
  }, [q]);

  const fixedKey = JSON.stringify(fixed ?? {});
  const load = useLoad(
    () => fetcher({ ...fixed, ...filters, q, page, size }),
    [page, q, size, JSON.stringify(filters), fixedKey],
    enabled,
  );

  // A page that no longer exists (rows were removed, a hand-edited URL) returns to the last page that does.
  const data = load.state.data;
  useEffect(() => {
    const count = data ? (rows ? rows(data) : ((data as unknown as { items: unknown[] }).items?.length ?? 1)) : 1;
    if (data && count === 0 && data.total > 0 && page > 0) {
      update({ page: Math.max(0, Math.ceil(data.total / data.size) - 1) });
    }
  }, [data, page, update, rows]);

  const setFilter = useCallback((name: string, value: string) => update({ [name]: value, page: 0 }), [update]);
  const setFilters = useCallback((patch: Record<string, string>) => update({ ...patch, page: 0 }), [update]);
  const setPage = useCallback((next: number) => update({ page: next }), [update]);
  const setSize = useCallback((next: number) => update({ size: next === defaultSize ? '' : next, page: 0 }), [update, defaultSize]);

  return {
    load,
    query,
    setQuery,
    filters,
    setFilter,
    setFilters,
    page,
    setPage,
    setSize,
    narrowed: q !== '' || Object.values(filters).some((v) => v !== ''),
  };
}
