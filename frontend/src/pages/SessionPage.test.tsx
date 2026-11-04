import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import SessionPage from './SessionPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { Session } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: {
    session: vitest.fn(),
    startSession: vitest.fn(),
    endSession: vitest.fn(),
    completeIndicators: vitest.fn(),
    options: vitest.fn(),
    assignSession: vitest.fn(),
    live: vitest.fn().mockResolvedValue({ active: false, recent: [], alerts: [] }),
  },
}));

const base = {
  id: 7, planId: 5, horseId: 9, date: '2026-10-21', type: 'GALLOP', plannedDurationMin: 60, targetDistanceM: null,
  targetSurface: null, responsiblePerson: null, state: 'SCHEDULED', startedAt: null, endedAt: null, endTimeAutomatic: false,
  overtime: false, lockDuringSession: false, missingIndicators: false, earlyEndReason: null, cancelReason: null,
  distanceM: null, durationMin: null, surface: null, intensity: null, form: null, avgHeartRate: null,
  avgHeartRateSource: null, maxSpeed: null, maxSpeedSource: null, weightKg: null, comment: null, assignmentAck: null,
  needsResponsible: false, declinedBy: null, declineReason: null, declinedAt: null,
  assignedTrainer: { id: 1, displayName: 'Test HEAD_TRAINER' }, canWrite: true,
} as Session;

/** The Grooms the service offers (BR-186, BR-134); the picker is a search select, so a choice is made by opening it. */
const grooms = (...people: { id: number; displayName: string }[]) =>
  vitest.mocked(api.options).mockResolvedValue(pageOf(people.map((p) => ({ id: p.id, label: p.displayName, hint: null }))));

async function chooseGroom(form: HTMLElement, name: string) {
  await userEvent.click(within(form).getByRole('combobox'));
  await userEvent.click(await screen.findByRole('option', { name }));
}

