import type { Page } from '../api/types';

/** A one-page answer of a list endpoint, for tests that mock `api.*` (BR-185). */
export function pageOf<T>(items: T[], total = items.length): Page<T> {
  return { items, page: 0, size: 20, total };
}
