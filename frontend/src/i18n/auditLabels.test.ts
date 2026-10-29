/// <reference types="vite/client" />
import { describe, expect, test } from 'vitest';
import { vi } from './vi';

// The audit search runs on the server and must match the text the screen shows (BR-093), so the service keeps a copy of
// these two tables. This test fails when one side changes without the other.
const sources = import.meta.glob('../../../backend/src/main/java/vn/racehorse/audit/AuditActionLabels.java', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;
const java = Object.values(sources)[0] ?? '';

function entries(block: string): Record<string, string> {
  return Object.fromEntries([...block.matchAll(/Map\.entry\("([^"]+)", "([^"]*)"\)/g)].map((m) => [m[1], m[2]]));
}

describe.skipIf(java === '')('audit labels the service searches', () => {
  const [actions, targets] = java.split('TARGETS');

  test('every action label equals the one the screen shows', () => {
    expect(entries(actions)).toEqual(vi.audit.actions);
  });

  test('every target label equals the one the screen shows', () => {
    expect(entries(targets)).toEqual(vi.audit.targets);
  });
});