describe('session page (task 8.2, 8.3)', () => {
  test('AC-040 the Cần theo dõi warning is confirmed before starting', async () => {
    const withGroom = { ...base, responsiblePerson: { id: 21, displayName: 'Groom G' }, assignmentAck: 'ACKNOWLEDGED' } as Session;
    vitest.mocked(api.session).mockResolvedValue({ session: withGroom, lockPendingAtEnd: false });
    vitest
      .mocked(api.startSession)
      .mockRejectedValueOnce(new ApiError(409, 'WARNING_CONFIRMATION_REQUIRED', 'Ngựa đang Cần theo dõi. Xác nhận để bắt đầu buổi tập.'))
      .mockResolvedValueOnce({ ...withGroom, state: 'IN_PROGRESS' });
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    await userEvent.click(await screen.findByRole('button', { name: 'Bắt đầu' }));
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('Ngựa đang Cần theo dõi');
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận và bắt đầu' }));
    expect(api.startSession).toHaveBeenLastCalledWith(7, true);
    expect(await screen.findByText('Đang tập')).toBeInTheDocument();
  });

  test('AC-043 a missing form rating is named and nothing is sent', async () => {
    vitest.mocked(api.session).mockResolvedValue({ session: { ...base, state: 'IN_PROGRESS' }, lockPendingAtEnd: false });
    vitest.mocked(api.endSession).mockReset();
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    await userEvent.click(await screen.findByRole('button', { name: 'Kết thúc buổi tập' }));
    expect(screen.getByLabelText(/^Phong độ/)).toHaveAccessibleDescription(/Bắt buộc chấm phong độ\./);
    for (const label of [/^Cự ly/, /^Thời lượng/, /^Mặt sân/, /^Cường độ/]) {
      expect(screen.getByLabelText(label), String(label)).toHaveAttribute('aria-invalid', 'true');
    }
    expect(api.endSession).not.toHaveBeenCalled();
  });

  test('a form rating outside 1 to 5, a heart rate or weight outside the plausible range are refused on the field', async () => {
    vitest.mocked(api.session).mockResolvedValue({ session: { ...base, state: 'IN_PROGRESS' }, lockPendingAtEnd: false });
    vitest.mocked(api.endSession).mockReset();
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    await userEvent.click(await screen.findByRole('button', { name: 'Kết thúc buổi tập' }));
    fireEvent.change(screen.getByLabelText(/^Cự ly/), { target: { value: '1600' } });
    fireEvent.change(screen.getByLabelText(/^Thời lượng/), { target: { value: '45' } });
    await userEvent.type(screen.getByLabelText(/^Mặt sân/), 'Cỏ');
    await userEvent.selectOptions(screen.getByLabelText(/^Cường độ/), 'MEDIUM');
    fireEvent.change(screen.getByLabelText(/^Phong độ/), { target: { value: '6' } });
    fireEvent.change(screen.getByLabelText(/^Nhịp tim/), { target: { value: '1500' } });
    fireEvent.change(screen.getByLabelText(/^Cân nặng/), { target: { value: '-5' } });
    fireEvent.change(screen.getByLabelText(/^Vận tốc/), { target: { value: '0' } });
    await userEvent.click(screen.getByRole('button', { name: 'Kết thúc buổi tập' }));
    expect(screen.getByLabelText(/^Phong độ/)).toHaveAccessibleDescription('Giá trị tối đa là 5.');
    expect(screen.getByLabelText(/^Nhịp tim/)).toHaveAccessibleDescription('Giá trị tối đa là 300.');
    expect(screen.getByLabelText(/^Cân nặng/)).toHaveAccessibleDescription('Giá trị tối thiểu là 100.');
    expect(screen.getByLabelText(/^Vận tốc/)).toHaveAccessibleDescription('Giá trị phải lớn hơn 0.');
    expect(api.endSession).not.toHaveBeenCalled();
  });

  test('a valid set of indicators is sent as numbers and the units are carried by the fields', async () => {
    vitest.mocked(api.session).mockResolvedValue({ session: { ...base, state: 'IN_PROGRESS' }, lockPendingAtEnd: false });
    vitest.mocked(api.endSession).mockReset().mockResolvedValue({ ...base, state: 'COMPLETED' } as never);
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    await userEvent.click(await screen.findByRole('button', { name: 'Kết thúc buổi tập' }));
    expect(screen.getByLabelText(/^Cự ly/)).toHaveAttribute('data-unit', 'm');
    expect(screen.getByLabelText(/^Nhịp tim/)).toHaveAttribute('data-unit', 'bpm');
    expect(screen.getByLabelText(/^Vận tốc/)).toHaveAttribute('data-unit', 'km/h');
    expect(screen.getByLabelText(/^Cân nặng/)).toHaveAttribute('data-unit', 'kg');
    fireEvent.change(screen.getByLabelText(/^Cự ly/), { target: { value: '1600' } });
    fireEvent.change(screen.getByLabelText(/^Thời lượng/), { target: { value: '45' } });
    await userEvent.type(screen.getByLabelText(/^Mặt sân/), 'Cỏ');
    await userEvent.selectOptions(screen.getByLabelText(/^Cường độ/), 'MEDIUM');
    fireEvent.change(screen.getByLabelText(/^Phong độ/), { target: { value: '4' } });
    await userEvent.click(screen.getByRole('button', { name: 'Kết thúc buổi tập' }));
    expect(api.endSession).toHaveBeenCalledWith(7, expect.objectContaining({ distanceM: 1600, durationMin: 45, form: 4, intensity: 'MEDIUM' }));
  });

  test('the service\'s own answer is still shown on the field when the client let a value through', async () => {
    vitest.mocked(api.session).mockResolvedValue({ session: { ...base, state: 'IN_PROGRESS' }, lockPendingAtEnd: false });
    vitest.mocked(api.endSession).mockReset().mockRejectedValue(
      new ApiError(400, 'INDICATORS_INCOMPLETE', 'Thiếu hoặc sai chỉ số.', { distanceM: 'Cự ly phải lớn hơn 0.' }),
    );
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    await userEvent.click(await screen.findByRole('button', { name: 'Kết thúc buổi tập' }));
    fireEvent.change(screen.getByLabelText(/^Cự ly/), { target: { value: '1600' } });
    fireEvent.change(screen.getByLabelText(/^Thời lượng/), { target: { value: '45' } });
    await userEvent.type(screen.getByLabelText(/^Mặt sân/), 'Cỏ');
    await userEvent.selectOptions(screen.getByLabelText(/^Cường độ/), 'MEDIUM');
    fireEvent.change(screen.getByLabelText(/^Phong độ/), { target: { value: '4' } });
    await userEvent.click(screen.getByRole('button', { name: 'Kết thúc buổi tập' }));
    expect(await screen.findByText('Cự ly phải lớn hơn 0.')).toBeInTheDocument();
  });

  test('AC-089 the Head Trainer sees that the horse will be locked when the session ends', async () => {
    vitest.mocked(api.session).mockResolvedValue({
      session: { ...base, state: 'IN_PROGRESS', lockDuringSession: true },
      lockPendingAtEnd: true,
    });
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    expect(await screen.findByText('Ngựa sẽ bị khóa khi buổi tập này kết thúc.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Kết thúc buổi tập' })).toBeInTheDocument();
  });

  test('AC-188 a same-day change asks to confirm the warning; cancelling changes nothing', async () => {
    const g = { id: 21, displayName: 'Groom G' };
    const h = { id: 22, displayName: 'Groom H' };
    const assigned = { ...base, responsiblePerson: g, assignmentAck: 'UNCONFIRMED' } as Session;
    vitest.mocked(api.session).mockResolvedValue({ session: assigned, lockPendingAtEnd: false });
    grooms(g, h);
    const warning = new ApiError(409, 'WARNING_CONFIRMATION_REQUIRED', 'Đổi người phụ trách trong ngày tập: người mới có thể không kịp chuẩn bị.');
    vitest
      .mocked(api.assignSession)
      .mockRejectedValueOnce(warning)
      .mockRejectedValueOnce(warning)
      .mockResolvedValueOnce({ ...assigned, responsiblePerson: h });
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    const form = await screen.findByRole('form', { name: 'Đổi người phụ trách' });
    await chooseGroom(form, 'Groom H');

    await userEvent.click(within(form).getByRole('button', { name: 'Đổi người phụ trách' }));
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('không kịp chuẩn bị');
    await userEvent.click(screen.getByRole('button', { name: 'Không đổi' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(api.assignSession).toHaveBeenCalledTimes(1);
    expect(api.assignSession).toHaveBeenLastCalledWith(7, 22, false);
    expect(screen.getByText(/Người phụ trách: Groom G/)).toBeInTheDocument();

    await userEvent.click(within(form).getByRole('button', { name: 'Đổi người phụ trách' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Xác nhận đổi người' }));
    expect(api.assignSession).toHaveBeenLastCalledWith(7, 22, true);
    expect(await screen.findByText(/Người phụ trách: Groom H/)).toBeInTheDocument();
  });

  test('AC-238 the picker offers exactly the Grooms the server lists', async () => {
    const g = { id: 21, displayName: 'Groom G' };
    const h = { id: 22, displayName: 'Groom H' };
    vitest.mocked(api.session).mockResolvedValue({ session: { ...base, responsiblePerson: g } as Session, lockPendingAtEnd: false });
    grooms(g, h);
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    const form = await screen.findByRole('form', { name: 'Đổi người phụ trách' });
    await userEvent.click(within(form).getByRole('combobox'));
    await screen.findByRole('option', { name: 'Groom H' });
    const names = screen.getAllByRole('option').map((o) => o.textContent);
    expect(names).toEqual(['Groom G', 'Groom H']);
  });

  test('AC-239 a refused choice shows the message and keeps the responsible person', async () => {
    const g = { id: 21, displayName: 'Groom G' };
    const h = { id: 22, displayName: 'Groom H' };
    const assigned = { ...base, responsiblePerson: g, assignmentAck: 'UNCONFIRMED' } as Session;
    vitest.mocked(api.session).mockResolvedValue({ session: assigned, lockPendingAtEnd: false });
    grooms(g, h);
    vitest
      .mocked(api.assignSession)
      .mockRejectedValueOnce(new ApiError(400, 'INVALID_RESPONSIBLE', 'Người phụ trách phải là Groom đang hoạt động.'));
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    const form = await screen.findByRole('form', { name: 'Đổi người phụ trách' });
    await chooseGroom(form, 'Groom H');
    await userEvent.click(within(form).getByRole('button', { name: 'Đổi người phụ trách' }));
    expect(await screen.findByText('Người phụ trách phải là Groom đang hoạt động.')).toBeInTheDocument();
    expect(screen.getByText(/Người phụ trách: Groom G/)).toBeInTheDocument();
  });

  test('AC-240 an earlier non-Groom assignment stays shown but cannot be chosen again', async () => {
    const vet = { id: 31, displayName: 'Bác sĩ V' };
    const h = { id: 22, displayName: 'Groom H' };
    vitest.mocked(api.session).mockResolvedValue({ session: { ...base, responsiblePerson: vet } as Session, lockPendingAtEnd: false });
    grooms(h);
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    expect(await screen.findByText(/Người phụ trách: Bác sĩ V/)).toBeInTheDocument();
    // The earlier person stays shown in the picker, but only active Grooms are offered to choose (BR-134, BR-135).
    const form = await screen.findByRole('form', { name: 'Đổi người phụ trách' });
    expect(within(form).getByRole('combobox')).toHaveTextContent('Bác sĩ V');
    await userEvent.click(within(form).getByRole('combobox'));
    expect(await screen.findByRole('option', { name: 'Groom H' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /Bác sĩ V/ })).not.toBeInTheDocument();
  });

  test('AC-189 starting with an unconfirmed responsible person reminds but starts', async () => {
    const assigned = { ...base, responsiblePerson: { id: 21, displayName: 'Groom G' }, assignmentAck: 'UNCONFIRMED' } as Session;
    vitest.mocked(api.session).mockResolvedValue({ session: assigned, lockPendingAtEnd: false });
    vitest.mocked(api.startSession).mockResolvedValue({ ...assigned, state: 'IN_PROGRESS' });
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    expect(await screen.findByText(/Chưa xác nhận/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Bắt đầu' }));
    expect(api.startSession).toHaveBeenLastCalledWith(7, false);
    expect(await screen.findByText('Đang tập')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('người phụ trách chưa xác nhận');
  });
  test('AC-300 a session with nobody responsible says so and cannot be started', async () => {
    const orphan = {
      ...base,
      needsResponsible: true,
      declinedBy: { id: 21, displayName: 'Groom G' },
      declineReason: 'Bận việc gia đình',
    } as Session;
    vitest.mocked(api.session).mockResolvedValue({ session: orphan, lockPendingAtEnd: false });
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    expect(await screen.findByRole('status')).toHaveTextContent('Cần người phụ trách');
    expect(screen.getByRole('status')).toHaveTextContent('Groom G không nhận: Bận việc gia đình');
    expect(screen.getByRole('button', { name: 'Bắt đầu' })).toBeDisabled();
  });

  test('AC-290 and AC-291 another trainer reads the session read-only with the name of the assigned one', async () => {
    const theirs = {
      ...base,
      canWrite: false,
      assignedTrainer: { id: 2, displayName: 'Trần Quốc Hoàng' },
      responsiblePerson: { id: 21, displayName: 'Groom G' },
    } as Session;
    vitest.mocked(api.session).mockResolvedValue({ session: theirs, lockPendingAtEnd: false });
    renderAs('HEAD_TRAINER', <SessionPage />, '/sessions/:id', '/sessions/7');
    expect(await screen.findByText('Ngựa này do HLV Trần Quốc Hoàng phụ trách. Bạn chỉ có quyền xem.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Bắt đầu' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Đổi người phụ trách' })).not.toBeInTheDocument();
  });
});
