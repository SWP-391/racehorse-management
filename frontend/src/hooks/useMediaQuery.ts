import { useEffect, useState } from 'react';

/** Tracks a CSS media query; `fallback` is the answer where `matchMedia` does not exist. */
export function useMediaQuery(query: string, fallback = true): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia?.(query).matches ?? fallback);
  useEffect(() => {
    const mq = window.matchMedia?.(query);
    if (!mq) return;
    const onChange = () => setMatches(mq.matches);
    onChange();
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [query]);
  return matches;
}
