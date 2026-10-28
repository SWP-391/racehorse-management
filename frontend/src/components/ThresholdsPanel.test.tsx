import { pageOf } from '../test/page';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import ThresholdsPanel from './ThresholdsPanel';
import { renderAs } from '../test/render';
import { api } from '../api/api';

vitest.mock('../api/api', () => ({
  api: { thresholds: vitest.fn(), alerts: vitest.fn(), setThreshold: vitest.fn() },
}));

beforeEach(() => {
  vitest.mocked(api.thresholds).mockResolvedValue([]);
  vitest.mocked(api.alerts).mockResolvedValue(pageOf([]));
});

describe('thresholds and alerts (task 4.2, BR-052)', () => {
  test('AC-057 with no threshold the panel says no alert will be raised', async () => {
    renderAs('HEAD_TRAINER', <ThresholdsPanel horseId={9} />);
    expect(await screen.findByText('Chưa đặt ngưỡng: sẽ không có cảnh báo.')).toBeInTheDocument();
  });

  test('AC-056 the Head Trainer sets a threshold and sees alerts', async () => {
    vitest.mocked(api.alerts).mockResolvedValue(pageOf([
      { sessionId: 4, measure: 'HEART_RATE', limit: 100, value: 104, offsetSecond: 60, raisedAt: '', dataSource: 'SIMULATED', label: 'DỮ LIỆU MÔ PHỎNG' },
    ]));
    vitest.mocked(api.setThreshold).mockResolvedValue([{ measure: 'HEART_RATE', limit: 100, setAt: '' }]);
    renderAs('HEAD_TRAINER', <ThresholdsPanel horseId={9} />);
    await userEvent.type(await screen.findByLabelText('Ngưỡng'), '100');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt ngưỡng' }));
    expect(api.setThreshold).toHaveBeenCalledWith(9, 'HEART_RATE', 100);
    expect(await screen.findByText(/Nhịp tim \(bpm\): 100/)).toBeInTheDocument();
    expect(screen.getByText('Nhịp tim (bpm) vượt ngưỡng 100: 104 (DỮ LIỆU MÔ PHỎNG)')).toBeInTheDocument();
  });

  test('a Veterinarian sees alerts but cannot set thresholds', async () => {
    renderAs('VETERINARIAN', <ThresholdsPanel horseId={9} />);
    expect(await screen.findByText('Chưa có cảnh báo.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Đặt ngưỡng' })).not.toBeInTheDocument();
    expect(api.thresholds).not.toHaveBeenCalled();
  });

  test('AC-060 an Owner sees nothing', () => {
    renderAs('HORSE_OWNER', <ThresholdsPanel horseId={9} />);
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
    expect(api.alerts).not.toHaveBeenCalled();
  });

  test('a long alert list can be searched and says when nothing matches', async () => {
    vitest.mocked(api.alerts).mockResolvedValue(pageOf([], 57));
    renderAs('VETERINARIAN', <ThresholdsPanel horseId={9} />);
    await userEvent.type(await screen.findByRole('searchbox', { name: 'Tìm kiếm' }), 'tim');
    await vitest.waitFor(() => expect(api.alerts).toHaveBeenLastCalledWith(9, { q: 'tim' }));
  });
});
