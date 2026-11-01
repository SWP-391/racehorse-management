import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactElement } from 'react';
import { AuthProvider } from '../auth/AuthContext';
import type { Me, Permission, Role } from '../api/types';

/** Mirrors the service's Permission enum per role (ba/09-permission-matrix.md). */
const PERMISSIONS: Record<Role, Permission[]> = {
  HEAD_TRAINER: [
    'VIEW_RATION',
    'VIEW_HORSES',
    'MANAGE_PLANS',
    'SCHEDULE_SESSIONS',
    'RUN_SESSIONS',
    'VIEW_PROGRESS_BOARD',
    'VIEW_PLANS',
    'REVIEW_BLOCKED_SESSIONS',
    'REQUEST_LOCK_REVIEW',
    'SET_THRESHOLDS',
    'VIEW_LIVE',
    'RECEIVE_ALERTS',
    'VIEW_INJURY_MARKS',
    'VIEW_EXAMINATIONS',
    'VIEW_HEALTH',
    'VIEW_READINESS',
    'MANAGE_TRIAL_VIDEOS',
    'WATCH_TRIAL_VIDEOS',
    'VIEW_CHORES',
    'VIEW_BARN',
    'VIEW_SCHEDULES',
    'VIEW_OWN_TIMETABLE',
  ],
  VETERINARIAN: [
    'COMPOSE_RATION',
    'VIEW_RATION',
    'VIEW_HORSES',
    'VIEW_PLANS',
    'CHANGE_HEALTH_STATUS',
    'MANAGE_LOCKS',
    'REQUEST_PENDING_LOCK',
    'RECEIVE_ALERTS',
    'RECORD_EXAMINATIONS',
    'MANAGE_INJURY_MARKS',
    'VIEW_INJURY_MARKS',
    'VIEW_EXAMINATIONS',
    'MANAGE_CARE',
    'HANDLE_INCIDENTS',
    'VIEW_HORSE_OPTIONS',
    'VIEW_HEALTH',
    'VIEW_READINESS',
    'VIEW_CHORES',
    'VIEW_BARN',
  ],
  GROOM: [
    'VIEW_RATION',
    'SEND_INCIDENTS',
    'VIEW_HORSE_OPTIONS',
    'VIEW_ASSIGNED_SESSIONS',
    'ACKNOWLEDGE_ASSIGNMENT',
    'MARK_CHORES',
    'VIEW_CHORES',
    'VIEW_BARN',
    'VIEW_SUPPLIES',
    'PROPOSE_RESTOCK',
    'VIEW_SCHEDULES',
    'VIEW_OWN_TIMETABLE',
  ],
  HORSE_OWNER: [
    'VIEW_RATION',
    'VIEW_HORSES',
    'VIEW_PLANS',
    'VIEW_READINESS',
    'WATCH_TRIAL_VIDEOS',
    'VIEW_CHORES',
    'VIEW_BARN',
    'VIEW_BILLING',
    'EXPORT_BILLING',
  ],
  CLUB_MANAGER: [
    'VIEW_RATION',
    'VIEW_HORSES',
    'MANAGE_HORSES',
    'ARCHIVE_HORSES',
    'VIEW_PLANS',
    'VIEW_HEALTH',
    'VIEW_READINESS',
    'MANAGE_ACCOUNTS',
    'VIEW_AUDIT_LOG',
    'CONFIGURE_GRACE_PERIOD',
    'CONFIGURE_LATE_CHANGE_THRESHOLD',
    'VIEW_CHORES',
    'VIEW_BARN',
    'MANAGE_STALLS',
    'MANAGE_SUPPLIES',
    'VIEW_SUPPLIES',
    'DECIDE_RESTOCK',
    'ASSIGN_TRAINER',
    'MANAGE_CHORE_MARKS',
    'MANAGE_AREAS',
    'MANAGE_SCHEDULES',
    'VIEW_SCHEDULES',
    'CONFIGURE_THRESHOLDS',
    'MANAGE_STOCK',
    'VIEW_STOCK',
    'SET_PRICES_AND_CLOSE_MONTH',
    'VIEW_BILLING',
    'EXPORT_BILLING',
  ],
};

export function meAs(role: Role): Me {
  return { id: 1, username: role.toLowerCase(), displayName: `Test ${role}`, role, permissions: PERMISSIONS[role] };
}

/** Renders a page as a signed-in user at a route; `path` is the route pattern, `at` the concrete URL. */
export function renderAs(role: Role, element: ReactElement, path = '/', at = '/') {
  return render(
    <AuthProvider initial={meAs(role)}>
      <MemoryRouter initialEntries={[at]}>
        <Routes>
          <Route path={path} element={element} />
          <Route path="*" element={<p>navigated</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

/** Renders a page for a user that lacks the given permissions (the denied-access paths). */
export function renderWithout(role: Role, lacking: Permission[], element: ReactElement) {
  const me = meAs(role);
  return render(
    <AuthProvider initial={{ ...me, permissions: me.permissions.filter((p) => !lacking.includes(p)) }}>
      <MemoryRouter>{element}</MemoryRouter>
    </AuthProvider>,
  );
}
