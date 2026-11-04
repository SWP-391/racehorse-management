import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import SuppliesPage from './SuppliesPage';
import { renderAs, renderWithout } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { RestockProposal, SupplyItem } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: {
    supplies: vitest.fn(), createSupply: vitest.fn(), updateSupply: vitest.fn(), proposeRestock: vitest.fn(),
    proposals: vitest.fn(), approveProposal: vitest.fn(), rejectProposal: vitest.fn(),
    receiveStock: vitest.fn(), adjustStock: vitest.fn(), setSupplyPrice: vitest.fn(), stockMovements: vitest.fn(),
    expectedReceipts: vitest.fn(), expectReceipt: vitest.fn(), cancelExpectedReceipt: vitest.fn(),
    supplyUnits: vitest.fn(), addSupplyUnit: vitest.fn(), renameSupplyUnit: vitest.fn(), setSupplyUnitActive: vitest.fn(),
  },
}));

const oats: SupplyItem = {
  id: 1, name: 'Yến mạch', category: 'FEED', unit: 'kg', unitId: 1, quantity: 60, unitPrice: 25000, daysOfCover: 6, state: 'RESTOCK', dailyUse: 10,
  active: true,
};
const old: SupplyItem = { id: 2, name: 'Cỏ khô', category: 'FEED', unit: 'bó', unitId: 9, quantity: 0, unitPrice: 0, state: 'OUT', active: false };
const pending: RestockProposal = {
  id: 7, itemId: 1, itemName: 'Yến mạch', unit: 'kg', groomName: 'Minh', quantity: 200, note: 'Sắp hết', state: 'PENDING',
  createdAt: '2026-10-22T01:00:00Z', decidedByName: null, decidedAt: null, decisionReason: null,
};
const rejected: RestockProposal = {
  ...pending, id: 6, state: 'REJECTED', decidedByName: 'Quản lý', decidedAt: '2026-10-21T02:00:00Z', decisionReason: 'Còn đủ dùng',
};

const units = [
  { id: 1, name: 'kg', active: true, itemCount: 1 },
  { id: 2, name: 'lít', active: true, itemCount: 0 },
  { id: 9, name: 'bó', active: false, itemCount: 1 },
];

beforeEach(() => {
  vitest.mocked(api.supplyUnits).mockReset().mockResolvedValue(units);
  for (const f of [api.addSupplyUnit, api.renameSupplyUnit, api.setSupplyUnitActive]) vitest.mocked(f).mockReset().mockResolvedValue({} as never);
  vitest.mocked(api.supplies).mockReset().mockResolvedValue(pageOf([oats, old]));
  // The service filters by state: the pending list asks for PENDING only.
  vitest.mocked(api.proposals).mockReset().mockImplementation(async (query) =>
    pageOf([pending, rejected].filter((p) => !query?.state || p.state === query.state)),
  );
  for (const f of [
    api.createSupply, api.updateSupply, api.proposeRestock, api.approveProposal, api.rejectProposal, api.receiveStock,
    api.adjustStock, api.setSupplyPrice, api.expectReceipt, api.cancelExpectedReceipt,
  ]) {
    vitest.mocked(f).mockReset().mockResolvedValue({} as never);
  }
  vitest.mocked(api.stockMovements).mockReset().mockResolvedValue(pageOf([]));
  vitest.mocked(api.expectedReceipts).mockReset().mockResolvedValue([]);
});

/** A row button now opens a confirmation popup; this presses its confirm button. */
async function confirmWith(name: string) {
  await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name }));
}

