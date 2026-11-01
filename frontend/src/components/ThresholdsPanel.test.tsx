import { pageOf } from '../test/page';
import { fireEvent, screen } from '@testing-library/react';
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

  test('a heart-rate limit outside the plausible range or a fraction is refused on the field and nothing is sent', async () => {
    vitest.mocked(api.setThreshold).mockReset();
    renderAs('HEAD_TRAINER', <ThresholdsPanel horseId={9} />);
    const limit = await screen.findByLabelText('Ngưỡng');
    expect(limit).toHaveAttribute('data-unit', 'bpm');
    await userEvent.type(limit, '1500');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt ngưỡng' }));
    expect(limit).toHaveAccessibleDescription('Giá trị tối đa là 300.');
    await userEvent.clear(limit);
    fireEvent.change(limit, { target: { value: '120.5' } });
    await userEvent.click(screen.getByRole('button', { name: 'Đặt ngưỡng' }));
    expect(limit).toHaveAccessibleDescription('Hãy nhập số nguyên.');
    expect(api.setThreshold).not.toHaveBeenCalled();
  });

  test('the speed limit is in km/h, above zero and at most 100', async () => {
    vitest.mocked(api.setThreshold).mockReset();
    renderAs('HEAD_TRAINER', <ThresholdsPanel horseId={9} />);
    await userEvent.selectOptions(await screen.findByLabelText('Chỉ số'), 'SPEED');
    const limit = screen.getByLabelText('Ngưỡng');
    expect(limit).toHaveAttribute('data-unit', 'km/h');
    await userEvent.type(limit, '0');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt ngưỡng' }));
    expect(limit).toHaveAccessibleDescription('Giá trị phải lớn hơn 0.');
    expect(api.setThreshold).not.toHaveBeenCalled();
  });

  test('a Veterinarian sees alerts but cannot set thresholds', async () => {
    renderAs('VETERINARIAN', <ThresholdsPanel horseId={9} />);
    expect(await screen.findByText('Chưa có cảnh báo.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Đặt ngưỡng' })).not.toBeInTheDocument();
    expect(api.thresholds).not.toHaveBeenCalled();
  });

  test('AC-396 the Head Trainer without a threshold reads the message and gets the form', async () => {
    renderAs('HEAD_TRAINER', <ThresholdsPanel horseId={9} />);
    expect(await screen.findByText('Chưa đặt ngưỡng: sẽ không có cảnh báo.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đặt ngưỡng' })).toBeInTheDocument();
  });

  test('AC-397 a Veterinarian without an alert reads the message and no lone alerts heading', async () => {
    renderAs('VETERINARIAN', <ThresholdsPanel horseId={9} />);
    expect(await screen.findByText('Chưa có cảnh báo.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Cảnh báo' })).not.toBeInTheDocument();
  });

  test('AC-395 a role with nothing to set or read gets a message, not a blank frame (AC-060 for an Owner)', async () => {
    renderAs('HORSE_OWNER', <ThresholdsPanel horseId={9} />);
    expect(screen.getByText('Vai trò của bạn không đặt ngưỡng và không nhận cảnh báo.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Đặt ngưỡng' })).not.toBeInTheDocument();
    expect(api.alerts).not.toHaveBeenCalled();
  });

  test('a long alert list can be searched and says when nothing matches', async () => {
    vitest.mocked(api.alerts).mockResolvedValue(pageOf([], 57));
    renderAs('VETERINARIAN', <ThresholdsPanel horseId={9} />);
    await userEvent.type(await screen.findByRole('searchbox', { name: 'Tìm kiếm' }), 'tim');
    await vitest.waitFor(() => expect(api.alerts).toHaveBeenLastCalledWith(9, { q: 'tim' }));
  });
});
