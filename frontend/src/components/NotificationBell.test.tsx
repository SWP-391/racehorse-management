import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi as vitest } from 'vitest';
import { ApiError } from '../api/client';
import NotificationBell from './NotificationBell';
import { api } from '../api/api';
import { notify } from '../lib/notify';

vitest.mock('../api/api', () => ({ api: { notifications: vitest.fn(), markRead: vitest.fn() } }));
vitest.mock('../lib/notify', () => ({ notify: { error: vitest.fn(), success: vitest.fn() } }));

describe('notification bell (task 5.3, NFR-006)', () => {
  test('shows the unread count and marks a notification read', async () => {
    vitest
      .mocked(api.notifications)
      .mockResolvedValueOnce({
        unread: 1,
        items: [{ id: 4, message: 'Ngựa "Sao Mai" bị khóa huấn luyện: sưng.', link: '/horses/9', createdAt: '', read: false }],
        page: 0, size: 20, total: 1,
      })
      .mockResolvedValue({ unread: 0, items: [], page: 0, size: 20, total: 0 });
    vitest.mocked(api.markRead).mockResolvedValue({});
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Thông báo (1)' }));
    expect(screen.getByRole('link', { name: /bị khóa huấn luyện/ })).toHaveAttribute('href', '/horses/9');
    await userEvent.click(screen.getByRole('button', { name: 'Đã đọc' }));
    expect(api.markRead).toHaveBeenCalledWith(4);
    expect(await screen.findByRole('button', { name: 'Thông báo (0)' })).toBeInTheDocument();
  });
});

describe('notification bell states (verifier finding: unknown is not zero)', () => {
  const open = async () => {
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole('button', { name: /^Thông báo/ }));
  };

  test('before the first answer it shows no count and no "empty" message', async () => {
    vitest.mocked(api.notifications).mockReturnValue(new Promise(() => undefined));
    await open();
    expect(screen.getByRole('button', { name: 'Thông báo' })).toBeInTheDocument();
    expect(screen.queryByText('Không có thông báo.')).not.toBeInTheDocument();
  });

  test('a failed load shows the message and Retry loads again', async () => {
    vitest
      .mocked(api.notifications)
      .mockRejectedValueOnce(new ApiError(503, 'UNAVAILABLE', 'Dịch vụ tạm thời không sẵn sàng.'))
      .mockResolvedValue({ unread: 0, items: [], page: 0, size: 20, total: 0 });
    await open();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Dịch vụ tạm thời không sẵn sàng.');
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Không có thông báo.')).toBeInTheDocument();
  });

  test('a refused mark-as-read is reported, not swallowed', async () => {
    vitest.mocked(api.notifications).mockResolvedValue({
      unread: 1,
      items: [{ id: 4, message: 'Ngựa bị khóa.', link: null, createdAt: '', read: false }],
        page: 0, size: 20, total: 1,
    });
    const refusal = new ApiError(500, 'X', 'Không đánh dấu được.');
    vitest.mocked(api.markRead).mockRejectedValue(refusal);
    await open();
    await userEvent.click(await screen.findByRole('button', { name: 'Đã đọc' }));
    expect(api.markRead).toHaveBeenCalledWith(4);
    expect(notify.error).toHaveBeenCalledWith(refusal);
    // The row stays unread: the failed call changed nothing on screen.
    expect(screen.getByRole('button', { name: 'Đã đọc' })).toBeInTheDocument();
  });
});

describe('reassign notice (BR-136, XDEP-017)', () => {
  test('AC-244 the notice naming the Groom and the sessions is listed in the inbox the server returns', async () => {
    vitest.mocked(api.notifications).mockResolvedValue({
      unread: 1,
      page: 0,
      size: 20,
      total: 1,
      items: [
        {
          id: 9,
          message: 'Groom Nguyễn An đã bị vô hiệu hóa. Cần giao lại 1 buổi tập: Phi của ngựa "Sao Mai" ngày 23/10/2026.',
          link: '/horses',
          createdAt: '',
          read: false,
        },
      ],
    });
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Thông báo (1)' }));
    expect(screen.getByRole('link', { name: /Cần giao lại 1 buổi tập/ })).toHaveAttribute('href', '/horses');
  });
});