describe('supplies screen (task 6.4)', () => {
  test('AC-223 the Club Manager adds an item; invalid values are refused first (AC-224)', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm vật tư' }));
    const dialog = await screen.findByRole('dialog', { name: 'Thêm vật tư' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(within(dialog).getByText('Nhập tên vật tư.')).toBeInTheDocument();
    expect(within(dialog).getByText('Chọn loại vật tư.')).toBeInTheDocument();
    expect(within(dialog).getByText('Nhập giá từ 0 trở lên.')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Tên vật tư'), 'Cám');
    await userEvent.selectOptions(within(dialog).getByLabelText('Loại'), 'FEED');
    await userEvent.selectOptions(within(dialog).getByLabelText('Đơn vị'), '1');
    await userEvent.type(within(dialog).getByLabelText('Giá (VND)'), '25');
    await userEvent.type(within(dialog).getByLabelText('Tồn đầu'), '-3');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(within(dialog).getByText('Số lượng tồn phải là số nguyên từ 0 trở lên.')).toBeInTheDocument();
    expect(api.createSupply).not.toHaveBeenCalled();
    await userEvent.clear(within(dialog).getByLabelText('Tồn đầu'));
    await userEvent.type(within(dialog).getByLabelText('Tồn đầu'), '50');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(api.createSupply).toHaveBeenCalledWith({
      name: 'Cám', category: 'FEED', unitId: 1, active: true, price: 25000, openingQuantity: 50,
    });
  });

  test('AC-224 a duplicate name from the service is shown in the dialog', async () => {
    vitest.mocked(api.createSupply).mockRejectedValue(new ApiError(409, 'SUPPLY_DUPLICATE', 'Vật tư Yến mạch đã có trong loại Thức ăn.'));
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm vật tư' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Tên vật tư'), 'Yến mạch');
    await userEvent.selectOptions(within(dialog).getByLabelText('Loại'), 'FEED');
    await userEvent.selectOptions(within(dialog).getByLabelText('Đơn vị'), '1');
    await userEvent.type(within(dialog).getByLabelText('Giá (VND)'), '1000');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(await within(dialog).findByText('Vật tư Yến mạch đã có trong loại Thức ăn.')).toBeInTheDocument();
  });

  test('AC-225 edit and deactivate send the whole item', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Ngừng dùng Yến mạch' }));
    await confirmWith('Ngừng dùng');
    expect(api.updateSupply).toHaveBeenCalledWith(1, { name: 'Yến mạch', category: 'FEED', unitId: 1, active: false });
    await userEvent.click(screen.getByRole('button', { name: 'Dùng lại Cỏ khô' }));
    await confirmWith('Dùng lại');
    expect(api.updateSupply).toHaveBeenCalledWith(2, expect.objectContaining({ active: true }));
    await userEvent.click(screen.getByRole('button', { name: 'Sửa Yến mạch' }));
    expect(within(await screen.findByRole('dialog', { name: 'Sửa vật tư' })).getByLabelText('Tên vật tư')).toHaveValue('Yến mạch');
  });

  test('AC-333 and AC-334 the catalog shows the price, the days of cover and the state with text', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    const table = await screen.findByRole('table', { name: 'Danh mục vật tư' });
    const row = within(table).getByText('Yến mạch').closest('tr') as HTMLElement;
    expect(within(row).getByText('6 ngày')).toBeInTheDocument();
    expect(within(row).getByText('Cần nhập')).toBeInTheDocument();
    expect(within(row).getByText('25.000')).toBeInTheDocument();
    const gone = within(table).getByText('Cỏ khô').closest('tr') as HTMLElement;
    expect(within(gone).getByText('Hết hàng')).toBeInTheDocument();
    expect(within(gone).getByText('Không dùng hằng ngày')).toBeInTheDocument();
  });

  test('the quantity is changed by the Kho dialog: a receipt, and an adjustment that needs a reason (AC-332)', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Quản lý kho Yến mạch' }));
    const dialog = await screen.findByRole('dialog', { name: 'Kho: Yến mạch' });
    // The receipt tab is open at first: its tab button and its submit button share the name "Nhập kho".
    await userEvent.type(within(dialog).getByLabelText('Số lượng nhập (kg)'), '30');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Nhập kho' }).at(-1) as HTMLElement);
    expect(api.receiveStock).toHaveBeenCalledWith(1, 30, '', undefined);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Điều chỉnh' }));
    await userEvent.type(within(dialog).getByLabelText('Số lượng đếm được (kg)'), '40');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Điều chỉnh' }).at(-1) as HTMLElement);
    expect(within(dialog).getByText('Bắt buộc nhập lý do điều chỉnh.')).toBeInTheDocument();
    expect(api.adjustStock).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText('Lý do'), 'kiểm kê');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Điều chỉnh' }).at(-1) as HTMLElement);
    expect(api.adjustStock).toHaveBeenCalledWith(1, 40, 'kiểm kê');
  });

  test('AC-228 the Club Manager approves a pending proposal', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đề xuất chờ duyệt' }));
    const table = await screen.findByRole('table', { name: 'Đề xuất chờ duyệt' });
    expect(within(table).getByText('Minh')).toBeInTheDocument();
    expect(within(table).queryByText('Còn đủ dùng')).not.toBeInTheDocument();
    await userEvent.click(within(table).getByRole('button', { name: 'Duyệt đề xuất Yến mạch' }));
    await confirmWith('Duyệt');
    expect(api.approveProposal).toHaveBeenCalledWith(7);
    await vitest.waitFor(() => expect(api.proposals).toHaveBeenCalledTimes(2));
  });

  test('AC-229 and AC-230 rejecting needs a reason', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đề xuất chờ duyệt' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Từ chối đề xuất Yến mạch' }));
    const dialog = await screen.findByRole('dialog', { name: 'Từ chối đề xuất' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Từ chối đề xuất' }));
    expect(within(dialog).getByText('Nhập lý do từ chối.')).toBeInTheDocument();
    expect(api.rejectProposal).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText('Lý do từ chối'), 'Còn đủ dùng');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Từ chối đề xuất' }));
    expect(api.rejectProposal).toHaveBeenCalledWith(7, 'Còn đủ dùng');
  });

  test('AC-230 an already decided proposal shows the refusal', async () => {
    vitest.mocked(api.approveProposal).mockRejectedValue(new ApiError(409, 'PROPOSAL_DECIDED', 'Đề xuất đã được quyết định.'));
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đề xuất chờ duyệt' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Duyệt đề xuất Yến mạch' }));
    await confirmWith('Duyệt');
    await vitest.waitFor(() => expect(api.proposals).toHaveBeenCalledTimes(2));
  });

  test('AC-226 and AC-227 a Groom proposes with a quantity above zero', async () => {
    renderAs('GROOM', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đề xuất bổ sung Yến mạch' }));
    const dialog = await screen.findByRole('dialog', { name: 'Đề xuất bổ sung Yến mạch' });
    await userEvent.type(within(dialog).getByLabelText('Số lượng cần bổ sung (kg)'), '0');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Gửi đề xuất' }));
    expect(within(dialog).getByText('Số lượng bổ sung phải là số nguyên lớn hơn 0.')).toBeInTheDocument();
    expect(api.proposeRestock).not.toHaveBeenCalled();
    await userEvent.clear(within(dialog).getByLabelText('Số lượng cần bổ sung (kg)'));
    await userEvent.type(within(dialog).getByLabelText('Số lượng cần bổ sung (kg)'), '200');
    await userEvent.type(within(dialog).getByLabelText('Ghi chú (không bắt buộc)'), 'Sắp hết');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Gửi đề xuất' }));
    expect(api.proposeRestock).toHaveBeenCalledWith(1, 200, 'Sắp hết');
  });

  test('AC-227 an inactive item refused by the service is shown in the dialog', async () => {
    vitest.mocked(api.proposeRestock).mockRejectedValue(new ApiError(409, 'SUPPLY_INACTIVE', 'Vật tư này đã ngừng dùng.'));
    renderAs('GROOM', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đề xuất bổ sung Yến mạch' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Số lượng cần bổ sung (kg)'), '5');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Gửi đề xuất' }));
    expect(await within(dialog).findByText('Vật tư này đã ngừng dùng.')).toBeInTheDocument();
  });

  test('AC-229 and AC-231 a Groom sees own proposals with state and reason, and no manager controls', async () => {
    renderAs('GROOM', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đề xuất của bạn' }));
    const mine = await screen.findByRole('table', { name: 'Đề xuất của bạn' });
    expect(within(mine).getByText('Chờ duyệt')).toBeInTheDocument();
    expect(within(mine).getByText('Từ chối')).toBeInTheDocument();
    expect(within(mine).getByText('Lý do: Còn đủ dùng')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Thêm vật tư|Sửa |Ngừng dùng|Duyệt|Từ chối đề xuất/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Đề xuất chờ duyệt' })).not.toBeInTheDocument();
  });

  test('states: error with retry, and empty', async () => {
    vitest.mocked(api.supplies).mockRejectedValueOnce(new ApiError(500, 'X', 'Máy chủ không phản hồi.'));
    vitest.mocked(api.proposals).mockResolvedValue(pageOf([]));
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    expect(await screen.findByText('Máy chủ không phản hồi.')).toBeInTheDocument();
    vitest.mocked(api.supplies).mockResolvedValue(pageOf([]));
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Chưa có vật tư nào')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Đề xuất chờ duyệt/ }));
    expect(await screen.findByText('Không có đề xuất nào chờ duyệt')).toBeInTheDocument();
  });

  test('AC-232 a user without VIEW_SUPPLIES gets the access-denied state and no request', () => {
    renderWithout('GROOM', ['VIEW_SUPPLIES'], <SuppliesPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
    expect(api.supplies).not.toHaveBeenCalled();
  });
});

describe('supply form validation (input-validation)', () => {
  test('the unit is chosen from a list, the price and stock carry their bounds and a long name is stopped', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm vật tư' }));
    const dialog = await screen.findByRole('dialog', { name: 'Thêm vật tư' });
    expect(within(dialog).getByLabelText('Đơn vị').tagName).toBe('SELECT');
    expect(within(dialog).getByLabelText('Giá (VND)')).toHaveAttribute('data-unit', 'VND');
    expect(within(dialog).getByLabelText('Tính tiền theo')).toHaveValue('1000');
    expect(within(dialog).getByLabelText('Tồn đầu')).toHaveAttribute('step', '1');
    expect(within(dialog).getByLabelText('Tên vật tư')).toHaveAttribute('maxlength', '200');
  });

  test('a fractional stock and a price above the limit are refused on the fields', async () => {
    vitest.mocked(api.createSupply).mockClear();
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm vật tư' }));
    const dialog = await screen.findByRole('dialog', { name: 'Thêm vật tư' });
    await userEvent.type(within(dialog).getByLabelText('Tên vật tư'), 'Cám');
    await userEvent.selectOptions(within(dialog).getByLabelText('Loại'), 'FEED');
    await userEvent.selectOptions(within(dialog).getByLabelText('Đơn vị'), '1');
    await userEvent.type(within(dialog).getByLabelText('Giá (VND)'), '99999999999');
    // The key "." is blocked in a whole-number field; a pasted or scripted value still reaches the rule.
    fireEvent.change(within(dialog).getByLabelText('Tồn đầu'), { target: { value: '2.5' } });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(within(dialog).getByLabelText('Giá (VND)')).toHaveAccessibleDescription(/tối đa/);
    expect(within(dialog).getByLabelText('Tồn đầu')).toHaveAttribute('aria-invalid', 'true');
    expect(api.createSupply).not.toHaveBeenCalled();
  });

  test('AC-392 the unit field is a list of active units, there is no free-text unit field', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm vật tư' }));
    const dialog = await screen.findByRole('dialog', { name: 'Thêm vật tư' });
    const field = within(dialog).getByLabelText('Đơn vị');
    expect(field.tagName).toBe('SELECT');
    expect(within(field).getByRole('option', { name: 'kg' })).toBeInTheDocument();
    expect(within(field).queryByRole('option', { name: /bó/ })).not.toBeInTheDocument();
  });

  test('an item keeps its deactivated unit in the list and says so', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa Cỏ khô' }));
    const dialog = await screen.findByRole('dialog', { name: 'Sửa vật tư' });
    expect(within(within(dialog).getByLabelText('Đơn vị')).getByRole('option', { name: /bó \(ngừng dùng/ })).toBeInTheDocument();
  });

  test('with no active unit the form says so and is not saved', async () => {
    vitest.mocked(api.supplyUnits).mockResolvedValue([{ id: 9, name: 'bó', active: false, itemCount: 1 }]);
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm vật tư' }));
    const dialog = await screen.findByRole('dialog', { name: 'Thêm vật tư' });
    expect(await within(dialog).findByText(/Chưa có đơn vị nào đang dùng/)).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText('Tên vật tư'), 'Cám');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(api.createSupply).not.toHaveBeenCalled();
  });

  test('AC-388 the Club Manager adds a unit; a bad name is refused before sending', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đơn vị' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Thêm đơn vị' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Tên đơn vị'), '123');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(within(dialog).getByText('Tên đơn vị phải có ít nhất một chữ cái.')).toBeInTheDocument();
    expect(api.addSupplyUnit).not.toHaveBeenCalled();
    await userEvent.clear(within(dialog).getByLabelText('Tên đơn vị'));
    await userEvent.type(within(dialog).getByLabelText('Tên đơn vị'), '  chai   lớn ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    await vitest.waitFor(() => expect(api.addSupplyUnit).toHaveBeenCalledWith('  chai   lớn '));
  });

  test('AC-389 a unit is renamed, deactivated and reactivated from the table, never deleted', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đơn vị' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Đổi tên đơn vị kg' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.clear(within(dialog).getByLabelText('Tên đơn vị'));
    await userEvent.type(within(dialog).getByLabelText('Tên đơn vị'), 'kilô');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    await vitest.waitFor(() => expect(api.renameSupplyUnit).toHaveBeenCalledWith(1, 'kilô'));
    await userEvent.click(await screen.findByRole('button', { name: 'Ngừng dùng đơn vị lít' }));
    await vitest.waitFor(() => expect(api.setSupplyUnitActive).toHaveBeenCalledWith(2, false));
    await userEvent.click(await screen.findByRole('button', { name: 'Dùng lại đơn vị bó' }));
    await vitest.waitFor(() => expect(api.setSupplyUnitActive).toHaveBeenCalledWith(9, true));
    expect(screen.queryByRole('button', { name: /Xóa/ })).not.toBeInTheDocument();
  });

  test('a Groom does not see the units section', async () => {
    renderAs('GROOM', <SuppliesPage />);
    await screen.findAllByText('Yến mạch');
    expect(screen.queryByRole('button', { name: 'Thêm đơn vị' })).not.toBeInTheDocument();
    expect(api.supplyUnits).not.toHaveBeenCalled();
  });
});

describe('supplies tabs (BR-209, AC-429, AC-430)', () => {
  test('AC-429 a Groom has "Vật tư đang dùng" and "Đề xuất của bạn" as two tabs, supplies first', async () => {
    renderAs('GROOM', <SuppliesPage />);
    expect(await screen.findByRole('button', { name: 'Vật tư đang dùng' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('table', { name: 'Đề xuất của bạn' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Đề xuất của bạn' }));
    expect(await screen.findByRole('table', { name: 'Đề xuất của bạn' })).toBeInTheDocument();
  });

  test('AC-430 the Club Manager has the catalog, the pending proposals and the units each in its own tab', async () => {
    renderAs('CLUB_MANAGER', <SuppliesPage />);
    for (const name of ['Danh mục vật tư', 'Đề xuất chờ duyệt', 'Đơn vị']) {
      expect(await screen.findByRole('button', { name: new RegExp(name) })).toBeInTheDocument();
    }
    expect(screen.queryByRole('table', { name: 'Đề xuất chờ duyệt' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Đề xuất chờ duyệt/ }));
    expect(await screen.findByRole('table', { name: 'Đề xuất chờ duyệt' })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Danh mục vật tư' })).not.toBeInTheDocument();
  });
});
