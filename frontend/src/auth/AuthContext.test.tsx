import { act, render, screen } from '@testing-library/react';
import { vi as vitest } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import { meAs } from '../test/render';
import { api } from '../api/api';
import { request, resetSessionEnded } from '../api/client';

vitest.mock('../api/api', () => ({ api: { login: vitest.fn(), logout: vitest.fn(), me: vitest.fn() } }));

let auth: ReturnType<typeof useAuth>;
function Probe() {
  auth = useAuth();
  return (
    <p>
      {auth.me ? `in:${auth.me.role}` : 'out'} | {auth.endedReason ?? 'no-reason'} | {auth.landing ?? 'no-landing'}
    </p>
  );
}

const refused = () => vitest.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'UNAUTHENTICATED' }), { status: 401 }));

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
  resetSessionEnded();
  window.history.pushState({}, '', '/plans/7');
});
afterEach(() => vitest.unstubAllGlobals());

describe('client session end (BR-191, TECH-126)', () => {
  test('AC-279 a refused call signs the person out of the client and says why', async () => {
    vitest.stubGlobal('fetch', refused());
    render(
      <AuthProvider initial={meAs('HEAD_TRAINER')}>
        <Probe />
      </AuthProvider>,
    );
    expect(screen.getByText(/in:HEAD_TRAINER/)).toBeInTheDocument();
    await act(async () => {
      await request('GET', '/api/horses').catch(() => undefined);
    });
    expect(screen.getByText(/out \| Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại\./)).toBeInTheDocument();
  });

  test('AC-280 signing in again as the same role returns to the same page, and the reason is cleared', async () => {
    vitest.stubGlobal('fetch', refused());
    vitest.mocked(api.login).mockResolvedValue(meAs('HEAD_TRAINER'));
    render(
      <AuthProvider initial={meAs('HEAD_TRAINER')}>
        <Probe />
      </AuthProvider>,
    );
    await act(async () => {
      await request('GET', '/api/horses').catch(() => undefined);
    });
    await act(async () => auth.login('ht', 'pw'));
    // The person is already on /plans/7, so there is nothing to navigate to.
    expect(screen.getByText('in:HEAD_TRAINER | no-reason | no-landing')).toBeInTheDocument();
  });

  test('AC-280 a Groom signing in on the screen of a Head Trainer lands on the Groom home', async () => {
    vitest.stubGlobal('fetch', refused());
    vitest.mocked(api.login).mockResolvedValue(meAs('GROOM'));
    render(
      <AuthProvider initial={meAs('HEAD_TRAINER')}>
        <Probe />
      </AuthProvider>,
    );
    await act(async () => {
      await request('GET', '/api/horses').catch(() => undefined);
    });
    await act(async () => auth.login('groom', 'pw'));
    expect(screen.getByText('in:GROOM | no-reason | /incidents')).toBeInTheDocument();
  });

  test('signing out keeps no reason and a refused sign-out call still signs the person out', async () => {
    vitest.stubGlobal('fetch', refused());
    vitest.mocked(api.logout).mockRejectedValue(new (await import('../api/client')).ApiError(401, 'UNAUTHENTICATED', 'x'));
    render(
      <AuthProvider initial={meAs('HEAD_TRAINER')}>
        <Probe />
      </AuthProvider>,
    );
    await act(async () => auth.logout());
    expect(screen.getByText('out | no-reason | no-landing')).toBeInTheDocument();
  });
});
