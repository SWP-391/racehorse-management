import { matchPath } from 'react-router-dom';
import type { Me, Permission } from '../api/types';

/** A client route and the permissions that open it (any one is enough). BR-091: the service still checks every call. */
export interface RouteAccess {
  path: string;
  any: Permission[];
}

/** Menu sections in menu order; the first one a role may open is that role's home (`NavList` adds label and icon). */
export const SECTIONS: RouteAccess[] = [
  { path: '/horses', any: ['VIEW_HORSES'] },
  { path: '/health', any: ['VIEW_HEALTH'] },
  { path: '/progress', any: ['VIEW_PROGRESS_BOARD'] },
  { path: '/care', any: ['MANAGE_CARE'] },
  { path: '/incidents', any: ['SEND_INCIDENTS', 'HANDLE_INCIDENTS'] },
  { path: '/chores', any: ['VIEW_CHORES'] },
  { path: '/barn', any: ['VIEW_BARN'] },
  { path: '/supplies', any: ['VIEW_SUPPLIES'] },
  { path: '/billing', any: ['VIEW_BILLING'] },
  { path: '/timetable', any: ['VIEW_OWN_TIMETABLE'] },
  { path: '/my-sessions', any: ['VIEW_ASSIGNED_SESSIONS'] },
  { path: '/accounts', any: ['MANAGE_ACCOUNTS'] },
  { path: '/audit', any: ['VIEW_AUDIT_LOG'] },
  { path: '/settings', any: ['CONFIGURE_GRACE_PERIOD'] },
];

/** Pages below a section; a more specific pattern comes first (`/horses/new` before `/horses/:id`). */
const DETAIL_ROUTES: RouteAccess[] = [
  { path: '/horses/new', any: ['MANAGE_HORSES'] },
  { path: '/horses/:id/edit', any: ['MANAGE_HORSES'] },
  { path: '/horses/:id/injuries', any: ['VIEW_INJURY_MARKS'] },
  { path: '/horses/:id/ration', any: ['VIEW_RATION'] },
  { path: '/horses/:id/plans/new', any: ['MANAGE_PLANS'] },
  { path: '/horses/:id', any: ['VIEW_HORSES'] },
  { path: '/plans/:planId/edit', any: ['MANAGE_PLANS'] },
  { path: '/plans/:id', any: ['VIEW_PLANS'] },
  { path: '/sessions/:id', any: ['VIEW_PLANS'] },
  { path: '/billing/print/:kind/:id', any: ['VIEW_BILLING'] },
];

export const ROUTE_ACCESS: RouteAccess[] = [...DETAIL_ROUTES, ...SECTIONS];

/** True when the account's role may open this path (a path with no entry is not openable). */
export function canOpen(me: Me, path: string): boolean {
  const pathname = path.split(/[?#]/)[0];
  const entry = ROUTE_ACCESS.find((r) => matchPath({ path: r.path, end: true }, pathname));
  return !!entry && entry.any.some((p) => me.permissions.includes(p));
}

/** The page a role starts on: the first menu section it may open, `/none` only when it may open none (BR-191). */
export function homeFor(me: Me): string {
  return SECTIONS.find((s) => s.any.some((p) => me.permissions.includes(p)))?.path ?? '/none';
}
