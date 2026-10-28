import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import CarePage from './CarePage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { CareItem } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: {
    careDue: vitest.fn(), careOf: vitest.fn(), options: vitest.fn(), createCare: vitest.fn(), completeCare: vitest.fn(),
    overrideCare: vitest.fn(), postponeCare: vitest.fn(), careHistory: vitest.fn(), medicines: vitest.fn(),
  },
}));

const overdue: CareItem = { id: 4, horseId: 9, horseName: 'Sao Mai', type: 'VACCINATION', name: 'Uốn ván', intervalDays: 365, nextDue: '2026-10-20', state: 'OVERDUE', lastCompletion: null, hasDetails: false, plannedSupplyItemId: null, plannedSupplyItemName: null, plannedQuantity: null };
const soon: CareItem = { id: 5, horseId: 9, horseName: 'Sao Mai', type: 'DEWORMING', name: 'Ivermectin', intervalDays: 90, nextDue: '2026-10-27', state: 'DUE_SOON', lastCompletion: null, hasDetails: false, plannedSupplyItemId: null, plannedSupplyItemName: null, plannedQuantity: null };

beforeEach(() => {
  vitest.mocked(api.careDue).mockResolvedValue(pageOf([overdue, soon]));
  vitest.mocked(api.careOf).mockResolvedValue(pageOf([overdue]));
  vitest.mocked(api.options).mockResolvedValue(pageOf([{ id: 9, label: 'Sao Mai', hint: 'VN-9' }]));
  vitest.mocked(api.medicines).mockReset().mockResolvedValue([
    { id: 31, name: 'Vắc-xin uốn ván', unit: 'liều', quantity: 4, state: 'OK' },
  ]);
});

