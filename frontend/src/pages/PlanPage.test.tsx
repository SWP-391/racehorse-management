import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import PlanPage from './PlanPage';
import PlanEditorPage from './PlanEditorPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { HorseHealth, Plan, Session } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: {
    plan: vitest.fn(),
    sessionsOf: vitest.fn(),
    planVersions: vitest.fn(),
    horseHealth: vitest.fn(),
    options: vitest.fn(),
    assignableGrooms: vitest.fn(),
    shiftPlan: vitest.fn(),
    keepAfterLock: vitest.fn(),
    createPlan: vitest.fn(),
    createSession: vitest.fn(),
    cancelPlan: vitest.fn(),
    cancelSession: vitest.fn(),
  },
}));

const plan: Plan = {
  id: 5, horseId: 9, title: 'Giáo án mùa thu', state: 'ACTIVE', cancelReason: null, completionSuggested: false,
  assignedTrainer: { id: 1, displayName: 'Test HEAD_TRAINER' }, canWrite: true,
  phases: [{ id: 1, ordinal: 1, name: 'Nền tảng', startDate: '2026-10-01', endDate: '2026-10-20', goal: 'Sức bền', distanceM: 1600, weeklyVolumeKm: 12, surface: 'Cỏ' }],
};
const awaiting = { id: 70, planId: 5, horseId: 9, date: '2026-10-25', type: 'GALLOP', plannedDurationMin: 60, state: 'AWAITING_REVIEW' } as unknown as Session;
const health = (newUnexamined: boolean): HorseHealth => ({
  horseId: 9, horseName: 'Sao Mai', registrationNumber: 'VN-9', status: 'UNDER_OBSERVATION', lock: null, activity: 'IDLE',
  readiness: 'UNDER_OBSERVATION', newUnexamined, history: [],
});

beforeEach(() => {
  vitest.mocked(api.sessionsOf).mockResolvedValue(pageOf([]));
  vitest.mocked(api.planVersions).mockResolvedValue(pageOf([]));
  vitest.mocked(api.horseHealth).mockResolvedValue(health(false));
  vitest.mocked(api.options).mockResolvedValue(pageOf([{ id: 3, label: 'Anh Tuấn', hint: null }]));
  vitest.mocked(api.assignableGrooms).mockResolvedValue({
    available: [{ id: 3, displayName: 'Anh Tuấn' }],
    unavailable: [],
    nextFree: null,
  });
});

