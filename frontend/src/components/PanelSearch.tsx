import { useEffect, useRef, useState } from 'react';
import { vi } from '../i18n/vi';
import type { Page } from '../api/types';
import { ListToolbar } from './ui/list-toolbar';

const DEBOUNCE_MS = 250;

/**
 * Search box of a small list inside a panel or dialog (leave, care history, plan versions, alerts, cancelled marks).
 * It shows only when the list does not fit on one page or a search is active, so a short list stays plain; the text
 * reaches `onSearch` 250 ms after the last key and the caller sends it as `q` and returns to the first page.
 */
export function PanelSearch({ data, q, onSearch, label }: { data: Page<unknown> | null; q: string; onSearch: (q: string) => void; label?: string }) {
  const [text, setText] = useState(q);
  const latest = useRef(onSearch);
  latest.current = onSearch;

  useEffect(() => setText(q), [q]);
  useEffect(() => {
    if (text.trim() === q) return;
    const handle = setTimeout(() => latest.current(text.trim()), DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [text, q]);

  if (!data || (data.total <= data.size && q === '' && text === '')) return null;
  return <ListToolbar query={text} onQuery={setText} label={label ?? vi.common.search} total={data.total} />;
}

/** The empty answer of a search: the list is not empty, nothing matches the text. */
export function NoMatches() {
  return <p className="text-meta text-muted-foreground">{vi.common.noResults}</p>;
}
