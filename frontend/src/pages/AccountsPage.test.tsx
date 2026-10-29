import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import AccountsPage from './AccountsPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: {
    accounts: vitest.fn(),
    createAccount: vitest.fn(),
    changeRole: vitest.fn(),
    deactivate: vitest.fn(),
    groomSchedule: vitest.fn(),
    setGroomSchedule: vitest.fn(),
    leave: vitest.fn(),
    recordLeave: vitest.fn(),
    cancelLeave: vitest.fn(),
    leaveNotice: vitest.fn(),
  },
}));

const account = { id: 7, username: 'ht', displayName: 'Huấn luyện viên A', role: 'HEAD_TRAINER', active: true, createdAt: '' };

const groom = { ...account, id: 8, username: 'groom', displayName: 'Groom An', role: 'GROOM' };

beforeEach(() => {
  vitest.mocked(api.accounts).mockResolvedValue(pageOf([account]) as never);
  vitest.mocked(api.createAccount).mockReset().mockResolvedValue(groom as never);
  vitest.mocked(api.groomSchedule).mockReset().mockResolvedValue({ ranges: [{ weekday: 1, start: '05:00', end: '12:00' }] });
  vitest.mocked(api.setGroomSchedule).mockReset().mockResolvedValue({ ranges: [] });
  vitest.mocked(api.leave).mockReset().mockResolvedValue(pageOf([]));
  vitest.mocked(api.recordLeave).mockReset();
  vitest.mocked(api.cancelLeave).mockReset();
  vitest.mocked(api.leaveNotice).mockReset().mockResolvedValue({ days: 7 });
});

describe('account administration (task 7.2, BR-090)', () => {
  test('role is chosen with a single select, never several', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    const roleSelect = screen.getByLabelText(/^Vai trò/) as HTMLSelectElement;
    expect(roleSelect.multiple).toBe(false);
    expect(screen.getByText('Mỗi người chỉ có một vai trò.')).toBeInTheDocument();
  });

  test('AC-022 the refused second role is reported', async () => {
    vitest.mocked(api.createAccount).mockRejectedValue(
      new ApiError(400, 'ONE_ROLE_ONLY', 'Mỗi người chỉ có một vai trò.', { roles: 'Mỗi người chỉ có một vai trò.' }),
    );
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.type(screen.getByLabelText(/^Tên đăng nhập/), 'new');
    await userEvent.type(screen.getByLabelText(/^Họ tên/), 'Người mới');
    await userEvent.type(screen.getByLabelText(/^Mật khẩu/), 'password-123');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Mỗi người chỉ có một vai trò.');
  });

  test('deactivate calls the service for that account', async () => {
    vitest.mocked(api.deactivate).mockResolvedValue({ ...account, active: false } as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Vô hiệu hóa' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Vô hiệu hóa' }));
    expect(api.deactivate).toHaveBeenCalledWith(7);
  });

  test('AC-125 (DEC-031) deactivating your own account is refused with the service message', async () => {
    vitest.mocked(api.deactivate).mockRejectedValue(
      new ApiError(400, 'SELF_DEACTIVATION', 'Không thể tự vô hiệu hóa tài khoản của chính mình.'),
    );
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Vô hiệu hóa' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Vô hiệu hóa' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tự vô hiệu hóa tài khoản của chính mình.');
  });

  test('P06 a Club Manager row shows the role as a read-only badge with a hint, another row keeps its selector', async () => {
    const manager = { ...account, id: 1, username: 'admin', displayName: 'Quản lý CLB', role: 'CLUB_MANAGER' };
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([manager, account]) as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    expect(await screen.findByText('Quản lý CLB là vai trò cao nhất, không thể đổi.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Đổi vai trò admin')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Đổi vai trò ht')).toBeInTheDocument();
  });

  test('P06 a refused role change shows the service message', async () => {
    vitest.mocked(api.changeRole).mockRejectedValue(
      new ApiError(400, 'CLUB_MANAGER_ROLE_LOCKED', 'Không thể đổi vai trò của tài khoản Quản lý CLB.'),
    );
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.selectOptions(await screen.findByLabelText('Đổi vai trò ht'), 'GROOM');
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể đổi vai trò của tài khoản Quản lý CLB.');
  });

  test('other roles are refused', () => {
    renderAs('VETERINARIAN', <AccountsPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
    expect(api.accounts).not.toHaveBeenCalled();
  });
});

