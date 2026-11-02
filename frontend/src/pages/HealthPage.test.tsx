import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import HealthPage from './HealthPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import type { HorseHealth } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: { herdHealth: vitest.fn(), healthSummary: vitest.fn(), barn: vitest.fn(), horsePhotos: vitest.fn() },
}));

const REASON = 'Nghi ngờ đau chân sau, khóa sau khi buổi tập kết thúc';
const row = (over: Partial<HorseHealth> = {}): HorseHealth => ({
  horseId: 4, horseName: 'Bạch Mã', registrationNumber: 'VN-004', status: 'INJURED',
  lock: { kind: 'MANUAL', reason: REASON, placedBy: 'BS Lan', placedAt: '2026-10-30T01:00:00Z', reviewDate: null } as HorseHealth['lock'],
  activity: 'IDLE', readiness: 'NOT_READY', newUnexamined: false, history: [], ...over,
});

beforeEach(() => {
  vitest.mocked(api.healthSummary).mockResolvedValue({
    total: 5, byStatus: { ELIGIBLE: 3, UNDER_OBSERVATION: 1, INJURED: 1, QUARANTINED: 0 }, locked: 1,
  });
  vitest.mocked(api.herdHealth).mockResolvedValue(pageOf([row()]));
  vitest.mocked(api.barn).mockResolvedValue({ areas: [], unplaced: [] } as never);
});

describe('health page tabs (BR-209, DEC-069)', () => {
  test('AC-424 opens on the overview: counts and a chart, no list under them', async () => {
    renderAs('VETERINARIAN', <HealthPage />);
    expect(await screen.findByText('Đang khóa')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Phân bố trạng thái sức khỏe' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Bạch Mã' })).toBeNull();
    expect(api.herdHealth).not.toHaveBeenCalled();
  });

  test('AC-441 and AC-442 the list shows the lock kind with a button for the reason, and the column reads "Trạng thái"', async () => {
    renderAs('VETERINARIAN', <HealthPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Danh sách' }));
    const link = await screen.findByRole('link', { name: 'Bạch Mã' });
    const tr = link.closest('tr')!;
    expect(within(tr).getByText('Thủ công')).toBeInTheDocument();
    expect(within(tr).queryByText(new RegExp(REASON))).toBeNull();
    expect(screen.getByRole('columnheader', { name: 'Trạng thái' })).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Hoạt động' })).toBeNull();
    // AC-423: the reason opens in a popup from the button.
    await userEvent.click(within(tr).getByRole('button', { name: /Lý do khóa — Bạch Mã/ }));
    expect(within(screen.getByRole('dialog')).getByText(REASON)).toBeInTheDocument();
  });

  test('a horse without a lock says so, and the filters of the list stay when the tab is left and reopened', async () => {
    vitest.mocked(api.herdHealth).mockResolvedValue(pageOf([row({ lock: null, status: 'ELIGIBLE' })]));
    renderAs('HEAD_TRAINER', <HealthPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Danh sách' }));
    await userEvent.selectOptions(await screen.findByLabelText('Trạng thái sức khỏe'), 'ELIGIBLE');
    expect(await screen.findByText('Không khóa')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Tổng quan' }));
    await userEvent.click(screen.getByRole('button', { name: 'Danh sách' }));
    expect(await screen.findByLabelText('Trạng thái sức khỏe')).toHaveValue('ELIGIBLE');
  });
});
