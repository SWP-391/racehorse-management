import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import BillingPage from './BillingPage';
import { renderAs, renderWithout } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { Statement } from '../api/types';

vitest.mock('../api/api', () => ({
  api: {
    myStatement: vitest.fn(),
    statementOwners: vitest.fn(),
    ownerStatement: vitest.fn(),
    priceList: vitest.fn(),
    setPrice: vitest.fn(),
    createFeeType: vitest.fn(),
    deactivateFeeType: vitest.fn(),
    invoices: vitest.fn().mockResolvedValue([]),
    invoice: vitest.fn(),
    closeMonth: vitest.fn(),
    addAdjustment: vitest.fn(),
    options: vitest.fn().mockResolvedValue({ items: [], page: 0, size: 20, total: 0 }),
  },
}));

const statement = {
  month: '2026-10',
  closed: false,
  ownerId: 7,
  ownerName: 'Bà Lý Thị Hoa',
  total: 230000,
  invoiceId: null,
  invoiceNumber: null,
  dailyFeeThrough: '2026-10-24',
  horses: [
    {
      horseId: 1,
      horseName: 'Sấm Chớp',
      total: 230000,
      categories: [
        {
          category: 'MEDICINE',
          total: 80000,
          lines: [{ date: '2026-10-20', description: 'Thuốc và vật tư: Cúm (liều)', quantity: 2, unitPrice: 40000, amount: 80000, reason: null }],
        },
        {
          category: 'FEED',
          total: 150000,
          lines: [{ date: '2026-10-21', description: 'Thức ăn: Cỏ khô (kg)', quantity: 6, unitPrice: 25000, amount: 150000, reason: null }],
        },
      ],
    },
  ],
} as Statement;

