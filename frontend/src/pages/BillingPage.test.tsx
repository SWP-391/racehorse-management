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
    billingOverview: vitest.fn(),
    deactivateFeeType: vitest.fn(),
    invoices: vitest.fn().mockResolvedValue([]),
    invoice: vitest.fn(),
    closeMonth: vitest.fn(),
    closingStatus: vitest.fn(),
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
  closedAt: null,
  dailyFeeThrough: '2026-10-24',
  categories: [
    { category: 'MEDICINE', total: 80000 },
    { category: 'FEED', total: 150000 },
  ],
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

describe('detailed statement, export and print (BR-203, BR-204)', () => {
  const two = {
    ...statement,
    total: 260000,
    categories: [
      { category: 'MEDICINE', total: 80000 },
      { category: 'FEED', total: 180000 },
    ],
    horses: [
      ...statement.horses.map((h) => ({ ...h, total: 230000 })),
      {
        horseId: 2,
        horseName: 'Gió Bắc',
        total: 30000,
        categories: [
          { category: 'FEED', total: 30000, lines: [{ date: '2026-10-22', description: 'Thức ăn: Yến mạch (kg)', quantity: 1, unitPrice: 30000, amount: 30000, reason: null }] },
        ],
      },
    ],
  } as Statement;

  async function openDetail() {
    vitest.mocked(api.myStatement).mockResolvedValue(two);
    renderAs('HORSE_OWNER', <BillingPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Chi tiết' }));
    return screen.getByTestId('statement-detail');
  }

  test('AC-406 the detail table has one row per line and the service total', async () => {
    const detail = await openDetail();
    expect(within(detail).getAllByRole('row')).toHaveLength(1 + 3);
    expect(within(detail).getByText(/Tổng cộng:/)).toHaveTextContent('260.000');
  });

  test('AC-407 the filters narrow the rows and the subtotal is the service\'s own number', async () => {
    const detail = await openDetail();
    await userEvent.selectOptions(within(detail).getByLabelText('Nhóm'), 'FEED');
    expect(within(detail).getAllByRole('row')).toHaveLength(1 + 2);
    expect(within(detail).getByText(/Tổng theo bộ lọc:/)).toHaveTextContent('180.000');
    await userEvent.selectOptions(within(detail).getByLabelText('Ngựa'), '2');
    expect(within(detail).getAllByRole('row')).toHaveLength(1 + 1);
    expect(within(detail).getByText(/Tổng theo bộ lọc:/)).toHaveTextContent('30.000');
  });

  test('a filter with no line says so and keeps the statement total', async () => {
    const detail = await openDetail();
    await userEvent.selectOptions(within(detail).getByLabelText('Ngựa'), '2');
    await userEvent.selectOptions(within(detail).getByLabelText('Nhóm'), 'MEDICINE');
    expect(within(detail).getByText('Không có dòng nào khớp bộ lọc.')).toBeInTheDocument();
    expect(within(detail).getByText(/Tổng cộng của bảng kê: .*260\.000/)).toBeInTheDocument();
    expect(within(detail).getByText(/Tổng theo bộ lọc:/)).toHaveTextContent('0');
  });

  test('a Horse Owner can export and print their statement', async () => {
    vitest.mocked(api.myStatement).mockResolvedValue(statement);
    renderAs('HORSE_OWNER', <BillingPage />);
    expect(await screen.findByRole('button', { name: 'Xuất Excel' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'In' })).toHaveAttribute('href', expect.stringContaining('/billing/print/statement/1'));
  });

  test('the Club Manager can export all owners of the month', async () => {
    vitest.mocked(api.statementOwners).mockResolvedValue([]);
    vitest.mocked(api.closingStatus).mockResolvedValue({ month: '2026-09', state: 'READY', openMonth: null, message: null });
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    expect(await screen.findByRole('button', { name: 'Xuất Excel tất cả chủ ngựa' })).toBeInTheDocument();
  });
});

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
    vitest.mocked(api.closingStatus).mockResolvedValue({ month: '2026-09', state: 'READY', openMonth: null, message: null });
    vitest.mocked(api.priceList).mockResolvedValue({
      dailyFee: { currentPrice: 150000, prices: [{ id: 1, amount: 150000, effectiveFrom: '2026-10-01' }] },
      feeTypes: [{ id: 3, name: 'Khám tổng quát', active: true, currentPrice: 200000, prices: [{ id: 2, amount: 200000, effectiveFrom: '2026-10-01' }] }],
    });
  });

  test('AC-356 closing a month asks first, then closes and reports the invoice count', async () => {
    vitest.mocked(api.closeMonth).mockResolvedValue({ month: '2026-09', invoices: 2, total: 460000 });
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    expect(await screen.findByRole('cell', { name: 'Bà Lý Thị Hoa' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Chốt tháng' }));
    expect(await screen.findByText(/Mỗi chủ ngựa có khoản trong tháng sẽ có một hóa đơn không thể sửa/)).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Chốt tháng' }));
    expect(api.closeMonth).toHaveBeenCalledTimes(1);
    // The result stays beside the buttons until dismissed (AC-412).
    expect(await screen.findByRole('status')).toHaveTextContent('Đã chốt tháng 2026-09: 2 hóa đơn');
    await userEvent.click(screen.getByRole('button', { name: 'Đóng' }));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  test('AC-411 when the month is ready, the page says what closing will do', async () => {
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    expect(await screen.findByText(/Có thể chốt tháng .*: mỗi chủ ngựa có khoản sẽ có một hóa đơn/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Chốt tháng' })).toBeEnabled();
  });

  test.each([
    ['NOT_FINISHED', 'Tháng 2026-10 chưa kết thúc, chưa thể chốt.'],
    ['CLOSED', 'Tháng 2026-10 đã được chốt.'],
    ['PREVIOUS_MONTH_OPEN', 'Tháng 2026-08 vẫn còn khoản chưa chốt; hãy chốt tháng đó trước.'],
    ['FEES_PENDING', 'Phí chuồng của tháng 2026-10 chưa được tính đủ đến ngày cuối tháng; thử lại sau.'],
  ] as const)('AC-411 %s: the button is disabled and the reason is written next to it', async (state, message) => {
    vitest.mocked(api.closingStatus).mockResolvedValue({ month: '2026-10', state, openMonth: null, message });
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Chốt tháng' })).toBeDisabled();
  });

  test('AC-414 a month without charges is closed with a message saying no invoice was made', async () => {
    vitest.mocked(api.closeMonth).mockResolvedValue({ month: '2026-09', invoices: 0, total: 0 });
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    await screen.findByRole('cell', { name: 'Bà Lý Thị Hoa' });
    await userEvent.click(screen.getByRole('button', { name: 'Chốt tháng' }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Chốt tháng' }));
    expect(await screen.findByRole('status')).toHaveTextContent('không có hóa đơn');
  });

  test('the service refusal of a closing is shown', async () => {
    vitest.mocked(api.closeMonth).mockRejectedValue(new ApiError(409, 'PREVIOUS_MONTH_OPEN', 'Tháng 2026-08 vẫn còn khoản chưa chốt; hãy chốt tháng đó trước.'));
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    await screen.findByRole('cell', { name: 'Bà Lý Thị Hoa' });
    await userEvent.click(screen.getByRole('button', { name: 'Chốt tháng' }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Chốt tháng' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Tháng 2026-08 vẫn còn khoản chưa chốt');
  });

  test('AC-357 a correction line needs an owner, a date, a whole amount and a reason, named on each field, nothing sent', async () => {
    vitest.mocked(api.addAdjustment).mockReset();
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    await screen.findByRole('cell', { name: 'Bà Lý Thị Hoa' });
    await userEvent.click(screen.getByRole('button', { name: 'Điều chỉnh' }));
    const form = await screen.findByRole('form', { name: 'Thêm khoản điều chỉnh' });
    await userEvent.selectOptions(within(form).getByLabelText('Tính tiền theo'), 'đồng');
    await userEvent.type(within(form).getByLabelText(/^Số tiền/), '10.5');
    await userEvent.click(within(form).getByRole('button', { name: 'Thêm điều chỉnh' }));
    expect(within(form).getByLabelText(/^Lý do/)).toHaveAccessibleDescription(/Bắt buộc nhập lý do\./);
    expect(within(form).getByLabelText(/^Số tiền/)).toHaveAccessibleDescription('Hãy nhập số nguyên.');
    expect(within(form).getByLabelText(/^Số tiền/)).toHaveAttribute('data-unit', 'VND');
    expect(api.addAdjustment).not.toHaveBeenCalled();
  });

  test('a price cannot be fractional or negative (the minus is not typed) and needs a start date', async () => {
    vitest.mocked(api.setPrice).mockReset();
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    await userEvent.click(await screen.findByRole('button', { name: 'Bảng giá' }));
    const exam = await screen.findByRole('row', { name: /Khám tổng quát/ });
    await userEvent.click(within(exam).getByRole('button', { name: 'Đặt giá mới' }));
    const form = await screen.findByRole('form', { name: 'Đặt giá mới' });
    await userEvent.selectOptions(within(form).getByLabelText('Tính tiền theo'), 'đồng');
    await userEvent.type(within(form).getByLabelText(/^Số tiền/), '-5.5');
    expect(within(form).getByLabelText(/^Số tiền/)).toHaveValue('5.5');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu giá' }));
    expect(within(form).getByLabelText(/^Số tiền/)).toHaveAttribute('aria-invalid', 'true');
    expect(within(form).getByLabelText(/^Áp dụng từ/)).toHaveAccessibleDescription('Bắt buộc nhập.');
    expect(api.setPrice).not.toHaveBeenCalled();
  });

  test('AC-352 the price list shows current prices and history and sets a new price from a date', async () => {
    vitest.mocked(api.setPrice).mockResolvedValue({ dailyFee: { currentPrice: 150000, prices: [] }, feeTypes: [] });
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    await userEvent.click(await screen.findByRole('button', { name: 'Bảng giá' }));
    expect(await screen.findByText('Khám tổng quát')).toBeInTheDocument();
    expect(screen.getAllByText(/150\.000/).length).toBeGreaterThan(0);
    const exam = screen.getByRole('row', { name: /Khám tổng quát/ });
    await userEvent.click(within(exam).getByRole('button', { name: 'Đặt giá mới' }));
    const form = await screen.findByRole('form', { name: 'Đặt giá mới' });
    await userEvent.type(within(form).getByLabelText(/^Số tiền/), '250');
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
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
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

describe('billing overview, cost list, statement popup and fee types (BR-209, DEC-069, DEC-070, AC-448 to AC-454)', () => {
  const owners = [
    { ownerId: 7, ownerName: 'Bà Lý Thị Hoa', total: 230000, closed: false, invoiceId: null },
    { ownerId: 8, ownerName: 'Ông Trần Văn Bình', total: 90000, closed: false, invoiceId: null },
  ];
  const overview = (scope: string, over = {}) => ({
    scope,
    total: 320000,
    owners: 2,
    months: Array.from({ length: 12 }, (_, i) => ({ month: `2025-${String(i + 1).padStart(2, '0')}`, total: i === 11 ? 320000 : 0 })),
    categories: [{ category: 'FEED', total: 200000 }, { category: 'MEDICINE', total: 120000 }],
    topOwners: [{ ownerId: 7, ownerName: 'Bà Lý Thị Hoa', total: 230000 }, { ownerId: 8, ownerName: 'Ông Trần Văn Bình', total: 90000 }],
    ...over,
  });

  beforeEach(() => {
    vitest.mocked(api.statementOwners).mockResolvedValue(owners as never);
    vitest.mocked(api.ownerStatement).mockResolvedValue(statement);
    vitest.mocked(api.closingStatus).mockResolvedValue({ month: '2026-09', state: 'NOT_READY', openMonth: null, message: 'x' } as never);
    vitest.mocked(api.billingOverview).mockReset().mockResolvedValue(overview('2026-09') as never);
  });

  test('AC-448 the overview opens first with four cards of charts only, for a month and then for all months', async () => {
    renderAs('CLUB_MANAGER', <BillingPage />);
    expect(await screen.findByRole('table', { name: 'Chi phí theo tháng' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Cơ cấu theo loại chi phí' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Chủ ngựa chi nhiều nhất' })).toBeInTheDocument();
    expect(screen.getByText('Tổng kết')).toBeInTheDocument();
    // No list under the charts.
    expect(screen.queryByRole('table', { name: 'Chi phí' })).not.toBeInTheDocument();
    vitest.mocked(api.billingOverview).mockResolvedValue(overview('all', { total: 999000 }) as never);
    await userEvent.selectOptions(screen.getByLabelText('Tháng'), 'all');
    await vitest.waitFor(() => expect(api.billingOverview).toHaveBeenLastCalledWith('all'));
    expect(await screen.findByText(/999\.000/)).toBeInTheDocument();
  });

  test('AC-449 a month with nothing says so instead of drawing empty charts', async () => {
    vitest.mocked(api.billingOverview).mockResolvedValue(overview('2026-09', { total: 0, owners: 0, categories: [], topOwners: [] }) as never);
    renderAs('CLUB_MANAGER', <BillingPage />);
    expect((await screen.findAllByText('Chưa có chi phí nào')).length).toBe(2);
  });

  test('AC-450 the cost list has no chart and finds an owner by part of the name, with or without diacritics', async () => {
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    await screen.findByRole('cell', { name: 'Bà Lý Thị Hoa' });
    expect(screen.queryByRole('table', { name: 'Cơ cấu chi phí theo loại' })).not.toBeInTheDocument();
    await userEvent.type(screen.getByRole('searchbox'), 'tran van');
    expect(screen.queryByRole('cell', { name: 'Bà Lý Thị Hoa' })).not.toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Ông Trần Văn Bình' })).toBeInTheDocument();
  });

  test('AC-451 "Xem" opens the statement in a popup with the total in bold, and the list stays where it was', async () => {
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    await userEvent.click(await screen.findByRole('button', { name: 'Xem Bà Lý Thị Hoa' }));
    const dialog = await screen.findByRole('dialog', { name: 'Bảng kê của Bà Lý Thị Hoa' });
    const total = await within(dialog).findByLabelText('Tổng cộng');
    expect(total.className).toContain('font-bold');
    expect(total).toHaveTextContent('230.000');
    await userEvent.keyboard('{Escape}');
    expect(screen.getByRole('cell', { name: 'Ông Trần Văn Bình' })).toBeInTheDocument();
  });

  test('AC-452 and AC-453 a fee type is added with its price from a button, and refused without one', async () => {
    vitest.mocked(api.priceList).mockResolvedValue({ dailyFee: { currentPrice: 150000, prices: [] }, feeTypes: [{ id: 3, name: 'Khám tổng quát', active: true, currentPrice: 200000, prices: [] }] } as never);
    vitest.mocked(api.createFeeType).mockReset().mockResolvedValue({ id: 4, name: 'Khám da liễu' });
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=prices');
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm loại phí khám' }));
    const form = await screen.findByRole('form', { name: 'Thêm loại phí khám' });
    await userEvent.type(within(form).getByLabelText(/^Tên loại phí/), 'Khám da liễu');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu giá' }));
    expect(within(form).getByLabelText(/^Số tiền/)).toHaveAccessibleDescription('Bắt buộc nhập.');
    expect(api.createFeeType).not.toHaveBeenCalled();
    await userEvent.type(within(form).getByLabelText(/^Số tiền/), '200');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu giá' }));
    await vitest.waitFor(() => expect(api.createFeeType).toHaveBeenCalledWith('Khám da liễu', 200000, expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/)));
  });

  test('the fee types can be searched', async () => {
    vitest.mocked(api.priceList).mockResolvedValue({
      dailyFee: { currentPrice: null, prices: [] },
      feeTypes: [
        { id: 3, name: 'Khám tổng quát', active: true, currentPrice: 200000, prices: [] },
        { id: 4, name: 'Tiêm phòng', active: true, currentPrice: 90000, prices: [] },
      ],
    } as never);
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=prices');
    await userEvent.type(await screen.findByRole('searchbox'), 'tiem');
    expect(screen.queryByRole('row', { name: /Khám tổng quát/ })).not.toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Tiêm phòng/ })).toBeInTheDocument();
    await userEvent.clear(screen.getByRole('searchbox'));
    await userEvent.type(screen.getByRole('searchbox'), 'zzz');
    expect(screen.getByText('Không có loại phí khám nào khớp.')).toBeInTheDocument();
  });

  test('AC-454 the adjustment amount names its unit, says a negative is a deduction and shows the full amount', async () => {
    renderAs('CLUB_MANAGER', <BillingPage />, '/', '/?tab=statements');
    await userEvent.click(await screen.findByRole('button', { name: 'Điều chỉnh' }));
    const form = await screen.findByRole('form', { name: 'Thêm khoản điều chỉnh' });
    expect(within(form).getByLabelText(/^Số tiền \(số âm là giảm trừ, tính bằng VND\)/)).toBeInTheDocument();
    await userEvent.type(within(form).getByLabelText(/^Số tiền/), '-300');
    expect(within(form).getByText('= -300.000 VND')).toBeInTheDocument();
  });
});
