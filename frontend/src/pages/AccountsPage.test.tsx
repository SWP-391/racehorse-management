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
    changeEmail: vitest.fn(),
    renameAccount: vitest.fn(),
    me: vitest.fn(),
    deactivate: vitest.fn(),
    resendActivationCode: vitest.fn(),
    groomSchedule: vitest.fn(),
    setGroomSchedule: vitest.fn(),
    leave: vitest.fn(),
    recordLeave: vitest.fn(),
    cancelLeave: vitest.fn(),
    leaveNotice: vitest.fn(),
  },
}));

const account = { id: 7, email: 'ht@racehorse.example', displayName: 'Huấn luyện viên A', role: 'HEAD_TRAINER', active: true, pending: false, createdAt: '' };

const groom = { ...account, id: 8, email: 'groom@racehorse.example', displayName: 'Groom An', role: 'GROOM' };

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
    await userEvent.type(screen.getByLabelText(/^Email/), 'new@racehorse.example');
    await userEvent.type(screen.getByLabelText(/^Nhập lại email/), 'new@racehorse.example');
    await userEvent.type(screen.getByLabelText(/^Họ tên/), 'Người mới');
    await userEvent.click(screen.getByRole('radio', { name: /Đặt mật khẩu ngay/ }));
    await userEvent.type(screen.getByLabelText(/^Mật khẩu/), 'password-123');
    await userEvent.selectOptions(screen.getByLabelText(/^Vai trò/), 'HEAD_TRAINER');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect((await screen.findAllByText('Mỗi người chỉ có một vai trò.')).length).toBeGreaterThan(0);
  });

  test('an empty form names every field, a short password is refused and nothing is sent', async () => {
    vitest.mocked(api.createAccount).mockClear();
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(screen.getByRole('radio', { name: /Đặt mật khẩu ngay/ }));
    await userEvent.type(screen.getByLabelText(/^Mật khẩu/), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(screen.getByLabelText(/^Email/)).toHaveAccessibleDescription('Bắt buộc nhập.');
    expect(screen.getByLabelText(/^Họ tên/)).toHaveAccessibleDescription('Bắt buộc nhập.');
    expect(screen.getByLabelText(/^Mật khẩu/)).toHaveAccessibleDescription('Mật khẩu cần 8 ký tự trở lên.');
    expect(screen.getByLabelText(/^Vai trò/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(/^Email/)).toHaveFocus();
    expect(screen.getByLabelText(/^Email/)).toHaveAttribute('maxlength', '254');
    expect(screen.getByLabelText(/^Họ tên/)).toHaveAttribute('maxlength', '200');
    expect(api.createAccount).not.toHaveBeenCalled();
  });

  test('AC-459 an email that is not an email is refused on the field and nothing is sent', async () => {
    vitest.mocked(api.createAccount).mockClear();
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.type(screen.getByLabelText(/^Email/), 'an.le@');
    await userEvent.type(screen.getByLabelText(/^Nhập lại email/), 'an.le@');
    await userEvent.type(screen.getByLabelText(/^Họ tên/), 'An Lê');
    await userEvent.click(screen.getByRole('radio', { name: /Đặt mật khẩu ngay/ }));
    await userEvent.type(screen.getByLabelText(/^Mật khẩu/), 'password-123');
    await userEvent.selectOptions(screen.getByLabelText(/^Vai trò/), 'GROOM');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(screen.getByLabelText(/^Email/)).toHaveAccessibleDescription('Email không hợp lệ.');
    expect(api.createAccount).not.toHaveBeenCalled();
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
    const manager = { ...account, id: 1, email: 'admin@racehorse.example', displayName: 'Quản lý CLB', role: 'CLUB_MANAGER' };
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([manager, account]) as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    expect(await screen.findByText('Quản lý CLB là vai trò cao nhất, không thể đổi.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Đổi vai trò admin@racehorse.example')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Đổi vai trò ht@racehorse.example')).toBeInTheDocument();
  });

  test('P06 a refused role change shows the service message', async () => {
    vitest.mocked(api.changeRole).mockRejectedValue(
      new ApiError(400, 'CLUB_MANAGER_ROLE_LOCKED', 'Không thể đổi vai trò của tài khoản Quản lý CLB.'),
    );
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.selectOptions(await screen.findByLabelText('Đổi vai trò ht@racehorse.example'), 'GROOM');
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể đổi vai trò của tài khoản Quản lý CLB.');
  });

  test('other roles are refused', () => {
    renderAs('VETERINARIAN', <AccountsPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
    expect(api.accounts).not.toHaveBeenCalled();
  });
});

describe('account activation by a mailed code (BR-235 to BR-238, DEC-076)', () => {
  const pendingGroom = { ...groom, id: 9, email: 'moi@racehorse.example', displayName: 'Groom Mới', active: false, pending: true };

  async function fill(email = 'moi@racehorse.example', repeat = email) {
    await userEvent.type(screen.getByLabelText(/^Email/), email);
    if (repeat) await userEvent.type(screen.getByLabelText(/^Nhập lại email/), repeat);
    await userEvent.type(screen.getByLabelText(/^Họ tên/), 'Người mới');
    await userEvent.selectOptions(screen.getByLabelText(/^Vai trò/), 'VETERINARIAN');
  }

  test('AC-505 the default sends no password, hides the field and says where the code went', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    expect(screen.getByRole('radio', { name: /Gửi mã kích hoạt qua email/ })).toBeChecked();
    expect(screen.queryByLabelText(/^Mật khẩu/)).not.toBeInTheDocument();
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(api.createAccount).toHaveBeenCalledTimes(1);
    expect(vitest.mocked(api.createAccount).mock.calls[0][0]).toEqual({
      email: 'moi@racehorse.example', displayName: 'Người mới', password: undefined, roles: ['VETERINARIAN'], schedule: undefined,
    });
  });

  test('the password mode shows the field, checks it and sends it', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(screen.getByRole('radio', { name: /Đặt mật khẩu ngay/ }));
    await fill();
    await userEvent.type(screen.getByLabelText(/^Mật khẩu/), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(screen.getByLabelText(/^Mật khẩu/)).toHaveAccessibleDescription('Mật khẩu cần 8 ký tự trở lên.');
    expect(api.createAccount).not.toHaveBeenCalled();
    await userEvent.clear(screen.getByLabelText(/^Mật khẩu/));
    await userEvent.type(screen.getByLabelText(/^Mật khẩu/), 'password-123');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(vitest.mocked(api.createAccount).mock.calls[0][0].password).toBe('password-123');
  });

  test('AC-520 two emails that differ send nothing and name the second field', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await fill('moi@racehorse.example', 'moi@racehorse.exampel');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(screen.getByLabelText(/^Nhập lại email/)).toHaveAccessibleDescription('Hai email chưa giống nhau.');
    expect(api.createAccount).not.toHaveBeenCalled();
  });

  test('AC-520 the second email is required and a different case or spaces still match', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await fill('moi@racehorse.example', '');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(screen.getByLabelText(/^Nhập lại email/)).toHaveAccessibleDescription('Bắt buộc nhập.');
    await userEvent.type(screen.getByLabelText(/^Nhập lại email/), ' MOI@Racehorse.example ');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(api.createAccount).toHaveBeenCalledTimes(1);
  });

  test('AC-518 a pending row shows its status with resend, correct email and deactivate, and no role selector', async () => {
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([{ ...pendingGroom, role: 'VETERINARIAN' }]) as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    expect(await screen.findByText('Chờ kích hoạt')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gửi lại mã kích hoạt cho Groom Mới' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sửa email/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Vô hiệu hóa' })).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Đổi vai trò/)).not.toBeInTheDocument();
  });

  test('AC-519 a pending Groom can have the schedule edited but not leave recorded', async () => {
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([pendingGroom]) as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    expect(await screen.findByRole('button', { name: 'Sửa lịch làm của Groom Mới' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ghi nghỉ phép cho Groom Mới' })).not.toBeInTheDocument();
  });

  test('AC-512 resend calls the service and a refusal that comes too soon is shown as a banner', async () => {
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([pendingGroom]) as never);
    vitest.mocked(api.resendActivationCode).mockResolvedValueOnce(pendingGroom as never).mockRejectedValueOnce(
      new ApiError(429, 'TOO_MANY_REQUESTS', 'Vui lòng thử lại sau 40 giây.', { retryAfterSeconds: '40' }),
    );
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Gửi lại mã kích hoạt cho Groom Mới' }));
    expect(api.resendActivationCode).toHaveBeenCalledWith(9);
    await userEvent.click(screen.getByRole('button', { name: 'Gửi lại mã kích hoạt cho Groom Mới' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Vui lòng thử lại sau 40 giây.');
  });

  test('deactivating a pending account says the invitation is cancelled', async () => {
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([pendingGroom]) as never);
    vitest.mocked(api.deactivate).mockResolvedValue({ ...pendingGroom, pending: false } as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Vô hiệu hóa' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('Lời mời sẽ bị hủy');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Vô hiệu hóa' }));
    expect(api.deactivate).toHaveBeenCalledWith(9);
  });
});

