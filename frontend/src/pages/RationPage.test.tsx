import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import RationPage from './RationPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { Ration, RationVersion } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: { ration: vitest.fn(), saveRation: vitest.fn(), rationOptions: vitest.fn(), options: vitest.fn() },
}));

const v1: RationVersion = {
  version: 1,
  effectiveDate: '2026-10-22',
  savedByName: 'Bác sĩ Lan',
  savedAt: '2026-10-22T01:00:00Z',
  meals: [
    {
      name: 'Bữa sáng',
      time: '06:00',
      items: [
        { type: 'GRAIN', supplyItemId: 1, supplyItemName: 'Yến mạch', unit: 'kg', active: true, quantity: 2.5 },
        { type: 'HAY', supplyItemId: 2, supplyItemName: 'Cỏ khô', unit: 'kg', active: false, quantity: 4 },
      ],
    },
  ],
};
const v2: RationVersion = { ...v1, version: 2, effectiveDate: '2026-10-23' };

const base: Ration = { horseId: 9, horseName: 'Sao Mai', archived: false, current: v1, history: [v1] };

beforeEach(() => {
  vitest.mocked(api.ration).mockReset().mockResolvedValue(base);
  vitest.mocked(api.saveRation).mockReset();
  vitest.mocked(api.options).mockReset().mockResolvedValue(pageOf([{ id: 1, label: 'Yến mạch', hint: 'kg' }]));
  vitest.mocked(api.rationOptions).mockReset().mockResolvedValue([
    { id: 1, name: 'Yến mạch', unit: 'kg' },
    { id: 2, name: 'Cỏ khô', unit: 'kg' },
    { id: 3, name: 'Vitamin E', unit: 'g' },
  ]);
});

describe('ration page (REQ-018)', () => {
  test('AC-255 a Groom reads the meals read-only, inactive items are marked', async () => {
    renderAs('GROOM', <RationPage />, '/horses/:id/ration', '/horses/9/ration');
    const list = await screen.findByRole('list', { name: 'Khẩu phần hiện hành' });
    expect(within(list).getByText('Bữa sáng')).toBeInTheDocument();
    expect(within(list).getByText('06:00')).toBeInTheDocument();
    expect(within(list).getByText('Ngũ cốc')).toBeInTheDocument();
    expect(within(list).getByText('đã ngừng dùng')).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Soạn khẩu phần' })).not.toBeInTheDocument();
  });

  test('AC-257 the Owner sees the current ration without history or editor', async () => {
    vitest.mocked(api.ration).mockResolvedValue({ ...base, history: null });
    renderAs('HORSE_OWNER', <RationPage />, '/horses/:id/ration', '/horses/9/ration');
    expect(await screen.findByRole('list', { name: 'Khẩu phần hiện hành' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Lịch sử phiên bản' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Soạn khẩu phần' })).not.toBeInTheDocument();
  });

  test('AC-256 staff see the version history', async () => {
    vitest.mocked(api.ration).mockResolvedValue({ ...base, current: v2, history: [v2, v1] });
    renderAs('HEAD_TRAINER', <RationPage />, '/horses/:id/ration', '/horses/9/ration');
    const history = await screen.findByRole('region', { name: 'Lịch sử phiên bản' });
    expect(within(history).getByRole('list', { name: 'Phiên bản 1' })).toBeInTheDocument();
  });

  test('AC-259 a horse without a ration says so and prompts the Veterinarian', async () => {
    vitest.mocked(api.ration).mockResolvedValue({ ...base, current: null, history: [] });
    renderAs('VETERINARIAN', <RationPage />, '/horses/:id/ration', '/horses/9/ration');
    expect(await screen.findByText('Chưa có khẩu phần')).toBeInTheDocument();
    expect(screen.getByText('Chưa có khẩu phần. Hãy soạn khẩu phần cho ngựa này.')).toBeInTheDocument();
    expect(await screen.findByRole('form', { name: 'Soạn khẩu phần' })).toBeInTheDocument();
  });

  test('AC-245 the Veterinarian composes a ration and saves it', async () => {
    vitest.mocked(api.ration).mockResolvedValue({ ...base, current: null, history: [] });
    vitest.mocked(api.saveRation).mockResolvedValue(base);
    renderAs('VETERINARIAN', <RationPage />, '/horses/:id/ration', '/horses/9/ration');
    const form = await screen.findByRole('form', { name: 'Soạn khẩu phần' });
    await userEvent.type(within(form).getByLabelText('Tên bữa'), 'Bữa sáng');
    await userEvent.type(within(form).getByLabelText('Giờ ăn'), '06:00');
    await userEvent.selectOptions(within(form).getByLabelText('Loại 1.1'), 'GRAIN');
    await userEvent.click(within(form).getByRole('combobox', { name: 'Vật tư 1.1' }));
    await userEvent.click(await screen.findByRole('option', { name: /Yến mạch/ }));
    await userEvent.type(within(form).getByLabelText('Số lượng 1.1'), '2.5');
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu khẩu phần' }));
    expect(api.saveRation).toHaveBeenCalledWith(9, [
      { name: 'Bữa sáng', time: '06:00', items: [{ type: 'GRAIN', supplyItemId: 1, quantity: '2.5' }] },
    ]);
  });

  test('AC-248 a refused item shows the service message under its field', async () => {
    vitest.mocked(api.saveRation).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Số lượng phải lớn hơn 0.', {
        'meals[0].items[0].quantity': 'Số lượng phải là số lớn hơn 0.',
      }),
    );
    renderAs('VETERINARIAN', <RationPage />, '/horses/:id/ration', '/horses/9/ration');
    const form = await screen.findByRole('form', { name: 'Soạn khẩu phần' });
    await userEvent.click(within(form).getByRole('button', { name: 'Lưu khẩu phần' }));
    expect(await within(form).findByText('Số lượng phải là số lớn hơn 0.')).toBeInTheDocument();
  });

  test('AC-253 an archived horse shows the ration read-only for the Veterinarian too', async () => {
    vitest.mocked(api.ration).mockResolvedValue({ ...base, archived: true });
    renderAs('VETERINARIAN', <RationPage />, '/horses/:id/ration', '/horses/9/ration');
    expect(await screen.findByText(/Ngựa đã ngừng quản lý/)).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Soạn khẩu phần' })).not.toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Khẩu phần hiện hành' })).toBeInTheDocument();
  });
});