describe('plan page (task 8.1, 8.2)', () => {
  test('AC-030 completion is suggested, the plan stays active', async () => {
    vitest.mocked(api.plan).mockResolvedValue({ ...plan, completionSuggested: true });
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    expect(await screen.findByRole('status')).toHaveTextContent('Bạn có muốn đánh dấu giáo án hoàn thành?');
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Đang thực hiện');
    expect(screen.getByRole('button', { name: 'Hoàn thành giáo án' })).toBeInTheDocument();
  });

  test('AC-082 shifting shows a preview before it is applied', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    vitest.mocked(api.sessionsOf).mockResolvedValue(pageOf([awaiting]));
    vitest.mocked(api.shiftPlan).mockResolvedValue([{ sessionId: 70, from: '2026-10-25', to: '2026-11-04' }]);
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    const review = await screen.findByRole('region', { name: 'Xem xét sau khi gỡ khóa' });
    expect(within(review).getByRole('button', { name: 'Giữ nguyên lịch' })).toBeInTheDocument();
    await userEvent.type(within(review).getByLabelText('Dịch lịch (ngày)'), '10');
    await userEvent.click(within(review).getByRole('button', { name: 'Xem trước' }));
    expect(api.shiftPlan).toHaveBeenCalledWith(5, 10, true);
    expect(await within(review).findByText('2026-10-25 → 2026-11-04')).toBeInTheDocument();
    await userEvent.click(within(review).getByRole('button', { name: 'Xác nhận dịch lịch' }));
    expect(api.shiftPlan).toHaveBeenLastCalledWith(5, 10, false);
  });

  async function fillSession(time: string) {
    const form = await screen.findByRole('form', { name: 'Thêm buổi tập' });
    fireEvent.change(within(form).getByLabelText('Ngày'), { target: { value: '2026-10-25' } });
    if (time) fireEvent.change(within(form).getByLabelText('Giờ bắt đầu'), { target: { value: time } });
    await userEvent.selectOptions(within(form).getByLabelText('Loại buổi tập'), 'GALLOP');
    // Only Grooms who are free for the time range are offered (BR-162): the options arrive once date and time are set.
    if (time) {
      const picker = await within(form).findByRole('combobox', { name: 'Người phụ trách' });
      await vitest.waitFor(() => expect(picker).toBeEnabled());
      await userEvent.click(picker);
      await userEvent.click(await screen.findByRole('option', { name: 'Anh Tuấn' }));
    }
    return form;
  }

  test('AC-194 the start time is sent and shown in the session list', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    vitest.mocked(api.createSession).mockResolvedValue({ ...awaiting, state: 'SCHEDULED' });
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    const form = await fillSession('07:00');
    vitest.mocked(api.sessionsOf).mockResolvedValue(pageOf([{ ...awaiting, state: 'SCHEDULED', startTime: '07:00:00' }]));
    await userEvent.click(within(form).getByRole('button', { name: 'Thêm buổi tập' }));
    expect(api.createSession).toHaveBeenCalledWith(5, expect.objectContaining({ date: '2026-10-25', startTime: '07:00', responsiblePersonId: 3 }));
    const table = screen.getAllByRole('table')[1];
    expect(await within(table).findByText('07:00')).toBeInTheDocument();
  });

  test('AC-196 a missing start time is named', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    vitest
      .mocked(api.createSession)
      .mockRejectedValue(new ApiError(400, 'VALIDATION_FAILED', 'Thông tin buổi tập chưa hợp lệ.', { startTime: 'Bắt buộc nhập giờ bắt đầu.' }));
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    const form = await fillSession('');
    await userEvent.click(within(form).getByRole('button', { name: 'Thêm buổi tập' }));
    expect(await within(form).findByText('Bắt buộc nhập giờ bắt đầu.')).toBeInTheDocument();
    expect(within(form).getByLabelText(/^Giờ bắt đầu/)).toHaveAccessibleDescription(/Bắt buộc nhập giờ bắt đầu\./);
  });

  test('AC-200 and AC-308 an overlapping Groom is refused with the reason and nothing is saved', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    const message = 'Không thể chọn Anh Tuấn làm người phụ trách: Đã có buổi tập trùng giờ: Phi ngày 25/10/2026 lúc 07:00 của ngựa "Sao Mai".';
    vitest
      .mocked(api.createSession)
      .mockRejectedValue(new ApiError(409, 'GROOM_NOT_AVAILABLE', message, { responsiblePersonId: message }));
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    const form = await fillSession('07:30');
    await userEvent.click(within(form).getByRole('button', { name: 'Thêm buổi tập' }));
    // The overlap is no longer a warning to confirm (BR-161): no dialog, the message sits with the field.
    expect(await within(form).findAllByText(/trùng giờ/)).not.toHaveLength(0);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(api.createSession).toHaveBeenCalledTimes(1);
  });

  test('AC-306 and AC-309 the picker asks the service for the time range and lists who cannot be chosen', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    vitest.mocked(api.assignableGrooms).mockResolvedValue({
      available: [{ id: 3, displayName: 'Anh Tuấn' }],
      unavailable: [
        { id: 4, displayName: 'Bình', reason: 'BARN_HOURS', detail: 'đang trong giờ làm chuồng 06:00 đến 08:00', sessionId: null },
        { id: 5, displayName: 'Cường', reason: 'OVERLAP', detail: 'Đã có buổi tập trùng giờ: Phi lúc 07:00', sessionId: 12 },
      ],
      nextFree: null,
    });
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    const form = await fillSession('07:00');
    expect(api.assignableGrooms).toHaveBeenLastCalledWith({ horseId: 9, date: '2026-10-25', startTime: '07:00', durationMin: 60, sessionId: undefined });
    const picker = within(form).getByRole('combobox', { name: 'Người phụ trách' });
    expect(picker).toHaveTextContent('Anh Tuấn');
    await userEvent.click(picker);
    await screen.findByRole('option', { name: 'Anh Tuấn' });
    expect(screen.queryByRole('option', { name: 'Bình' })).not.toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    await userEvent.click(within(form).getByText(/Không chọn được/));
    expect(within(form).getByText(/Bình: trong giờ làm chuồng/)).toBeInTheDocument();
    expect(within(form).getByText(/Cường: trùng buổi tập khác/)).toBeInTheDocument();
  });

  test('AC-310 when nobody is free the reason and the next free time are shown', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    vitest.mocked(api.assignableGrooms).mockResolvedValue({
      available: [],
      unavailable: [{ id: 4, displayName: 'Bình', reason: 'BARN_HOURS', detail: 'đang trong giờ làm chuồng 06:00 đến 08:00', sessionId: null }],
      nextFree: '08:00',
    });
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    const form = await screen.findByRole('form', { name: 'Thêm buổi tập' });
    fireEvent.change(within(form).getByLabelText('Ngày'), { target: { value: '2026-10-25' } });
    fireEvent.change(within(form).getByLabelText('Giờ bắt đầu'), { target: { value: '06:30' } });
    expect(await within(form).findByText(/Không có Groom nào rảnh/)).toHaveTextContent('Giờ rảnh gần nhất: 08:00');
  });

  test('AC-298 a draft may leave the responsible person empty', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    vitest.mocked(api.createSession).mockResolvedValue({ ...awaiting, state: 'DRAFT' });
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    const form = await fillSession('');
    fireEvent.change(within(form).getByLabelText('Giờ bắt đầu'), { target: { value: '07:00' } });
    await userEvent.click(within(form).getByRole('checkbox', { name: 'Chỉ soạn nháp' }));
    expect(within(form).getByText('Có thể chọn sau khi xếp lịch (nháp).')).toBeInTheDocument();
    await userEvent.click(within(form).getByRole('button', { name: 'Thêm buổi tập' }));
    expect(api.createSession).toHaveBeenCalledWith(5, expect.objectContaining({ draft: true, responsiblePersonId: '' }));
  });

  test('AC-290 and AC-291 another trainer reads the plan without any write control', async () => {
    vitest.mocked(api.plan).mockResolvedValue({ ...plan, canWrite: false, assignedTrainer: { id: 2, displayName: 'Trần Quốc Hoàng' } });
    vitest.mocked(api.sessionsOf).mockResolvedValue(pageOf([{ ...awaiting, state: 'SCHEDULED', canWrite: false } as Session]));
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    expect(await screen.findByText('Ngựa này do HLV Trần Quốc Hoàng phụ trách. Bạn chỉ có quyền xem.')).toBeInTheDocument();
    for (const name of ['Hủy giáo án', 'Hoàn thành giáo án', 'Hủy buổi tập', 'Thêm buổi tập']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
    expect(screen.queryByRole('form', { name: 'Thêm buổi tập' })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Xem xét sau khi gỡ khóa' })).not.toBeInTheDocument();
  });

  test('AC-029 the plan editor shows the new-horse label', async () => {
    vitest.mocked(api.horseHealth).mockResolvedValue(health(true));
    renderAs('HEAD_TRAINER', <PlanEditorPage />, '/horses/:id/plans/new', '/horses/9/plans/new');
    expect(await screen.findByText('Ngựa mới nhập đàn – chưa khám sức khỏe')).toBeInTheDocument();
  });
});

