import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import ProgressPage from './ProgressPage';
import TrainingSection from '../components/TrainingSection';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import type { BoardRow, Session } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({ api: { progress: vitest.fn(), schedule: vitest.fn(), plansOf: vitest.fn() } }));

const done = { id: 1, date: '2026-10-20', type: 'GALLOP', distanceM: 1500, form: 4, state: 'COMPLETED' } as unknown as Session;

describe('progress board and Owner schedule (task 8.3)', () => {
  test('AC-065 each horse shows status, current plan and recent sessions', async () => {
    const row: BoardRow = {
      horseId: 9, horseName: 'Sao Mai', status: 'ELIGIBLE', readiness: 'READY', locked: false, newUnexamined: false,
      currentPlan: { id: 5, horseId: 9, title: 'Giáo án mùa thu', state: 'ACTIVE', cancelReason: null, completionSuggested: false, phases: [], assignedTrainer: null, canWrite: true },
      recentSessions: [done], trend: [done], assignedTrainer: { id: 1, displayName: 'Test HEAD_TRAINER' }, canWrite: true,
    };
    vitest.mocked(api.progress).mockResolvedValue(pageOf([row]));
    renderAs('HEAD_TRAINER', <ProgressPage />);
    expect(await screen.findByRole('link', { name: 'Sao Mai' })).toBeInTheDocument();
    expect(within(screen.getByRole('link', { name: 'Sao Mai' }).closest('tr')!).getByText('Đủ điều kiện')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Giáo án mùa thu/ })).toBeInTheDocument();
    // The recent sessions open in a popup from the eye button, not in the narrow column.
    await userEvent.click(screen.getByRole('button', { name: /Buổi tập gần đây — Sao Mai/ }));
    expect(within(screen.getByRole('dialog')).getByText(/Phi, 1500 m/)).toBeInTheDocument();
  });

  test('AC-290 the board shows every horse with its trainer and a toggle narrows it to the horses of the user', async () => {
    const mineRow: BoardRow = {
      horseId: 9, horseName: 'Sao Mai', status: 'ELIGIBLE', readiness: 'READY', locked: false, newUnexamined: false,
      currentPlan: null, recentSessions: [], trend: [], assignedTrainer: { id: 1, displayName: 'Test HEAD_TRAINER' }, canWrite: true,
    };
    const theirRow: BoardRow = { ...mineRow, horseId: 10, horseName: 'Gió Bắc', assignedTrainer: { id: 2, displayName: 'Trần Quốc Hoàng' }, canWrite: false };
    vitest.mocked(api.progress).mockResolvedValueOnce(pageOf([mineRow, theirRow])).mockResolvedValue(pageOf([mineRow]));
    renderAs('HEAD_TRAINER', <ProgressPage />);
    // Off by default: REQ-001, the whole club is on the board, each horse with the trainer who writes for it.
    expect(await screen.findByRole('link', { name: 'Gió Bắc' })).toBeInTheDocument();
    expect(screen.getByText('Trần Quốc Hoàng')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Chỉ ngựa của tôi' }));
    await vitest.waitFor(() => expect(api.progress).toHaveBeenLastCalledWith(expect.objectContaining({ trainerId: '1' })));
    await vitest.waitFor(() => expect(screen.queryByRole('link', { name: 'Gió Bắc' })).not.toBeInTheDocument());
  });

  test('AC-068 an Owner is refused', () => {
    renderAs('HORSE_OWNER', <ProgressPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
  });

  test('AC-114 the Owner sees blocked sessions as Bị chặn do khóa', async () => {
    vitest.mocked(api.schedule).mockResolvedValue(pageOf([
      { id: 3, date: '2026-10-22', startTime: '07:00:00', type: 'TROT', state: 'BLOCKED_BY_LOCK', form: null, comment: null, videoCount: 0, plannedEnd: null, plannedEndNextDay: false, endedAt: null },
    ]));
    renderAs('HORSE_OWNER', <TrainingSection horseId={9} archived={false} />);
    expect(await screen.findByText('Bị chặn do khóa')).toBeInTheDocument();
    expect(api.plansOf).not.toHaveBeenCalled();
  });
});
