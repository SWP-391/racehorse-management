import { screen } from '@testing-library/react';
import { vi as vitest } from 'vitest';
import Layout from '../Layout';
import { renderAs } from '../../test/render';
import { api } from '../../api/api';
import { ROLES } from '../../api/types';

vitest.mock('../../api/api', () => ({
  api: { logout: vitest.fn(), notifications: vitest.fn().mockResolvedValue({ unread: 0, items: [] }), inboxUnread: vitest.fn() },
}));

beforeEach(() => {
  vitest.mocked(api.inboxUnread).mockReset().mockResolvedValue({ unread: 3 });
});

describe('inbox menu entry (PERM-061, BR-233)', () => {
  test.each(ROLES)('%s sees "Hộp thư" with the unread count', async (role) => {
    renderAs(role, <Layout />);
    const links = screen.getAllByRole('link', { name: /Hộp thư/ });
    expect(links.length).toBeGreaterThan(0);
    expect((await screen.findAllByRole('status', { name: '3 tin chưa đọc' })).length).toBeGreaterThan(0);
  });

  test('no count shows while the first answer has not arrived (unknown is not zero)', () => {
    vitest.mocked(api.inboxUnread).mockReturnValue(new Promise(() => undefined));
    renderAs('HORSE_OWNER', <Layout />);
    expect(screen.getAllByRole('link', { name: /Hộp thư/ }).length).toBeGreaterThan(0);
    expect(screen.queryByRole('status', { name: /tin chưa đọc/ })).toBeNull();
  });

  test('no badge when nothing is unread, and "9+" above nine', async () => {
    vitest.mocked(api.inboxUnread).mockResolvedValue({ unread: 0 });
    renderAs('GROOM', <Layout />);
    await vitest.waitFor(() => expect(api.inboxUnread).toHaveBeenCalled());
    expect(screen.queryByRole('status', { name: /tin chưa đọc/ })).toBeNull();
  });

  test('a failed answer shows no count and breaks nothing', async () => {
    vitest.mocked(api.inboxUnread).mockRejectedValue(new Error('down'));
    renderAs('VETERINARIAN', <Layout />);
    await vitest.waitFor(() => expect(api.inboxUnread).toHaveBeenCalled());
    expect(screen.getAllByRole('link', { name: /Hộp thư/ }).length).toBeGreaterThan(0);
    expect(screen.queryByRole('status', { name: /tin chưa đọc/ })).toBeNull();
  });
});