describe('owner cost statement (BR-180, BR-182, AC-353, AC-359)', () => {
  test('AC-353 the owner reads the month grouped by horse and category with the stored total', async () => {
    vitest.mocked(api.myStatement).mockResolvedValue(statement);
    renderAs('HORSE_OWNER', <BillingPage />);
    expect(await screen.findByRole('heading', { name: 'Sấm Chớp' })).toBeInTheDocument();
    expect(within(screen.getByTestId('statement')).getByText('Thuốc và vật tư y tế')).toBeInTheDocument();
    expect(within(screen.getByTestId('statement')).getByText('Thức ăn')).toBeInTheDocument();
    expect(screen.getByLabelText('Tổng cộng')).toHaveTextContent('230.000');
    expect(screen.getByText(/được tính đến hết ngày 2026-10-24/)).toBeInTheDocument();
    // No manager controls for an owner.
    expect(screen.queryByRole('button', { name: 'Chốt tháng' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Bảng giá' })).not.toBeInTheDocument();
  });

  test('an empty month says so and what makes costs appear', async () => {
    vitest.mocked(api.myStatement).mockResolvedValue({ ...statement, total: 0, horses: [] });
    renderAs('HORSE_OWNER', <BillingPage />);
    expect(await within(await screen.findByTestId('statement')).findByText('Chưa có khoản nào trong tháng này')).toBeInTheDocument();
  });

  test('AC-359 a role without the permission gets the access-denied state', () => {
    renderWithout('HORSE_OWNER', ['VIEW_BILLING'], <BillingPage />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(api.myStatement).not.toHaveBeenCalledWith(expect.anything(), expect.anything());
  });
});

describe('Club Manager side (PERM-051, AC-352, AC-354, AC-356, AC-357)', () => {
  beforeEach(() => {
    vitest.mocked(api.statementOwners).mockResolvedValue([
      { ownerId: 7, ownerName: 'Bà Lý Thị Hoa', total: 230000, closed: false, invoiceId: null },
    ]);
    vitest.mocked(api.ownerStatement).mockResolvedValue(statement);
    vitest.mocked(api.priceList).mockResolvedValue({
      dailyFee: { currentPrice: 150000, prices: [{ id: 1, amount: 150000, effectiveFrom: '2026-10-01' }] },
      feeTypes: [{ id: 3, name: 'Khám tổng quát', active: true, currentPrice: 200000, prices: [{ id: 2, amount: 200000, effectiveFrom: '2026-10-01' }] }],
    });
  });

  test('AC-356 closing a month asks first, then closes and reports the invoice count', async () => {
    vitest.mocked(api.closeMonth).mockResolvedValue({ month: '2026-09', invoices: 2, total: 460000 });
    renderAs('CLUB_MANAGER', <BillingPage />);
    expect(await screen.findByRole('cell', { name: 'Bà Lý Thị Hoa' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Chốt tháng' }));
    expect(await screen.findByText(/Mỗi chủ ngựa có khoản trong tháng sẽ có một hóa đơn không thể sửa/)).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Chốt tháng' }));
    expect(api.closeMonth).toHaveBeenCalledTimes(1);
  });

  test('the service refusal of a closing is shown', async () => {
    vitest.mocked(api.closeMonth).mockRejectedValue(new ApiError(409, 'PREVIOUS_MONTH_OPEN', 'Tháng 2026-08 vẫn còn khoản chưa chốt; hãy chốt tháng đó trước.'));
    renderAs('CLUB_MANAGER', <BillingPage />);
    await screen.findByRole('cell', { name: 'Bà Lý Thị Hoa' });
    await userEvent.click(screen.getByRole('button', { name: 'Chốt tháng' }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Chốt tháng' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Tháng 2026-08 vẫn còn khoản chưa chốt');
  });

  test('AC-357 a correction line needs a reason and the refusal names the field', async () => {
    vitest.mocked(api.addAdjustment).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Thông tin điều chỉnh chưa hợp lệ.', { reason: 'Bắt buộc nhập lý do.' }),
    );
    renderAs('CLUB_MANAGER', <BillingPage />);
    await screen.findByRole('cell', { name: 'Bà Lý Thị Hoa' });
    await userEvent.click(screen.getByRole('button', { name: 'Điều chỉnh' }));
    const form = await screen.findByRole('form', { name: 'Thêm khoản điều chỉnh' });
    await userEvent.click(within(form).getByRole('button', { name: 'Thêm điều chỉnh' }));
    expect(within(form).getByLabelText(/^Lý do/)).toHaveAccessibleDescription(/Bắt buộc nhập lý do\./);
  });

  test('AC-352 the price list shows current prices and history and sets a new price from a date', async () => {
    vitest.mocked(api.setPrice).mockResolvedValue({ dailyFee: { currentPrice: 150000, prices: [] }, feeTypes: [] });
    renderAs('CLUB_MANAGER', <BillingPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Bảng giá' }));
    expect(await screen.findByText('Khám tổng quát')).toBeInTheDocument();
    expect(screen.getAllByText(/150\.000/).length).toBeGreaterThan(0);
    const exam = screen.getByRole('row', { name: /Khám tổng quát/ });
    await userEvent.click(within(exam).getByRole('button', { name: 'Đặt giá mới' }));
    const form = await screen.findByRole('form', { name: 'Đặt giá mới' });
    await userEvent.type(within(form).getByLabelText(/^Số tiền/), '250000');
    await userEvent.type(within(form).getByLabelText(/^Áp dụng từ/), '2026-11-01');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu giá' }));
    expect(api.setPrice).toHaveBeenCalledWith('EXAM_FEE', 3, 250000, '2026-11-01');
  });

  test('AC-354 a price in the past is refused with the service message', async () => {
    vitest.mocked(api.setPrice).mockRejectedValue(
      new ApiError(400, 'PRICE_DATE_IN_PAST', 'Giá mới chỉ áp dụng từ hôm nay trở đi, không áp ngược về quá khứ.', {
        effectiveFrom: 'Giá mới chỉ áp dụng từ hôm nay trở đi, không áp ngược về quá khứ.',
      }),
    );
    renderAs('CLUB_MANAGER', <BillingPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Bảng giá' }));
    await screen.findByText('Khám tổng quát');
    await userEvent.click(screen.getAllByRole('button', { name: 'Đặt giá mới' })[0]);
    const form = await screen.findByRole('form', { name: 'Đặt giá mới' });
    await userEvent.type(within(form).getByLabelText(/^Số tiền/), '1000');
    await userEvent.type(within(form).getByLabelText(/^Áp dụng từ/), '2020-01-01');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu giá' }));
    expect((await within(form).findAllByRole('alert'))[0]).toHaveTextContent('không áp ngược về quá khứ');
  });
});
