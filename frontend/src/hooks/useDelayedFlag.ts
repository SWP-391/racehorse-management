import { useEffect, useState } from 'react';

/** Kit rule: a skeleton shows only after 200 ms, so fast loads never flash (ui-kit contract 2). */
export const SKELETON_DELAY_MS = 200;

/** True once `flag` has stayed true for `delay` ms; false again as soon as it turns false. */
export function useDelayedFlag(flag: boolean, delay = SKELETON_DELAY_MS): boolean {
  const [shown, setShown] = useState(false);
  useEffect(() => {
    if (!flag) {
      setShown(false);
      return;
    }
    const timer = setTimeout(() => setShown(true), delay);
    return () => clearTimeout(timer);
  }, [flag, delay]);
  return shown;
}
