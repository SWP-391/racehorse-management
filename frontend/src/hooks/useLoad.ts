import { useCallback, useEffect, useRef, useState, type DependencyList } from 'react';
import { messageOf } from '../components/ErrorText';

export type LoadState<T> =
  | { status: 'loading'; data: T | null; error: null }
  | { status: 'error'; data: T | null; error: string }
  | { status: 'ready'; data: T; error: null };

export interface Load<T> {
  state: LoadState<T>;
  /** Re-runs the loader (ErrorState Retry, refresh after a save). Keeps the old data on screen while it runs. */
  reload: () => Promise<void>;
  /** Replaces the data in place after a save that returned the new value. */
  set: (data: T) => void;
}

/**
 * The one data-loading hook (TECH-074): wraps an `api` call in loading / error / ready, drops answers
 * that arrive after a newer request or after unmount, and turns refusals into the service's message.
 * Pass `enabled: false` to skip the call (e.g. the user lacks the permission).
 */
export function useLoad<T>(fn: () => Promise<T>, deps: DependencyList, enabled = true): Load<T> {
  const [state, setState] = useState<LoadState<T>>({ status: 'loading', data: null, error: null });
  const seq = useRef(0);
  const fnRef = useRef(fn);
  fnRef.current = fn;

  const reload = useCallback(async () => {
    const id = ++seq.current;
    setState((s) => ({ status: 'loading', data: s.data, error: null }));
    try {
      const data = await fnRef.current();
      if (id === seq.current) setState({ status: 'ready', data, error: null });
    } catch (e) {
      if (id === seq.current) setState((s) => ({ status: 'error', data: s.data, error: messageOf(e) }));
    }
  }, []);

  useEffect(() => {
    if (!enabled) return;
    void reload();
    return () => {
      seq.current++;
    };
    // `deps` is the caller's list of what the loader reads; the loader itself is read from a ref.
  }, [enabled, reload, ...deps]);

  const set = useCallback((data: T) => setState({ status: 'ready', data, error: null }), []);

  return { state, reload, set };
}