describe('care screen (task 5.2)', () => {
  test('AC-095 and AC-097 the due list shows due-soon and overdue items', async () => {
    renderAs('VETERINARIAN', <CarePage />);
    const table = await screen.findByRole('table', { name: 'Sắp đến hạn và quá hạn' });
    expect(within(table).getByText('Quá hạn')).toBeInTheDocument();
    expect(within(table).getByText('Sắp đến hạn')).toBeInTheDocument();
  });

  test('AC-371 the care row shows state, next due and the latest completion, not notes or batch', async () => {
    vitest.mocked(api.careDue).mockResolvedValue(
      pageOf([{ ...overdue, lastCompletion: { dateDone: '2025-10-20', product: 'Vắc-xin uốn ván', performer: 'BS. Lan' }, hasDetails: true }]),
    );
    renderAs('VETERINARIAN', <CarePage />);
    const table = await screen.findByRole('table', { name: 'Sắp đến hạn và quá hạn' });
    expect(within(table).getByText('Quá hạn')).toBeInTheDocument();
    expect(within(table).getByText('2026-10-20')).toBeInTheDocument();
    expect(within(table).getByText('2025-10-20')).toBeInTheDocument();
    expect(within(table).getByText('Vắc-xin uốn ván · BS. Lan')).toBeInTheDocument();
    expect(within(table).queryByText(/lô/i)).not.toBeInTheDocument();
    expect(api.careHistory).not.toHaveBeenCalled(); // the details open only on request (AC-372)
  });

  test('an item never done says so', async () => {
    renderAs('VETERINARIAN', <CarePage />);
    const table = await screen.findByRole('table', { name: 'Sắp đến hạn và quá hạn' });
    expect(within(table).getAllByText('Chưa thực hiện').length).toBeGreaterThan(0);
  });

  test('AC-369 the performer is a staff member chosen from the list or an outsider typed', async () => {
    vitest.mocked(api.options).mockImplementation(async (kind) =>
      kind === 'staff' ? pageOf([{ id: 2, label: 'BS. Lan', hint: 'bs-lan' }]) : pageOf([{ id: 9, label: 'Sao Mai', hint: 'VN-9' }]),
    );
    vitest.mocked(api.completeCare).mockResolvedValue({} as never);
    renderAs('VETERINARIAN', <CarePage />);
    await userEvent.click(await screen.findByRole('combobox', { name: 'Ngựa' }));
    await userEvent.click(await screen.findByRole('option', { name: /Sao Mai/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Ghi nhận đã thực hiện' }));
    await userEvent.click(screen.getByRole('combobox', { name: /^Người thực hiện/ }));
    await userEvent.click(await screen.findByRole('option', { name: /BS\. Lan/ }));
    await userEvent.click(screen.getByRole('combobox', { name: /^Thuốc dùng/ }));
    await userEvent.click(await screen.findByRole('option', { name: /Vắc-xin uốn ván/ }));
    await userEvent.type(screen.getByLabelText(/^Số lượng đã dùng/), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(api.completeCare).toHaveBeenLastCalledWith(4, expect.objectContaining({ performer: 'BS. Lan', supplyItemId: 31, quantity: 1 }));
    // An outsider is typed as text.
    await userEvent.click(await screen.findByRole('button', { name: 'Ghi nhận đã thực hiện' }));
    await userEvent.click(screen.getByRole('button', { name: 'Người ngoài câu lạc bộ' }));
    await userEvent.type(screen.getByLabelText(/Người ngoài câu lạc bộ/), 'BS. Hưng (Chi cục)');
    await userEvent.click(screen.getByRole('combobox', { name: /^Thuốc dùng/ }));
    await userEvent.click(await screen.findByRole('option', { name: /Vắc-xin uốn ván/ }));
    await userEvent.type(screen.getByLabelText(/^Số lượng đã dùng/), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(api.completeCare).toHaveBeenLastCalledWith(4, expect.objectContaining({ performer: 'BS. Hưng (Chi cục)' }));
  });

  test('AC-339 to AC-341 a vaccination picks a medicine in stock and caps the dose at the stock', async () => {
    renderAs('VETERINARIAN', <CarePage />);
    await userEvent.click(await screen.findByRole('combobox', { name: 'Ngựa' }));
    await userEvent.click(await screen.findByRole('option', { name: /Sao Mai/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Ghi nhận đã thực hiện' }));
    expect(screen.queryByLabelText('Sản phẩm')).not.toBeInTheDocument(); // no free-text product for a medicine
    await userEvent.click(screen.getByRole('combobox', { name: /^Thuốc dùng/ }));
    expect(await screen.findByRole('option', { name: 'Vắc-xin uốn ván (còn 4 liều)' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('option', { name: /Vắc-xin uốn ván/ }));
    expect(screen.getByText('Trong kho còn 4 liều.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Số lượng đã dùng/)).toHaveAttribute('max', '4');
  });

  test('AC-342 a hoof check keeps a free-text product and offers no medicine', async () => {
    vitest.mocked(api.careOf).mockResolvedValue(pageOf([{ ...overdue, id: 6, type: 'HOOF_CHECK', name: 'Cắt móng' }]));
    renderAs('VETERINARIAN', <CarePage />);
    await userEvent.click(await screen.findByRole('combobox', { name: 'Ngựa' }));
    await userEvent.click(await screen.findByRole('option', { name: /Sao Mai/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Ghi nhận đã thực hiện' }));
    expect(screen.getByLabelText('Sản phẩm')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: /^Thuốc dùng/ })).not.toBeInTheDocument();
    expect(api.medicines).not.toHaveBeenCalled();
  });

  test('AC-099 an override without a reason is refused', async () => {
    vitest.mocked(api.overrideCare).mockRejectedValue(
      new ApiError(400, 'REASON_REQUIRED', 'Bắt buộc nhập lý do.', { reason: 'Bắt buộc nhập lý do.' }),
    );
    renderAs('VETERINARIAN', <CarePage />);
    await userEvent.click(await screen.findByRole('combobox', { name: 'Ngựa' }));
    await userEvent.click(await screen.findByRole('option', { name: /Sao Mai/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Sửa ngày đến hạn' }));
    await userEvent.type(screen.getByLabelText('Ngày đến hạn mới'), '2026-11-01');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Bắt buộc nhập lý do.');
  });

  test('AC-101 other roles are refused', () => {
    renderAs('HEAD_TRAINER', <CarePage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
    expect(api.careDue).not.toHaveBeenCalled();
  });
});