describe('Groom barn hours and leave (BR-166, BR-167)', () => {
  async function fillGroom() {
    await userEvent.type(screen.getByLabelText(/^Tên đăng nhập/), 'groom');
    await userEvent.type(screen.getByLabelText(/^Họ tên/), 'Groom An');
    await userEvent.type(screen.getByLabelText(/^Mật khẩu/), 'password-123');
    await userEvent.selectOptions(screen.getByLabelText(/^Vai trò/), 'GROOM');
  }

  test('AC-319 the weekly schedule editor appears only for a Groom and is sent with the new account', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    expect(screen.queryByText('Giờ làm chuồng của Groom')).not.toBeInTheDocument();
    await fillGroom();
    expect(screen.getByText('Giờ làm chuồng của Groom')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Thêm khoảng giờ cho Thứ 2' }));
    await userEvent.click(screen.getByRole('button', { name: 'Dùng giờ Thứ 2 cho Thứ 3 đến Thứ 6' }));
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(api.createAccount).toHaveBeenCalledWith(
      expect.objectContaining({
        roles: ['GROOM'],
        schedule: [1, 2, 3, 4, 5].map((weekday) => ({ weekday, start: '05:00', end: '12:00' })),
      }),
    );
  });

  test('AC-319 a Groom can be created without hours; AC-321 a range of 00:00 is sent as the end of the day', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await fillGroom();
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(vitest.mocked(api.createAccount).mock.calls[0][0].schedule).toBeUndefined();
  });

  test('AC-320 a range the service refuses shows its message under the range', async () => {
    vitest.mocked(api.createAccount).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Lịch làm chưa hợp lệ.', { 'ranges[0]': 'Thứ 2: giờ kết thúc phải sau giờ bắt đầu.' }),
    );
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await fillGroom();
    await userEvent.click(screen.getByRole('button', { name: 'Thêm khoảng giờ cho Thứ 2' }));
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(await screen.findByText('Thứ 2: giờ kết thúc phải sau giờ bắt đầu.')).toBeInTheDocument();
  });

  test('a Groom without hours is flagged, and the Club Manager edits the hours of an existing Groom', async () => {
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([{ ...groom, hasSchedule: false }]) as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    expect(await screen.findByText('Chưa có lịch làm')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sửa lịch làm của Groom An' }));
    const dialog = await screen.findByRole('dialog', { name: 'Lịch làm của Groom An' });
    expect(await within(dialog).findByLabelText('Thứ 2: giờ bắt đầu')).toHaveValue('05:00');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Xóa khoảng giờ của Thứ 2' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu lịch làm' }));
    expect(api.setGroomSchedule).toHaveBeenCalledWith(8, []);
  });

  test('AC-322, AC-323 leave shows the late label; recording and cancelling call the service', async () => {
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([{ ...groom, hasSchedule: true }]) as never);
    vitest.mocked(api.leave).mockResolvedValue(pageOf([
      { id: 1, date: '2026-11-03', late: true, noticeDays: 1, reason: null, recordedBy: 'Quản lý', recordedAt: '', cancelled: false },
      { id: 2, date: '2026-11-20', late: false, noticeDays: 17, reason: 'Việc nhà', recordedBy: 'Quản lý', recordedAt: '', cancelled: false },
    ]));
    vitest.mocked(api.recordLeave).mockResolvedValue({
      id: 3, date: '2026-12-01', late: false, noticeDays: 30, reason: null, recordedBy: 'Quản lý', recordedAt: '', cancelled: false,
    });
    vitest.mocked(api.cancelLeave).mockResolvedValue({
      id: 1, date: '2026-11-03', late: true, noticeDays: 1, reason: null, recordedBy: 'Quản lý', recordedAt: '', cancelled: true,
    });
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Ghi nghỉ phép cho Groom An' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nghỉ phép của Groom An' });
    expect(await within(dialog).findByText('Đột xuất')).toBeInTheDocument();
    expect(within(dialog).getByText('Có kế hoạch')).toBeInTheDocument();
    expect(await within(dialog).findByText(/Báo trước ít nhất 7 ngày/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Ghi nghỉ phép' }));
    expect(within(dialog).getByText('Chọn ngày nghỉ.')).toBeInTheDocument();
    expect(api.recordLeave).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText('Ngày nghỉ'), '2026-12-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Ghi nghỉ phép' }));
    expect(api.recordLeave).toHaveBeenCalledWith(8, { date: '2026-12-01', reason: undefined });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Hủy ngày nghỉ 03/11/2026' }));
    expect(api.cancelLeave).toHaveBeenCalledWith(1);
  });

  test('a long leave list pages by twenty and the next page asks the service for page 1', async () => {
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([{ ...groom, hasSchedule: true }]) as never);
    const row = (id: number) => ({ id, date: '2026-11-03', late: false, noticeDays: 9, reason: null, recordedBy: 'Quản lý', recordedAt: '', cancelled: false });
    vitest.mocked(api.leave).mockImplementation(async (_id, query) =>
      query?.page === 1 ? { items: [row(21)], page: 1, size: 20, total: 21 } : { items: [row(1)], page: 0, size: 20, total: 21 },
    );
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Ghi nghỉ phép cho Groom An' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nghỉ phép của Groom An' });
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Trang sau' }));
    await waitFor(() => expect(api.leave).toHaveBeenLastCalledWith(8, { page: 1 }));
  });

  test('a leave the service refuses shows its message', async () => {
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([{ ...groom, hasSchedule: true }]) as never);
    vitest.mocked(api.recordLeave).mockRejectedValue(new ApiError(409, 'LEAVE_EXISTS', 'Đã ghi nghỉ phép ngày 01/12/2026.', { date: 'Đã ghi nghỉ phép ngày 01/12/2026.' }));
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Ghi nghỉ phép cho Groom An' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Ngày nghỉ'), '2026-12-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Ghi nghỉ phép' }));
    expect(await within(dialog).findByText('Đã ghi nghỉ phép ngày 01/12/2026.')).toBeInTheDocument();
  });
});
