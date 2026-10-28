import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import HealthPanel from './HealthPanel';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { HorseHealth } from '../api/types';

vitest.mock('../api/api', () => ({
  api: { horseHealth: vitest.fn(), changeStatus: vitest.fn(), lock: vitest.fn(), lift: vitest.fn(), requestReview: vitest.fn() },
}));

const eligible: HorseHealth = {
  horseId: 9, horseName: 'Sao Mai', registrationNumber: 'VN-9', status: 'ELIGIBLE', lock: null, activity: 'IDLE',
  readiness: 'READY', newUnexamined: false, history: [],
};
const injuredLocked: HorseHealth = {
  ...eligible, status: 'INJURED', readiness: 'NOT_READY',
  lock: { id: 1, kind: 'AUTOMATIC', reason: 'Chấn thương: sưng', placedBy: 'BS A', placedAt: '', reviewDate: null, reviewNotes: [] },
};

describe('health panel (task 5.1)', () => {
  test('AC-071 an invalid transition is reported', async () => {
    vitest.mocked(api.horseHealth).mockResolvedValue(injuredLocked);
    vitest.mocked(api.changeStatus).mockRejectedValue(
      new ApiError(409, 'INVALID_TRANSITION', 'Không thể chuyển từ Chấn thương sang Đủ điều kiện. Ngựa phải qua Cần theo dõi trước khi về Đủ điều kiện.'),
    );
    renderAs('VETERINARIAN', <HealthPanel horseId={9} archived={false} />);
    await userEvent.selectOptions(await screen.findByLabelText('Trạng thái mới'), 'ELIGIBLE');
    await userEvent.type(screen.getAllByLabelText('Lý do')[0], 'khỏi');
    await userEvent.click(screen.getByRole('button', { name: 'Đổi trạng thái' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('phải qua Cần theo dõi');
  });

  test('AC-072 a status change without reason is reported', async () => {
    vitest.mocked(api.horseHealth).mockResolvedValue(eligible);
    vitest.mocked(api.changeStatus).mockRejectedValue(new ApiError(400, 'REASON_REQUIRED', 'Bắt buộc nhập lý do.'));
    renderAs('VETERINARIAN', <HealthPanel horseId={9} archived={false} />);
    await userEvent.selectOptions(await screen.findByLabelText('Trạng thái mới'), 'INJURED');
    await userEvent.click(screen.getByRole('button', { name: 'Đổi trạng thái' }));
    expect(api.changeStatus).toHaveBeenCalledWith(9, 'INJURED', '');
    expect(await screen.findByRole('alert')).toHaveTextContent('Bắt buộc nhập lý do.');
  });

  test('AC-119 locking an eligible horse is reported', async () => {
    vitest.mocked(api.horseHealth).mockResolvedValue(eligible);
    vitest.mocked(api.lock).mockRejectedValue(
      new ApiError(409, 'LOCK_REQUIRES_OBSERVATION', 'Không thể khóa ngựa Đủ điều kiện. Hãy chuyển ngựa sang Cần theo dõi trước.'),
    );
    renderAs('VETERINARIAN', <HealthPanel horseId={9} archived={false} />);
    await screen.findByText('Đủ điều kiện');
    await userEvent.click(screen.getByRole('button', { name: 'Khóa huấn luyện' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Hãy chuyển ngựa sang Cần theo dõi trước.');
  });

  test('Veterinarian lifts with a return level', async () => {
    vitest.mocked(api.horseHealth).mockResolvedValue({ ...injuredLocked, status: 'UNDER_OBSERVATION' });
    vitest.mocked(api.lift).mockResolvedValue({ ...eligible, status: 'UNDER_OBSERVATION', readiness: 'UNDER_OBSERVATION' });
    renderAs('VETERINARIAN', <HealthPanel horseId={9} archived={false} />);
    await userEvent.selectOptions(await screen.findByLabelText('Mức độ quay lại'), 'RESUME_WITH_ADJUSTMENT');
    await userEvent.click(screen.getByRole('button', { name: 'Gỡ khóa' }));
    expect(api.lift).toHaveBeenCalledWith(9, 'RESUME_WITH_ADJUSTMENT');
  });

  test('AC-075 and AC-088 the Head Trainer has no status, lock or lift controls, only a review request', async () => {
    vitest.mocked(api.horseHealth).mockResolvedValue(injuredLocked);
    renderAs('HEAD_TRAINER', <HealthPanel horseId={9} archived={false} />);
    await screen.findByText('Chấn thương');
    expect(screen.queryByRole('button', { name: 'Đổi trạng thái' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gỡ khóa' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Khóa huấn luyện' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đề nghị xem xét khóa' })).toBeInTheDocument();
  });

  test('a new, unexamined horse shows the intake label', async () => {
    vitest.mocked(api.horseHealth).mockResolvedValue({
      ...eligible, status: 'UNDER_OBSERVATION', newUnexamined: true, readiness: 'NOT_READY',
      lock: { id: 2, kind: 'INTAKE', reason: 'Chờ khám nhập đàn', placedBy: 'Hệ thống', placedAt: '', reviewDate: null, reviewNotes: [] },
    });
    renderAs('HEAD_TRAINER', <HealthPanel horseId={9} archived={false} />);
    expect(await screen.findByText('Ngựa mới nhập đàn – chưa khám sức khỏe')).toBeInTheDocument();
    expect(screen.getByText(/Chờ khám nhập đàn/)).toBeInTheDocument();
  });

  test('a reason being typed is not erased when another action completes (regression)', async () => {
    const locked: HorseHealth = {
      ...eligible, status: 'UNDER_OBSERVATION', readiness: 'NOT_READY',
      lock: { id: 2, kind: 'INTAKE', reason: 'Chờ khám nhập đàn', placedBy: 'Hệ thống', placedAt: '', reviewDate: null, reviewNotes: [] },
    };
    vitest.mocked(api.horseHealth).mockResolvedValue(locked);
    let finishLift: (h: HorseHealth) => void = () => undefined;
    vitest.mocked(api.lift).mockReturnValue(new Promise((resolve) => (finishLift = resolve)));
    renderAs('VETERINARIAN', <HealthPanel horseId={9} archived={false} />);
    await userEvent.selectOptions(await screen.findByLabelText('Mức độ quay lại'), 'RESUME_AS_IS');
    await userEvent.click(screen.getByRole('button', { name: 'Gỡ khóa' }));
    await userEvent.type(screen.getAllByLabelText('Lý do')[0], 'Khám nhập đàn đạt yêu cầu');
    finishLift({ ...locked, lock: null, readiness: 'UNDER_OBSERVATION' });
    expect(await screen.findByText('Không khóa')).toBeInTheDocument();
    expect(screen.getAllByLabelText('Lý do')[0]).toHaveValue('Khám nhập đàn đạt yêu cầu');
  });
});
