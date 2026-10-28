import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import MySessionsPage from './MySessionsPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({ api: { mySessions: vitest.fn(), acknowledgeSession: vitest.fn(), declineSession: vitest.fn() } }));

describe('Groom assigned sessions (task 3.3; slice 9 task 2.3)', () => {
  test('AC-180 assigned sessions are listed read-only with horse, date, type, planned duration and state', async () => {
    vitest.mocked(api.mySessions).mockResolvedValue(pageOf([
      { id: 1, horseId: 9, horseName: 'Sao Mai', date: '2026-10-23', startTime: '06:30:00', type: 'TRIAL_RUN', plannedDurationMin: 45, state: 'SCHEDULED', assignmentAck: 'ACKNOWLEDGED', plannedEnd: '07:15:00', plannedEndNextDay: false, endedAt: null },
      { id: 2, horseId: 10, horseName: 'Gió Bắc', date: '2026-10-24', startTime: null, type: 'TROT', plannedDurationMin: 60, state: 'SCHEDULED', assignmentAck: 'ACKNOWLEDGED', plannedEnd: null, plannedEndNextDay: false, endedAt: null },
    ]));
    renderAs('GROOM', <MySessionsPage />);
    const row = (await screen.findByText('Sao Mai')).closest('tr')!;
    expect(within(row).getByText('2026-10-23')).toBeInTheDocument();
    expect(within(row).getByText('06:30')).toBeInTheDocument();
    // A session without a start time has no start and no planned end (BR-120, BR-190).
    expect(within(screen.getByText('Gió Bắc').closest('tr')!).getAllByText('—')).toHaveLength(2);
    expect(within(row).getByText('Kết thúc dự kiến 07:15')).toBeInTheDocument();
    expect(within(row).getByText('Chạy thử')).toBeInTheDocument();
    expect(within(row).getByText('45')).toBeInTheDocument();
    expect(within(row).getByText('Đã lên lịch')).toBeInTheDocument();
    expect(screen.getByText('Gió Bắc')).toBeInTheDocument();
    // Read-only: the "Đã nhận" confirmation is already given; only the refusal stays possible until the start (BR-159).
    expect(screen.queryByRole('button', { name: 'Đã nhận' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  test('AC-182 nothing assigned shows an empty message', async () => {
    vitest.mocked(api.mySessions).mockResolvedValue(pageOf([]));
    renderAs('GROOM', <MySessionsPage />);
    expect(await screen.findByText('Bạn chưa được giao buổi tập nào.')).toBeInTheDocument();
  });

  test('AC-186 the Groom confirms "Đã nhận" for an unconfirmed assignment', async () => {
    const base = { id: 3, horseId: 9, horseName: 'Sao Mai', date: '2026-10-25', startTime: '07:00:00', type: 'GALLOP', plannedDurationMin: 60, state: 'SCHEDULED', plannedEnd: null, plannedEndNextDay: false, endedAt: null } as const;
    vitest.mocked(api.mySessions).mockResolvedValue(pageOf([{ ...base, assignmentAck: 'UNCONFIRMED', plannedEnd: null, plannedEndNextDay: false, endedAt: null }]));
    vitest.mocked(api.acknowledgeSession).mockResolvedValue({ ...base, assignmentAck: 'ACKNOWLEDGED', plannedEnd: null, plannedEndNextDay: false, endedAt: null });
    renderAs('GROOM', <MySessionsPage />);
    const row = (await screen.findByText('Sao Mai')).closest('tr')!;
    expect(within(row).getByText('Chưa xác nhận')).toBeInTheDocument();
    await userEvent.click(within(row).getByRole('button', { name: 'Đã nhận' }));
    expect(api.acknowledgeSession).toHaveBeenCalledWith(3);
    expect(await within(row).findByText('Đã nhận')).toBeInTheDocument();
    expect(within(row).queryByRole('button', { name: 'Đã nhận' })).not.toBeInTheDocument();
  });

  test('AC-301 and AC-302 the Groom declines with a mandatory reason and the list reloads', async () => {
    const base = { id: 3, horseId: 9, horseName: 'Sao Mai', date: '2026-10-25', startTime: '07:00:00', type: 'GALLOP', plannedDurationMin: 60, state: 'SCHEDULED', plannedEnd: null, plannedEndNextDay: false, endedAt: null, assignmentAck: 'UNCONFIRMED' } as const;
    vitest.mocked(api.mySessions).mockResolvedValueOnce(pageOf([base])).mockResolvedValue(pageOf([]));
    vitest.mocked(api.declineSession).mockResolvedValue(undefined);
    renderAs('GROOM', <MySessionsPage />);
    const row = (await screen.findByText('Sao Mai')).closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: 'Không nhận được' }));
    const dialog = await screen.findByRole('dialog', { name: 'Không nhận buổi tập' });
    await userEvent.type(within(dialog).getByLabelText(/Lý do/), 'Bận việc gia đình');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Gửi' }));
    expect(api.declineSession).toHaveBeenCalledWith(3, 'Bận việc gia đình');
    expect(await screen.findByText('Bạn chưa được giao buổi tập nào.')).toBeInTheDocument();
  });

  test('AC-302 a refused decline shows the service message and keeps the dialog', async () => {
    const base = { id: 3, horseId: 9, horseName: 'Sao Mai', date: '2026-10-25', startTime: '07:00:00', type: 'GALLOP', plannedDurationMin: 60, state: 'SCHEDULED', plannedEnd: null, plannedEndNextDay: false, endedAt: null, assignmentAck: 'UNCONFIRMED' } as const;
    vitest.mocked(api.mySessions).mockResolvedValue(pageOf([base]));
    vitest.mocked(api.declineSession).mockRejectedValue(new ApiError(400, 'REASON_REQUIRED', 'Bắt buộc nhập lý do.'));
    renderAs('GROOM', <MySessionsPage />);
    const row = (await screen.findByText('Sao Mai')).closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: 'Không nhận được' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Gửi' }));
    expect(await within(dialog).findByText('Bắt buộc nhập lý do.')).toBeInTheDocument();
  });

  test('other roles have no assigned-sessions view', () => {
    renderAs('HEAD_TRAINER', <MySessionsPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
  });
});
