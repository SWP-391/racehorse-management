import '@testing-library/jest-dom/vitest';
import { vi as vitest } from 'vitest';

// jsdom lacks what Radix, vaul and motion probe for. Reduced motion is on, so effects render their end state at once.
// jsdom's window is 1024 px wide: `(min-width: Npx)` and `(max-width: Npx)` are answered against that.
const matchesWidth = (query: string) => {
  const min = /min-width:\s*(\d+)px/.exec(query);
  const max = /max-width:\s*(\d+)px/.exec(query);
  if (!min && !max) return false;
  return (!min || window.innerWidth >= Number(min[1])) && (!max || window.innerWidth <= Number(max[1]));
};

if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: query.includes('prefers-reduced-motion: reduce') || matchesWidth(query),
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }) as MediaQueryList;
}

// jsdom has no object URLs; the horse form previews a chosen photo with one.
if (typeof URL.createObjectURL !== 'function') {
  URL.createObjectURL = () => 'blob:test-preview';
  URL.revokeObjectURL = () => undefined;
}

class NoopObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return [];
  }
}
window.ResizeObserver ??= NoopObserver as unknown as typeof ResizeObserver;
window.IntersectionObserver ??= NoopObserver as unknown as typeof IntersectionObserver;
Element.prototype.scrollIntoView ??= () => undefined;
Element.prototype.hasPointerCapture ??= () => false;
Element.prototype.releasePointerCapture ??= () => undefined;
Element.prototype.setPointerCapture ??= () => undefined;

// The chart canvas needs a real browser; tests read the <Chart> hidden table instead (TECH-079).
vitest.mock('../components/chart/echarts', () => ({ default: () => null }));
