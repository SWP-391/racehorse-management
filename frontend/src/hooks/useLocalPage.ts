import { useEffect, useState, type DependencyList } from 'react';
import type { ListQuery, Page } from '../api/types';
import { useLoad, type Load } from './useLoad';

/**
 * Paging for a small list inside a dialog or a panel (leave, care history, plan versions, alerts, injury marks): the
 * page is kept in the component, not the URL, because the list is not the page's subject (TECH-177). A page that
 * becomes empty after a removal steps back one page. Changing `deps` (another horse, another Groom) returns to page 0 and clears the search; `q` is the search text sent to the service.
 */
export function useLocalPage<T>(
  fetcher: (query: ListQuery) => Promise<Page<T>>,
  deps: DependencyList,
  enabled = true,
): { load: Load<Page<T>>; page: number; setPage: (page: number) => void; q: string; setQ: (q: string) => void } {
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  useEffect(() => {
    setPage(0);
    setQ('');
  }, deps); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => setPage(0), [q]);
  const load = useLoad(() => fetcher({ page, q: q || undefined }), [page, q, ...deps], enabled);
  const data = load.state.data;
  useEffect(() => {
    if (load.state.status === 'ready' && data && data.items.length === 0 && page > 0) setPage(page - 1);
  }, [load.state.status, data, page]);
  return { load, page, setPage, q, setQ };
}
