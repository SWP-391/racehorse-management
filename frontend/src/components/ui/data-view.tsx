import type { ReactNode } from 'react';
import type { Load } from '@/hooks/useLoad';
import { useDelayedFlag } from '@/hooks/useDelayedFlag';
import { ErrorState, ListSkeleton } from './states';

interface DataViewProps<T> {
  load: Load<T>;
  /** Skeleton shaped like the content; defaults to a list. */
  skeleton?: ReactNode;
  /** Shown when `isEmpty(data)`; should name the next action. */
  empty?: ReactNode;
  isEmpty?: (data: T) => boolean;
  children: (data: T) => ReactNode;
}

/**
 * The four data states of the kit (contract 2): skeleton after 200 ms, ErrorState with Retry,
 * EmptyState, content. While a reload runs the old content stays on screen (never "0" for unknown).
 */
export function DataView<T>({ load, skeleton, empty, isEmpty, children }: DataViewProps<T>) {
  const { state, reload } = load;
  const firstLoad = state.status === 'loading' && state.data === null;
  const showSkeleton = useDelayedFlag(firstLoad);

  if (state.status === 'error' && state.data === null) {
    return <ErrorState message={state.error} onRetry={() => void reload()} />;
  }
  if (state.data === null) {
    return showSkeleton ? <>{skeleton ?? <ListSkeleton />}</> : null;
  }
  return (
    <>
      {state.status === 'error' && <ErrorState className="mb-4" message={state.error} onRetry={() => void reload()} />}
      {empty && isEmpty?.(state.data) ? empty : children(state.data)}
    </>
  );
}