describe('cancelling asks for a reason in a dialog (verifier finding: replaces window.prompt)', () => {
  test('cancelling the plan passes the typed reason to the service', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    vitest.mocked(api.cancelPlan).mockResolvedValue({ ...plan, state: 'CANCELLED', cancelReason: 'Ngựa bán' });
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    await userEvent.click(await screen.findByRole('button', { name: 'Hủy giáo án' }));
    const dialog = await screen.findByRole('dialog', { name: 'Hủy giáo án' });
    expect(api.cancelPlan).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText('Lý do'), 'Ngựa bán');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Hủy giáo án' }));
    expect(api.cancelPlan).toHaveBeenCalledWith(5, 'Ngựa bán');
  });

  test('closing the dialog cancels nothing', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    await userEvent.click(await screen.findByRole('button', { name: 'Hủy giáo án' }));
    const dialog = await screen.findByRole('dialog', { name: 'Hủy giáo án' });
    // Two buttons close the dialog: the corner cross and the footer button.
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Đóng' }).at(-1)!);
    expect(screen.queryByRole('dialog', { name: 'Hủy giáo án' })).not.toBeInTheDocument();
    expect(api.cancelPlan).not.toHaveBeenCalled();
  });

  test('cancelling a session passes its id and the reason', async () => {
    vitest.mocked(api.plan).mockResolvedValue(plan);
    vitest.mocked(api.sessionsOf).mockResolvedValue(pageOf([{ ...awaiting, state: 'SCHEDULED' } as Session]));
    vitest.mocked(api.cancelSession).mockResolvedValue({ ...awaiting, state: 'CANCELLED' } as Session);
    renderAs('HEAD_TRAINER', <PlanPage />, '/plans/:id', '/plans/5');
    await userEvent.click(await screen.findByRole('button', { name: 'Hủy buổi tập' }));
    const dialog = await screen.findByRole('dialog', { name: 'Hủy buổi tập' });
    await userEvent.type(within(dialog).getByLabelText('Lý do'), 'Trời mưa');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Hủy buổi tập' }));
    expect(api.cancelSession).toHaveBeenCalledWith(70, 'Trời mưa');
  });
});

