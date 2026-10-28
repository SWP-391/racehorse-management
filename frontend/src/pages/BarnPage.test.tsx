import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import BarnPage from './BarnPage';
import { renderAs, renderWithout } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { Barn, BarnHorse } from '../api/types';

vitest.mock('../api/api', () => ({
  api: {
    barn: vitest.fn(),
    setStall: vitest.fn(),
    clearStall: vitest.fn(),
    createArea: vitest.fn(),
    updateArea: vitest.fn(),
    deleteArea: vitest.fn(),
  },
}));

const horse = (id: number, name: string, done = 0, legacyStall: string | null = null): BarnHorse => ({
  horseId: id,
  horseName: name,
  registrationNumber: `VN-00${id}`,
  choresDone: done,
  choresTotal: 4,
  legacyStall,
});

const barn = (over: Partial<Barn> = {}): Barn => ({
  canPlace: true,
  areas: [
    {
      id: 1,
      name: 'Khu A',
      capacity: 4,
      slots: [
        { slot: 1, horse: null },
        { slot: 2, horse: null },
        { slot: 3, horse: horse(1, 'Sao Mai', 4) },
        { slot: 4, horse: null },
      ],
    },
    { id: 2, name: 'Khu B', capacity: 2, slots: [{ slot: 1, horse: horse(2, 'Gió Núi', 1) }, { slot: 2, horse: null }] },
  ],
  unplaced: [horse(3, 'Mây Trắng'), horse(4, 'Hắc Phong', 0, 'Khu C, ô B-2')],
  ...over,
});

beforeEach(() => {
  vitest.mocked(api.barn).mockReset().mockResolvedValue(barn());
  for (const f of [api.setStall, api.clearStall, api.deleteArea]) vitest.mocked(f).mockReset().mockResolvedValue(undefined as never);
  vitest.mocked(api.createArea).mockReset().mockResolvedValue({ id: 3, name: 'Khu C', capacity: 6, occupied: 0 });
  vitest.mocked(api.updateArea).mockReset().mockResolvedValue({ id: 1, name: 'Khu Đông', capacity: 4, occupied: 1 });
});

const areaList = (name: string) => screen.getByRole('list', { name });

