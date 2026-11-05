import { fireEvent, screen, within } from '@testing-library/react';
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

  test('AC-205 a value that is not a positive whole number of hours is refused on the field and nothing is sent', async () => {
    vitest.mocked(api.setLateChangeThreshold).mockReset();
    renderAs('CLUB_MANAGER', <SettingsPage />);
    const form = await thresholdForm();
    const input = within(form).getByRole('spinbutton');
    for (const [bad, message] of [['1.5', 'Hãy nhập số nguyên.'], ['0', 'Giá trị tối thiểu là 1.'], ['-3', 'Giá trị tối thiểu là 1.'], ['999', 'Giá trị tối đa là 168.'], ['', 'Bắt buộc nhập.']]) {
      fireEvent.change(input, { target: { value: bad } });
      await userEvent.click(within(form).getByRole('button', { name: 'Lưu' }));
      expect(input, bad).toHaveAccessibleDescription(new RegExp(message));
    }
    expect(api.setLateChangeThreshold).not.toHaveBeenCalled();
    expect(within(form).queryByRole('status')).not.toBeInTheDocument();
  });

  test('a whole number in range is sent, and a refusal by the service is still shown', async () => {
    vitest
      .mocked(api.setLateChangeThreshold)
      .mockReset()
      .mockRejectedValue(new ApiError(400, 'INVALID_LATE_CHANGE_THRESHOLD', 'Ngưỡng đổi gấp phải là số giờ nguyên lớn hơn 0.'));
    renderAs('CLUB_MANAGER', <SettingsPage />);
    const form = await thresholdForm();
    const input = within(form).getByRole('spinbutton');
    await userEvent.clear(input);
    await userEvent.type(input, '24');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu' }));
    expect(api.setLateChangeThreshold).toHaveBeenCalledWith(24);
    expect(await screen.findByText('Ngưỡng đổi gấp phải là số giờ nguyên lớn hơn 0.')).toBeInTheDocument();
  });
});
