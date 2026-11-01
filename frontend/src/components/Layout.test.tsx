import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi as vitest } from 'vitest';
import Layout from './Layout';
import LoginPage from '../pages/LoginPage';
import { renderAs } from '../test/render';
import { AuthProvider } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { api } from '../api/api';

vitest.mock('../api/api', () => ({
  api: {
    login: vitest.fn(),
    logout: vitest.fn(),
    me: vitest.fn(),
    notifications: vitest.fn().mockResolvedValue({ unread: 0, items: [] }),
  },
}));

function menu() {
  return within(screen.getByRole('navigation', { name: 'main' }))
    .queryAllByRole('link')
    .map((l) => l.textContent);
}

describe('role-aware navigation (task 7.1, BR-091)', () => {
  test('Club Manager sees horses, health, accounts, audit log and settings', () => {
    renderAs('CLUB_MANAGER', <Layout />);
    expect(menu()).toEqual(['Ngựa', 'Sức khỏe', 'Việc hằng ngày', 'Chuồng trại', 'Vật tư', 'Chi phí', 'Tài khoản', 'Nhật ký thao tác', 'Cài đặt']);
  });

  test('Head Trainer sees horses, health and the progress board', () => {
    renderAs('HEAD_TRAINER', <Layout />);
    expect(menu()).toEqual(['Ngựa', 'Sức khỏe', 'Tiến độ', 'Việc hằng ngày', 'Chuồng trại', 'Thời gian biểu']);
  });

  test('Veterinarian sees horses, health, care and incidents', () => {
    renderAs('VETERINARIAN', <Layout />);
    expect(menu()).toEqual(['Ngựa', 'Sức khỏe', 'Chăm sóc định kỳ', 'Sự cố', 'Việc hằng ngày', 'Chuồng trại']);
  });

  test('Horse Owner sees horses, own chores and own barn', () => {
    renderAs('HORSE_OWNER', <Layout />);
    expect(menu()).toEqual(['Ngựa', 'Việc hằng ngày', 'Chuồng trại', 'Chi phí']);
  });

  test('Groom sees incident reports and the sessions assigned to them (PERM-032)', () => {
    renderAs('GROOM', <Layout />);
    expect(menu()).toEqual(['Sự cố', 'Việc hằng ngày', 'Chuồng trại', 'Vật tư', 'Thời gian biểu', 'Buổi tập được giao']);
  });
});

describe('sign-in screen', () => {
  test('shows the service message when sign-in is refused', async () => {
    vitest.mocked(api.login).mockRejectedValue(
      new ApiError(401, 'INVALID_CREDENTIALS', 'Sai tên đăng nhập hoặc mật khẩu, hoặc tài khoản đã bị vô hiệu hóa.'),
    );
    render(
      <AuthProvider initial={null}>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    );
    await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'x');
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'y');
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('tài khoản đã bị vô hiệu hóa');
  });
});