describe('barn screen: areas as grids of stalls (BR-163 to BR-165)', () => {
  test('AC-313 every area shows all its stalls, empty ones as placeholders', async () => {
    renderAs('HEAD_TRAINER', <BarnPage />);
    expect(await screen.findByRole('heading', { name: 'Khu A' })).toBeInTheDocument();
    const a = areaList('Khu A');
    expect(within(a).getAllByRole('listitem')).toHaveLength(4);
    expect(within(a).getByText('Sao Mai')).toBeInTheDocument();
    expect(within(a).getByText('Xong cả bốn việc')).toBeInTheDocument();
    expect(within(a).getAllByText('Ô trống')).toHaveLength(3);
    expect(within(areaList('Khu B')).getByText('1/4 việc xong')).toBeInTheDocument();
  });

  test('AC-220 horses without a stall are listed apart, an old text stall is flagged for placing again', async () => {
    renderAs('HEAD_TRAINER', <BarnPage />);
    const apart = await screen.findByRole('table', { name: 'Ngựa chưa có chuồng' });
    expect(within(apart).getByText('Mây Trắng')).toBeInTheDocument();
    expect(within(apart).getByText('Cần xếp lại')).toBeInTheDocument();
    expect(within(apart).getByText('Chuồng cũ: Khu C, ô B-2')).toBeInTheDocument();
  });

  test('AC-221, AC-318 only the Club Manager has stall and area controls', async () => {
    for (const role of ['HEAD_TRAINER', 'VETERINARIAN', 'GROOM', 'HORSE_OWNER'] as const) {
      const view = renderAs(role, <BarnPage />);
      await screen.findByRole('heading', { name: 'Khu A' });
      expect(screen.queryByRole('button', { name: /Xếp chuồng|Chuyển chuồng|Bỏ .* khỏi chuồng|Ô trống \d+ của/ })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Thêm khu|Sửa khu|Xóa khu/ })).not.toBeInTheDocument();
      view.unmount();
    }
  });

  test('AC-313 the Club Manager picks a horse for an empty stall from a select and the horse is placed there', async () => {
    renderAs('CLUB_MANAGER', <BarnPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Ô trống 2 của Khu A: chọn ngựa để xếp' }));
    const dialog = await screen.findByRole('dialog', { name: 'Xếp ngựa vào ô trống' });
    expect(within(dialog).getByLabelText('Khu')).toHaveValue('1');
    expect(within(dialog).getByLabelText('Ô')).toHaveValue('2');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(within(dialog).getByText('Chọn một con ngựa.')).toBeInTheDocument();
    expect(api.setStall).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Ngựa chưa có chuồng' }));
    await userEvent.click(await screen.findByRole('option', { name: /Mây Trắng/ }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(api.setStall).toHaveBeenCalledWith(3, { areaId: 1, slot: 2 });
    await vitest.waitFor(() => expect(api.barn).toHaveBeenCalledTimes(2));
  });

  test('AC-217 a horse without a stall is placed by choosing an area and a free slot', async () => {
    renderAs('CLUB_MANAGER', <BarnPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Xếp chuồng cho Mây Trắng' }));
    const dialog = await screen.findByRole('dialog', { name: 'Xếp chuồng cho Mây Trắng' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(within(dialog).getByText('Chọn khu.')).toBeInTheDocument();
    expect(within(dialog).getByText('Chọn ô chuồng.')).toBeInTheDocument();
    await userEvent.selectOptions(within(dialog).getByLabelText('Khu'), '2');
    // The slot held by another horse cannot be chosen.
    expect(within(dialog).getByRole('option', { name: 'Ô 1 đã có ngựa (Gió Núi)' })).toBeDisabled();
    await userEvent.selectOptions(within(dialog).getByLabelText('Ô'), '2');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(api.setStall).toHaveBeenCalledWith(3, { areaId: 2, slot: 2 });
  });

  test('AC-218 a stall refused by the service shows its message in the dialog', async () => {
    vitest.mocked(api.setStall).mockRejectedValue(new ApiError(409, 'AREA_FULL', 'Khu "Khu B" đã đủ chỗ, hãy tạo khu mới.'));
    renderAs('CLUB_MANAGER', <BarnPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Xếp chuồng cho Mây Trắng' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.selectOptions(within(dialog).getByLabelText('Khu'), '2');
    await userEvent.selectOptions(within(dialog).getByLabelText('Ô'), '2');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(await within(dialog).findByText('Khu "Khu B" đã đủ chỗ, hãy tạo khu mới.')).toBeInTheDocument();
  });

  test('AC-219 moving fills the dialog with the stall; clearing asks first', async () => {
    renderAs('CLUB_MANAGER', <BarnPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Chuyển chuồng của Sao Mai' }));
    const dialog = await screen.findByRole('dialog', { name: 'Chuyển chuồng của Sao Mai' });
    expect(within(dialog).getByLabelText('Khu')).toHaveValue('1');
    expect(within(dialog).getByLabelText('Ô')).toHaveValue('3');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Hủy' }));
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ Gió Núi khỏi chuồng' }));
    const confirm = await screen.findByRole('alertdialog');
    await userEvent.click(within(confirm).getByRole('button', { name: 'Bỏ khỏi chuồng' }));
    expect(api.clearStall).toHaveBeenCalledWith(2);
  });

  test('AC-312 the Club Manager adds an area; empty or out-of-range values are refused first', async () => {
    renderAs('CLUB_MANAGER', <BarnPage />);
    await userEvent.click(await screen.findByRole('button', { name: /Thêm khu/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Thêm khu' });
    await userEvent.clear(within(dialog).getByLabelText('Sức chứa (số ô)'));
    await userEvent.type(within(dialog).getByLabelText('Sức chứa (số ô)'), '500');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(within(dialog).getByText('Nhập tên khu, ví dụ Khu A.')).toBeInTheDocument();
    expect(within(dialog).getByText('Sức chứa là số ô nguyên từ 1 đến 200.')).toBeInTheDocument();
    expect(api.createArea).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText('Tên khu'), 'Khu C');
    await userEvent.clear(within(dialog).getByLabelText('Sức chứa (số ô)'));
    await userEvent.type(within(dialog).getByLabelText('Sức chứa (số ô)'), '6');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(api.createArea).toHaveBeenCalledWith({ name: 'Khu C', capacity: 6 });
  });

  test('AC-315 a refused capacity shows the service message; AC-317 renaming sends the new name', async () => {
    vitest.mocked(api.updateArea).mockRejectedValueOnce(
      new ApiError(409, 'CAPACITY_BELOW_OCCUPIED', 'Sức chứa không thể thấp hơn ô số 3 đang có ngựa.', {
        capacity: 'Sức chứa không thể thấp hơn ô số 3 đang có ngựa.',
      }),
    );
    renderAs('CLUB_MANAGER', <BarnPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa khu Khu A' }));
    const dialog = await screen.findByRole('dialog', { name: 'Sửa khu' });
    await userEvent.clear(within(dialog).getByLabelText('Sức chứa (số ô)'));
    await userEvent.type(within(dialog).getByLabelText('Sức chứa (số ô)'), '2');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(await within(dialog).findByText('Sức chứa không thể thấp hơn ô số 3 đang có ngựa.')).toBeInTheDocument();
    await userEvent.clear(within(dialog).getByLabelText('Tên khu'));
    await userEvent.type(within(dialog).getByLabelText('Tên khu'), 'Khu Đông');
    await userEvent.clear(within(dialog).getByLabelText('Sức chứa (số ô)'));
    await userEvent.type(within(dialog).getByLabelText('Sức chứa (số ô)'), '4');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(api.updateArea).toHaveBeenLastCalledWith(1, { name: 'Khu Đông', capacity: 4 });
  });

  test('AC-316 deleting an area asks first and a refusal is reported', async () => {
    renderAs('CLUB_MANAGER', <BarnPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Xóa khu Khu B' }));
    const confirm = await screen.findByRole('alertdialog');
    await userEvent.click(within(confirm).getByRole('button', { name: 'Xóa khu' }));
    expect(api.deleteArea).toHaveBeenCalledWith(2);
  });

  test('a Horse Owner sees only the stalls of own horses and no placeholders', async () => {
    vitest.mocked(api.barn).mockResolvedValue(
      barn({
        canPlace: false,
        areas: [{ id: 1, name: 'Khu A', capacity: 4, slots: [{ slot: 3, horse: horse(1, 'Sao Mai', 4) }] }],
        unplaced: [],
      }),
    );
    renderAs('HORSE_OWNER', <BarnPage />);
    const a = await screen.findByRole('list', { name: 'Khu A' });
    expect(within(a).getAllByRole('listitem')).toHaveLength(1);
    expect(screen.queryByText('Ô trống')).not.toBeInTheDocument();
  });

  test('states: error with retry, and empty', async () => {
    vitest.mocked(api.barn).mockRejectedValueOnce(new ApiError(500, 'X', 'Máy chủ không phản hồi.'));
    renderAs('CLUB_MANAGER', <BarnPage />);
    expect(await screen.findByText('Máy chủ không phản hồi.')).toBeInTheDocument();
    vitest.mocked(api.barn).mockResolvedValue(barn({ areas: [], unplaced: [] }));
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Chưa có khu chuồng nào')).toBeInTheDocument();
  });

  test('a user without VIEW_BARN gets the access-denied state and no request', () => {
    renderWithout('VETERINARIAN', ['VIEW_BARN'], <BarnPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
    expect(api.barn).not.toHaveBeenCalled();
  });
});
