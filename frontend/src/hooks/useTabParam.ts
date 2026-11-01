import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * The open tab of a page kept in the URL (`?tab=`), so a link, a reload and the browser's back button return to it.
 * The other parameters, the search, filters and page of `usePagedList`, are left as they are, so a tab keeps its
 * filters when the person switches away and back (BR-209). An unknown value gives the first tab.
 */
export function useTabParam<T extends string>(values: readonly T[], param = 'tab'): [T, (tab: T) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get(param);
  const tab = (values as readonly string[]).includes(raw ?? '') ? (raw as T) : values[0];
  const setTab = useCallback(
    (next: T) =>
      setParams(
        (prev) => {
          const copy = new URLSearchParams(prev);
          if (next === values[0]) copy.delete(param);
          else copy.set(param, next);
          return copy;
        },
        { replace: true },
      ),
    [setParams, values, param],
  );
  return [tab, setTab];
}
