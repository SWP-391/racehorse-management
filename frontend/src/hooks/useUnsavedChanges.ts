import { useEffect, useRef } from 'react';

/**
 * Warns before the tab closes or reloads while a form has unsaved edits (ui-kit § Feedback).
 * Reads a ref, not state, so typing never re-registers the listener. Returns the ref to set.
 */
export function useUnsavedChanges() {
  const dirty = useRef(false);
  useEffect(() => {
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (!dirty.current) return;
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, []);
  return dirty;
}
