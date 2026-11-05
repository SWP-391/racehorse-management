import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { act } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { vi as vitest } from 'vitest';
import App from './App';
import { api } from './api/api';
import { ApiError, request, resetSessionEnded } from './api/client';
import { meAs } from './test/render';

vitest.mock('./api/api', () => ({
  api: {
    me: vitest.fn(),
    login: vitest.fn(),
    logout: vitest.fn(),
    notifications: vitest.fn().mockResolvedValue({ unread: 0, items: [] }),
  },
}));
// Pages are not under test here: a marker per route is enough to see where the person landed.
const page = (name: string) => ({ default: () => <p>{`page:${name}`}</p> });
vitest.mock('./pages/PlanPage', () => page('plan'));
vitest.mock('./pages/HorsesPage', () => page('horses'));
vitest.mock('./pages/ProgressPage', () => page('progress'));
vitest.mock('./pages/IncidentsPage', () => page('incidents'));

async function signIn(user: string) {
  await userEvent.type(await screen.findByLabelText('Tên đăng nhập'), user);
  await userEvent.type(screen.getByLabelText('Mật khẩu'), 'password-123');
  await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
}

async function endSession() {
  vitest.stubGlobal('fetch', vitest.fn().mockResolvedValue(new Response('{}', { status: 401 })));
  await act(async () => {
    await request('GET', '/api/horses').catch(() => undefined);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
  resetSessionEnded();
  vitest.mocked(api.me).mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED', 'Vui lòng đăng nhập.'));
});
afterEach(() => vitest.unstubAllGlobals());

describe('landing after sign-in (BR-191, TECH-127)', () => {
  test('AC-280 the same Head Trainer returns to the plan page they left', async () => {
    window.history.pushState({}, '', '/plans/7');
    vitest.mocked(api.login).mockResolvedValue(meAs('HEAD_TRAINER'));
    render(<BrowserRouter><App /></BrowserRouter>);
    await signIn('ht');
    expect(await screen.findByText('page:plan')).toBeInTheDocument();
    await endSession();
    expect(await screen.findByText('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'password-123');
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
    expect(await screen.findByText('page:plan')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/plans/7');
  });

  test('AC-280 a Groom signing in where a Head Trainer left lands on the Groom home', async () => {
    window.history.pushState({}, '', '/plans/7');
    vitest.mocked(api.login).mockResolvedValueOnce(meAs('HEAD_TRAINER')).mockResolvedValueOnce(meAs('GROOM'));
    render(<BrowserRouter><App /></BrowserRouter>);
    await signIn('ht');
    expect(await screen.findByText('page:plan')).toBeInTheDocument();
    await endSession();
    await screen.findByText('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.');
    await signIn('groom');
    expect(await screen.findByText('page:incidents')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/incidents');
  });

  test('a role switch without reload never lands on an access refusal', async () => {
    window.history.pushState({}, '', '/progress');
    vitest.mocked(api.login).mockResolvedValueOnce(meAs('HEAD_TRAINER')).mockResolvedValueOnce(meAs('VETERINARIAN'));
    vitest.mocked(api.logout).mockResolvedValue(undefined);
    render(<BrowserRouter><App /></BrowserRouter>);
    await signIn('ht');
    expect(await screen.findByText('page:progress')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Đăng xuất/ }));
    await signIn('vet');
    expect(await screen.findByText('page:horses')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/horses');
    expect(screen.queryByText('Bạn không có quyền thực hiện thao tác này.')).not.toBeInTheDocument();
  });
});
