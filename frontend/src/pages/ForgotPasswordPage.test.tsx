import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi as vitest } from 'vitest';
import ForgotPasswordPage from './ForgotPasswordPage';
import { api } from '../api/api';
import { ApiError } from '../api/client';

vitest.mock('../api/api', () => ({ api: { recoveryRequest: vitest.fn(), recoveryConfirm: vitest.fn() } }));

const ANSWER = { message: 'Nếu email thuộc một tài khoản đang hoạt động, mã xác nhận đã được gửi.', expiresInSeconds: 300, resendAfterSeconds: 60 };

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/forgot-password']}>
      <Routes>
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/" element={<p>trang đăng nhập</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function askForTheCode(email = 'an.le@racehorse.example') {
  await userEvent.type(screen.getByLabelText('Email'), email);
  await userEvent.click(screen.getByRole('button', { name: 'Gửi mã xác nhận' }));
}

beforeEach(() => {
  vitest.mocked(api.recoveryRequest).mockReset().mockResolvedValue(ANSWER);
  vitest.mocked(api.recoveryConfirm).mockReset().mockResolvedValue(undefined);
});

describe('recovery of a forgotten password (BR-214 to BR-217)', () => {
  test('AC-461 step 1 sends the email and moves to the code step with the same notice for any address', async () => {
    renderPage();
    await askForTheCode('nobody@racehorse.example');
    expect(api.recoveryRequest).toHaveBeenCalledWith('nobody@racehorse.example');
    expect(await screen.findByLabelText('Mã xác nhận')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(ANSWER.message);
  });

  test('AC-460 no code is ever shown, even when the answer carries one by mistake', async () => {
    vitest.mocked(api.recoveryRequest).mockResolvedValue({ ...ANSWER, code: '123456', simulatedCode: '123456' } as never);
    renderPage();
    await askForTheCode();
    await screen.findByLabelText('Mã xác nhận');
    expect(document.body).not.toHaveTextContent('123456');
    expect(screen.getByLabelText('Mã xác nhận')).toHaveValue('');
  });

  test('an email that is not valid is named on the field and the step does not change', async () => {
    vitest.mocked(api.recoveryRequest).mockRejectedValue(new ApiError(400, 'INVALID_EMAIL', 'Email không hợp lệ.', { email: 'Email không hợp lệ.' }));
    renderPage();
    await askForTheCode('an.le@');
    expect(await screen.findByLabelText('Email')).toHaveAccessibleDescription('Email không hợp lệ.');
    expect(screen.queryByLabelText('Mã xác nhận')).not.toBeInTheDocument();
  });

  test('AC-467 a request that comes too soon shows the time to wait', async () => {
    vitest.mocked(api.recoveryRequest).mockRejectedValue(
      new ApiError(429, 'TOO_MANY_REQUESTS', 'Vui lòng thử lại sau 55 giây.', { retryAfterSeconds: '55' }),
    );
    renderPage();
    await askForTheCode();
    expect(await screen.findByRole('alert')).toHaveTextContent('Vui lòng thử lại sau 55 giây.');
    expect(screen.queryByLabelText('Mã xác nhận')).not.toBeInTheDocument();
  });

  test('step 2 checks the passwords before sending anything', async () => {
    renderPage();
    await askForTheCode();
    await userEvent.type(await screen.findByLabelText('Mã xác nhận'), '123456');
    await userEvent.type(screen.getByLabelText('Mật khẩu mới'), 'short');
    await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu mới' }));
    expect(screen.getByLabelText('Mật khẩu mới')).toHaveAccessibleDescription('Mật khẩu cần 8 ký tự trở lên.');

    await userEvent.clear(screen.getByLabelText('Mật khẩu mới'));
    await userEvent.type(screen.getByLabelText('Mật khẩu mới'), 'new-password-456');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu mới' }));
    expect(screen.getByLabelText('Nhập lại mật khẩu mới')).toHaveAccessibleDescription('Hai mật khẩu chưa giống nhau.');
    expect(api.recoveryConfirm).not.toHaveBeenCalled();
  });

  test('AC-460 a right code with a new password returns to sign-in', async () => {
    renderPage();
    await askForTheCode();
    await userEvent.type(await screen.findByLabelText('Mã xác nhận'), ' 123456 ');
    await userEvent.type(screen.getByLabelText('Mật khẩu mới'), 'new-password-456');
    await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'new-password-456');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu mới' }));
    expect(api.recoveryConfirm).toHaveBeenCalledWith({ email: 'an.le@racehorse.example', code: '123456', newPassword: 'new-password-456' });
    expect(await screen.findByText('trang đăng nhập')).toBeInTheDocument();
  });

  test('AC-462 AC-463 a refused code keeps the step and says why on the code field', async () => {
    vitest.mocked(api.recoveryConfirm).mockRejectedValue(
      new ApiError(400, 'INVALID_OR_EXPIRED_CODE', 'Mã xác nhận không đúng hoặc đã hết hạn.', { code: 'Mã xác nhận không đúng hoặc đã hết hạn.' }),
    );
    renderPage();
    await askForTheCode();
    await userEvent.type(await screen.findByLabelText('Mã xác nhận'), '000000');
    await userEvent.type(screen.getByLabelText('Mật khẩu mới'), 'new-password-456');
    await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'new-password-456');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu mới' }));
    expect(await screen.findByLabelText('Mã xác nhận')).toHaveAccessibleDescription('Mã xác nhận không đúng hoặc đã hết hạn.');
    expect(screen.queryByText('trang đăng nhập')).not.toBeInTheDocument();
  });

  test('resending waits for the countdown, then asks again', async () => {
    vitest.useFakeTimers({ shouldAdvanceTime: true });
    try {
      renderPage();
      await askForTheCode();
      const resend = await screen.findByRole('button', { name: /Gửi lại mã sau 60 giây/ });
      expect(resend).toBeDisabled();
      // One tick a second: the countdown schedules the next tick only after each render.
      for (let i = 0; i < 61; i++) {
        await act(async () => {
          vitest.advanceTimersByTime(1000);
        });
      }
      const ready = screen.getByRole('button', { name: 'Gửi lại mã' });
      expect(ready).toBeEnabled();
      await userEvent.click(ready);
      expect(api.recoveryRequest).toHaveBeenCalledTimes(2);
    } finally {
      vitest.useRealTimers();
    }
  });

  test('"Dùng email khác" goes back to the first step', async () => {
    renderPage();
    await askForTheCode();
    await userEvent.click(await screen.findByRole('button', { name: 'Dùng email khác' }));
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
    expect(screen.queryByLabelText('Mã xác nhận')).not.toBeInTheDocument();
  });
});
