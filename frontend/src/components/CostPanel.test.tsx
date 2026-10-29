import { screen } from '@testing-library/react';
import { vi as vitest } from 'vitest';
import CostPanel from './CostPanel';
import { renderAs } from '../test/render';
import { api } from '../api/api';

vitest.mock('../api/api', () => ({
  api: {
    horseCosts: vitest.fn().mockResolvedValue({
      month: '2026-10',
      closed: false,
      ownerId: 7,
      ownerName: 'Bà Lý Thị Hoa',
      total: 150000,
      invoiceId: null,
      invoiceNumber: null,
      dailyFeeThrough: '2026-10-24',
      horses: [
        {
          horseId: 9,
          horseName: 'Sấm Chớp',
          total: 150000,
          categories: [
            { category: 'DAILY_FEE', total: 150000, lines: [{ date: '2026-10-24', description: 'Phí chuồng và công chăm sóc', quantity: 1, unitPrice: 150000, amount: 150000, reason: null }] },
          ],
        },
      ],
    }),
  },
}));

describe('cost panel of a horse (BR-182, AC-359, AC-360)', () => {
  test('the owner and the Club Manager read the horse costs', async () => {
    renderAs('HORSE_OWNER', <CostPanel horseId={9} />);
    expect(await screen.findByRole('heading', { name: 'Chi phí của ngựa' })).toBeInTheDocument();
    expect((await screen.findAllByText('Phí chuồng và công chăm sóc')).length).toBeGreaterThan(0);
    expect(api.horseCosts).toHaveBeenCalled();
  });

  test.each(['HEAD_TRAINER', 'VETERINARIAN', 'GROOM'] as const)('%s sees no cost panel and no request is made', (role) => {
    vitest.mocked(api.horseCosts).mockClear();
    renderAs(role, <CostPanel horseId={9} />);
    expect(screen.queryByRole('heading', { name: 'Chi phí của ngựa' })).not.toBeInTheDocument();
    expect(api.horseCosts).not.toHaveBeenCalled();
  });
});
