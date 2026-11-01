import appSource from '../App.tsx?raw';
import { meAs } from '../test/render';
import { canOpen, homeFor, ROUTE_ACCESS, SECTIONS } from './access';

describe('route access table (BR-191, BR-091, TECH-127)', () => {
  test('every route declared in App.tsx has an access entry', () => {
    const declared = [...appSource.matchAll(/<Route\s+path="([^"]+)"/g)].map((m) => m[1]).filter((p) => p !== '*' && p !== '/none');
    expect(declared.length).toBeGreaterThan(15);
    const known = ROUTE_ACCESS.map((r) => r.path);
    expect(declared.filter((p) => !known.includes(p))).toEqual([]);
  });

  test.each([
    ['HEAD_TRAINER', ['/progress', '/plans/1', '/sessions/1', '/horses/4/plans/new'], ['/accounts', '/my-sessions', '/horses/new']],
    ['VETERINARIAN', ['/care', '/plans/1', '/horses/4/injuries'], ['/progress', '/accounts', '/plans/1/edit']],
    ['GROOM', ['/my-sessions', '/incidents', '/supplies'], ['/plans/1', '/progress', '/horses']],
    ['HORSE_OWNER', ['/horses', '/horses/4', '/plans/1'], ['/accounts', '/horses/new', '/care']],
    ['CLUB_MANAGER', ['/accounts', '/horses/new', '/horses/4/edit', '/plans/1'], ['/progress', '/my-sessions', '/care']],
  ] as const)('%s may open and may not open', (role, allowed, refused) => {
    const me = meAs(role);
    expect(allowed.filter((p) => !canOpen(me, p))).toEqual([]);
    expect(refused.filter((p) => canOpen(me, p))).toEqual([]);
  });

  test('a query string does not change the answer and an unknown path is not openable', () => {
    const me = meAs('HEAD_TRAINER');
    expect(canOpen(me, '/horses?page=2')).toBe(true);
    expect(canOpen(me, '/none')).toBe(false);
    expect(canOpen(me, '/')).toBe(false);
    expect(canOpen(me, '/somewhere-else')).toBe(false);
  });

  test('each role has its home page', () => {
    expect(homeFor(meAs('HEAD_TRAINER'))).toBe('/horses');
    expect(homeFor(meAs('GROOM'))).toBe('/incidents');
    expect(homeFor({ ...meAs('GROOM'), permissions: [] })).toBe('/none');
  });

  test.each(['HEAD_TRAINER', 'VETERINARIAN', 'GROOM', 'HORSE_OWNER', 'CLUB_MANAGER'] as const)(
    '%s home is a page the role may open and the first one of its menu',
    (role) => {
      const me = meAs(role);
      const home = homeFor(me);
      expect(canOpen(me, home)).toBe(true);
      expect(home).toBe(SECTIONS.find((s) => s.any.some((p) => me.permissions.includes(p)))!.path);
    },
  );

  test('a role whose only section is not horses, incidents or accounts still lands on that section', () => {
    expect(homeFor({ ...meAs('HORSE_OWNER'), permissions: ['VIEW_BILLING'] })).toBe('/billing');
    expect(homeFor({ ...meAs('GROOM'), permissions: ['VIEW_ASSIGNED_SESSIONS', 'VIEW_SUPPLIES'] })).toBe('/supplies');
    expect(homeFor({ ...meAs('CLUB_MANAGER'), permissions: ['VIEW_AUDIT_LOG'] })).toBe('/audit');
  });
});
