import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import { LeaveDialog } from './LeaveDialog';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { pageOf } from '../test/page';
import type { Account, Leave } from '../api/types';

vitest.mock('../api/api', () => ({ api: { leave: vitest.fn(), leaveNotice: vitest.fn(), recordLeave: vitest.fn(), cancelLeave: vitest.fn() } }));

const groom = { id: 7, username: 'g', displayName: 'Groom An', role: 'GROOM', active: true, createdAt: '' } as Account;
const leave = (id: number, reason: string): Leave =>
  ({ id, accountId: 7, date: '2030-01-10', reason, cancelled: false, late: false, noticeDays: 7, recordedBy: 'QL' }) as unknown as Leave;

beforeEach(() => {
  vitest.mocked(api.leaveNotice).mockResolvedValue({ days: 7 } as never);
});

describe('leave list of a Groom (BR-167, BR-185)', () => {
  test('a long list is searched on the server and an empty answer says nothing matches', async () => {
    vitest.mocked(api.leave).mockImplementation((async (_id: number, query: { q?: string } = {}) =>
      query.q ? pageOf([], 0) : pageOf([leave(1, 'Nghỉ phép')], 21)) as never);
    renderAs('CLUB_MANAGER', <LeaveDialog groom={groom} onClose={() => undefined} />);
    await userEvent.type(await screen.findByRole('searchbox', { name: 'Tìm kiếm' }), 'xyz');
    await vitest.waitFor(() => expect(api.leave).toHaveBeenLastCalledWith(7, { page: 0, q: 'xyz' }));
    expect(await screen.findByText('Không có kết quả phù hợp')).toBeInTheDocument();
    expect(screen.getByRole('searchbox')).toHaveValue('xyz');
  });

  test('a short list has no search box', async () => {
    vitest.mocked(api.leave).mockResolvedValue(pageOf([leave(1, 'Nghỉ phép')]) as never);
    renderAs('CLUB_MANAGER', <LeaveDialog groom={groom} onClose={() => undefined} />);
    expect(await screen.findByText('Nghỉ phép')).toBeInTheDocument();
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument();
  });
});
