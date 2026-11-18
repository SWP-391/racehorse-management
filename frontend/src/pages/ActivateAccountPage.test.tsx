import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi as vitest } from 'vitest';
import ActivateAccountPage from './ActivateAccountPage';
import { api } from '../api/api';
import { ApiError } from '../api/client';

vitest.mock('../api/api', () => ({ api: { recoveryConfirm: vitest.fn() } }));

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/activate']}>
      <Routes>
        <Route path="/activate" element={<ActivateAccountPage />} />
        <Route path="/" element={<p>trang đăng nhập</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function fill(password = 'chosen-password-1', repeat = password) {
  await userEvent.type(screen.getByLabelText('Email'), ' moi@racehorse.example ');
  await userEvent.type(screen.getByLabelText('Mã xác nhận'), ' 123456 ');
  await userEvent.type(screen.getByLabelText('Mật khẩu mới'), password);
  await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu mới'), repeat);
}

beforeEach(() => {
  vitest.mocked(api.recoveryConfirm).mockReset().mockResolvedValue(undefined as never);
});

describe('account activation page (BR-237)', () => {
  test('AC-509 email, code and a new password are sent and the person goes to sign-in', async () => {
    renderPage();
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Kích hoạt tài khoản' }));
    expect(api.recoveryConfirm).toHaveBeenCalledWith({ email: 'moi@racehorse.example', code: '123456', newPassword: 'chosen-password-1' });
    expect(await screen.findByText('trang đăng nhập')).toBeInTheDocument();
  });

  test('empty fields, a short password and two different passwords send nothing', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: 'Kích hoạt tài khoản' }));
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Bắt buộc nhập.');
    expect(screen.getByLabelText('Mã xác nhận')).toHaveAccessibleDescription('Bắt buộc nhập.');
    expect(screen.getByLabelText('Mật khẩu mới')).toHaveAccessibleDescription('Mật khẩu cần 8 ký tự trở lên.');
    await fill('chosen-password-1', 'chosen-password-2');
    await userEvent.click(screen.getByRole('button', { name: 'Kích hoạt tài khoản' }));
    expect(screen.getByLabelText('Nhập lại mật khẩu mới')).toHaveAccessibleDescription('Hai mật khẩu chưa giống nhau.');
    expect(api.recoveryConfirm).not.toHaveBeenCalled();
  });

  test('AC-510 a refused code is said on the code field and the page stays', async () => {
    vitest.mocked(api.recoveryConfirm).mockRejectedValue(
      new ApiError(400, 'INVALID_OR_EXPIRED_CODE', 'Mã xác nhận không đúng hoặc đã hết hạn.', { code: 'Mã xác nhận không đúng hoặc đã hết hạn.' }),
    );
    renderPage();
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Kích hoạt tài khoản' }));
    expect(await screen.findByLabelText('Mã xác nhận')).toHaveAccessibleDescription('Mã xác nhận không đúng hoặc đã hết hạn.');
    expect(screen.queryByText('trang đăng nhập')).not.toBeInTheDocument();
    expect(screen.getByText(/Quên mật khẩu\?/)).toBeInTheDocument();
  });
});
