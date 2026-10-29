import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import SettingsPage from './SettingsPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';

vitest.mock('../api/api', () => ({
  api: {
    gracePeriod: vitest.fn().mockResolvedValue({ minutes: 60 }),
    setGracePeriod: vitest.fn(),
    lateChangeThreshold: vitest.fn().mockResolvedValue({ hours: 12 }),
    setLateChangeThreshold: vitest.fn(),
    leaveNotice: vitest.fn().mockResolvedValue({ days: 7 }),
    setLeaveNotice: vitest.fn(),
    stockWarning: vitest.fn().mockResolvedValue({ days: 7 }),
    setStockWarning: vitest.fn(),
  },
}));

const thresholdForm = () =>
  screen.findByRole('form', { name: 'Đổi người phụ trách sát giờ: cảnh báo khi còn dưới (giờ)' });

describe('settings page: late-change threshold (BR-123)', () => {
  test('AC-203 the Club Manager sets the threshold to 6 hours', async () => {
    vitest.mocked(api.setLateChangeThreshold).mockResolvedValue({ hours: 6 });
    renderAs('CLUB_MANAGER', <SettingsPage />);
    const form = await thresholdForm();
    const input = within(form).getByRole('spinbutton');
    expect(input).toHaveValue(12);
    await userEvent.clear(input);
    await userEvent.type(input, '6');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(api.setLateChangeThreshold).toHaveBeenCalledWith(6);
    expect(await within(form).findByRole('status')).toHaveTextContent('✓');
  });

  test('AC-205 a value that is not a positive whole number of hours is refused', async () => {
    vitest
      .mocked(api.setLateChangeThreshold)
      .mockRejectedValue(new ApiError(400, 'INVALID_LATE_CHANGE_THRESHOLD', 'Ngưỡng đổi gấp phải là số giờ nguyên lớn hơn 0.'));
    renderAs('CLUB_MANAGER', <SettingsPage />);
    const form = await thresholdForm();
    const input = within(form).getByRole('spinbutton');
    await userEvent.clear(input);
    await userEvent.type(input, '1.5');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(api.setLateChangeThreshold).toHaveBeenCalledWith(1.5);
    expect(await screen.findByText('Ngưỡng đổi gấp phải là số giờ nguyên lớn hơn 0.')).toBeInTheDocument();
    expect(within(form).queryByRole('status')).not.toBeInTheDocument();
  });
});

describe('settings page: leave notice (BR-167, PERM-054)', () => {
  test('the Club Manager sets the notice to 3 days; a refusal keeps the shown value', async () => {
    vitest.mocked(api.setLeaveNotice).mockResolvedValueOnce({ days: 3 });
    renderAs('CLUB_MANAGER', <SettingsPage />);
    const form = await screen.findByRole('form', { name: 'Số ngày báo trước nghỉ phép' });
    const input = within(form).getByRole('spinbutton');
    expect(input).toHaveValue(7);
    await userEvent.clear(input);
    await userEvent.type(input, '3');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(api.setLeaveNotice).toHaveBeenCalledWith(3);
    expect(await within(form).findByRole('status')).toHaveTextContent('✓');

    vitest
      .mocked(api.setLeaveNotice)
      .mockRejectedValueOnce(new ApiError(400, 'INVALID_LEAVE_NOTICE', 'Số ngày báo trước phải là số nguyên lớn hơn 0.'));
    await userEvent.clear(input);
    await userEvent.type(input, '0');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(await within(form).findByRole('alert')).toHaveTextContent('Số ngày báo trước phải là số nguyên lớn hơn 0.');
  });
});

describe('settings page: stock warning (BR-175)', () => {
  test('AC-337 the Club Manager sets the stock warning to 10 days; the service refuses a bad value', async () => {
    vitest.mocked(api.setStockWarning).mockResolvedValue({ days: 10 });
    renderAs('CLUB_MANAGER', <SettingsPage />);
    const form = await screen.findByRole('form', { name: 'Số ngày cảnh báo' });
    const input = within(form).getByRole('spinbutton');
    expect(input).toHaveValue(7);
    await userEvent.clear(input);
    await userEvent.type(input, '10');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(api.setStockWarning).toHaveBeenCalledWith(10);
    expect(await within(form).findByRole('status')).toHaveTextContent('✓');

    vitest.mocked(api.setStockWarning).mockRejectedValue(
      new ApiError(400, 'INVALID_STOCK_WARNING', 'Số ngày cảnh báo phải là số nguyên từ 1 đến 365.'),
    );
    await userEvent.clear(input);
    await userEvent.type(input, '366');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByText('Số ngày cảnh báo phải là số nguyên từ 1 đến 365.')).toBeInTheDocument();
  });
});