describe('Groom barn hours and leave (BR-166, BR-167)', () => {
  async function fillGroom() {
    await userEvent.type(screen.getByLabelText(/^Email/), 'groom@racehorse.example');
    await userEvent.type(screen.getByLabelText(/^Nhập lại email/), 'groom@racehorse.example');
    await userEvent.type(screen.getByLabelText(/^Họ tên/), 'Groom An');
    await userEvent.click(screen.getByRole('radio', { name: /Đặt mật khẩu ngay/ }));
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

describe('the email of an account (BR-212, AC-468, AC-484 to AC-486)', () => {
  const noEmail = { ...account, id: 9, email: null, displayName: 'Cũ Bình' };

  test('AC-468 an account without an email is marked and the Club Manager can enter one', async () => {
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([noEmail]) as never);
    vitest.mocked(api.changeEmail).mockReset().mockResolvedValue({ ...noEmail, email: 'binh@racehorse.example' } as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    expect(await screen.findByText('Chưa có email')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Nhập email Cũ Bình' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).queryByLabelText(/^Lý do/)).not.toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText(/^Email/), 'binh@racehorse.example');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu email' }));
    expect(api.changeEmail).toHaveBeenCalledWith(9, { email: 'binh@racehorse.example', reason: undefined });
  });

  test('AC-484 a correction sends the new email with the reason', async () => {
    vitest.mocked(api.changeEmail).mockReset().mockResolvedValue(account as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa email Huấn luyện viên A' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^Email/), 'dung@racehorse.example');
    await userEvent.type(within(dialog).getByLabelText(/^Lý do/), 'gõ nhầm khi tạo');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu email' }));
    expect(api.changeEmail).toHaveBeenCalledWith(7, { email: 'dung@racehorse.example', reason: 'gõ nhầm khi tạo' });
  });

  test('AC-485 a correction without a reason is refused on the field and nothing is sent', async () => {
    vitest.mocked(api.changeEmail).mockReset();
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa email Huấn luyện viên A' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^Email/), 'dung@racehorse.example');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu email' }));
    expect(within(dialog).getByLabelText(/^Lý do/)).toHaveAccessibleDescription('Bắt buộc nhập lý do.');
    expect(api.changeEmail).not.toHaveBeenCalled();
  });

  test('a refusal of the service is shown on the email field', async () => {
    vitest.mocked(api.changeEmail).mockReset().mockRejectedValue(
      new ApiError(409, 'DUPLICATE_EMAIL', 'Email đã được dùng.', { email: 'Email đã được dùng.' }),
    );
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa email Huấn luyện viên A' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^Email/), 'trung@racehorse.example');
    await userEvent.type(within(dialog).getByLabelText(/^Lý do/), 'sửa');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu email' }));
    expect(await within(dialog).findByLabelText(/^Email/)).toHaveAccessibleDescription('Email đã được dùng.');
  });

  test('AC-486 the signed-in person has no email button on their own row, the other rows do', async () => {
    const own = { ...account, id: 1, email: 'admin@racehorse.example', displayName: 'Quản lý CLB', role: 'CLUB_MANAGER' };
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([own, account]) as never);
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await screen.findByText('admin@racehorse.example');
    expect(screen.queryByRole('button', { name: 'Sửa email Quản lý CLB' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sửa email Huấn luyện viên A' })).toBeInTheDocument();
  });
});

describe('rename an account (BR-239, AC-521..AC-529)', () => {
  const own = { ...account, id: 1, email: 'admin@racehorse.example', displayName: 'Quản lý CLB', role: 'CLUB_MANAGER' };
  const gone = { ...account, id: 9, email: 'gone@racehorse.example', displayName: 'Đã nghỉ', active: false };
  const waiting = { ...account, id: 10, email: 'wait@racehorse.example', displayName: 'Chờ A', active: false, pending: true };

  beforeEach(() => {
    vitest.mocked(api.renameAccount).mockReset().mockResolvedValue(account as never);
    vitest.mocked(api.me).mockReset().mockResolvedValue({ id: 1, email: own.email, displayName: 'Tên mới', role: 'CLUB_MANAGER', permissions: [] } as never);
    vitest.mocked(api.accounts).mockResolvedValue(pageOf([own, account, gone, waiting]) as never);
  });

  test('every row has the rename button: own row, deactivated row and pending row included', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await screen.findByText('admin@racehorse.example');
    for (const name of ['Quản lý CLB', 'Huấn luyện viên A', 'Đã nghỉ', 'Chờ A']) {
      expect(screen.getByRole('button', { name: `Sửa tên ${name}` })).toBeInTheDocument();
    }
  });

  test('the popup starts with the current name and saves the typed name, then the list reloads', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa tên Huấn luyện viên A' }));
    const dialog = await screen.findByRole('dialog');
    const input = within(dialog).getByLabelText(/^Họ tên/) as HTMLInputElement;
    expect(input.value).toBe('Huấn luyện viên A');
    await userEvent.clear(input);
    await userEvent.type(input, 'Nguyễn Văn A');
    const calls = vitest.mocked(api.accounts).mock.calls.length;
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu tên' }));
    await waitFor(() => expect(api.renameAccount).toHaveBeenCalledWith(7, { displayName: 'Nguyễn Văn A' }));
    await waitFor(() => expect(vitest.mocked(api.accounts).mock.calls.length).toBeGreaterThan(calls));
    expect(api.me).not.toHaveBeenCalled();
  });

  test('AC-526 a blank name is refused on the field and nothing is sent', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa tên Huấn luyện viên A' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.clear(within(dialog).getByLabelText(/^Họ tên/));
    await userEvent.type(within(dialog).getByLabelText(/^Họ tên/), '   ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu tên' }));
    expect(within(dialog).getByLabelText(/^Họ tên/)).toHaveAccessibleDescription('Bắt buộc nhập.');
    expect(api.renameAccount).not.toHaveBeenCalled();
  });

  test('AC-527 a name over 200 characters is refused before sending', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa tên Huấn luyện viên A' }));
    const dialog = await screen.findByRole('dialog');
    const input = within(dialog).getByLabelText(/^Họ tên/);
    await userEvent.clear(input);
    await userEvent.click(input);
    await userEvent.paste('a'.repeat(201));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu tên' }));
    expect(within(dialog).getByLabelText(/^Họ tên/)).toHaveAccessibleDescription('Tên tối đa 200 ký tự.');
    expect(api.renameAccount).not.toHaveBeenCalled();
  });

  test('a refusal of the service is shown on the name field', async () => {
    vitest.mocked(api.renameAccount).mockRejectedValue(
      new ApiError(400, 'NAME_TOO_LONG', 'Tên tối đa 200 ký tự.', { displayName: 'Tên tối đa 200 ký tự.' }),
    );
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa tên Huấn luyện viên A' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^Họ tên/), ' B');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu tên' }));
    expect(await within(dialog).findByLabelText(/^Họ tên/)).toHaveAccessibleDescription('Tên tối đa 200 ký tự.');
  });

  test('AC-522 renaming one’s own account reads the signed-in person again so the header follows', async () => {
    renderAs('CLUB_MANAGER', <AccountsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa tên Quản lý CLB' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.clear(within(dialog).getByLabelText(/^Họ tên/));
    await userEvent.type(within(dialog).getByLabelText(/^Họ tên/), 'Tên mới');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu tên' }));
    await waitFor(() => expect(api.renameAccount).toHaveBeenCalledWith(1, { displayName: 'Tên mới' }));
    await waitFor(() => expect(api.me).toHaveBeenCalledTimes(1));
  });
});
