import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi as vitest } from 'vitest';
import LoginPage from './LoginPage';
import { AuthProvider } from '../auth/AuthContext';
import { api } from '../api/api';
import { ApiError, request, resetSessionEnded } from '../api/client';

vitest.mock('../api/api', () => ({ api: { login: vitest.fn(), logout: vitest.fn(), me: vitest.fn() } }));

const EXPIRED = 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.';

async function endSession() {
  vitest.stubGlobal('fetch', vitest.fn().mockResolvedValue(new Response('{}', { status: 401 })));
  await act(async () => {
    await request('GET', '/api/horses').catch(() => undefined);
  });
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
  resetSessionEnded();
  render(
    <AuthProvider initial={null}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </AuthProvider>,
  );
});
afterEach(() => vitest.unstubAllGlobals());

describe('sign-in after an ended session (BR-191)', () => {
  test('AC-279 the page explains that the session expired', async () => {
    expect(screen.queryByText(EXPIRED)).not.toBeInTheDocument();
    await endSession();
    expect(screen.getByText(EXPIRED)).toBeInTheDocument();
  });

  test('editing the form clears the message', async () => {
    await endSession();
    expect(screen.getByText(EXPIRED)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'x');
    expect(screen.queryByText(EXPIRED)).not.toBeInTheDocument();
  });

  test('AC-281 a refused sign-in keeps the expiry message, adds the refusal and does not loop', async () => {
    await endSession();
    vitest.mocked(api.login).mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'Sai tên đăng nhập hoặc mật khẩu.'));
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Sai tên đăng nhập hoặc mật khẩu.');
    expect(screen.getByText(EXPIRED)).toBeInTheDocument();
    expect(api.login).toHaveBeenCalledTimes(1);
  });

  test('a deactivated account shows the service message instead', async () => {
    vitest.stubGlobal(
      'fetch',
      vitest.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'ACCOUNT_DEACTIVATED', message: 'Tài khoản đã bị vô hiệu hóa.' }), { status: 401 })),
    );
    await act(async () => {
      await request('GET', '/api/horses').catch(() => undefined);
    });
    expect(screen.getByText('Tài khoản đã bị vô hiệu hóa.')).toBeInTheDocument();
    expect(screen.queryByText(EXPIRED)).not.toBeInTheDocument();
  });
});
