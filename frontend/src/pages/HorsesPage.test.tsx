import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import HorsesPage from './HorsesPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import type { Horse } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({ api: { horses: vitest.fn(), readinessList: vitest.fn(), areas: vitest.fn().mockResolvedValue([{ id: 1, name: 'A', capacity: 4, occupied: 1 }]) } }));

function horse(id: number, name: string): Horse {
  return {
    id, name, registrationNumber: `R${id}`, sex: 'MALE', birthDate: '2020-01-01', coatColour: null, breed: null,
    owner: { id: 3, displayName: 'Chủ B' }, archived: false, archiveReason: null, archiveNote: null, archivedAt: null, photoVersion: null,
    assignedTrainer: { id: 1, displayName: 'Test HEAD_TRAINER', active: true }, mainTrainer: { id: 1, displayName: 'Test HEAD_TRAINER', active: true }, temporaryTrainer: null, canWrite: true,
  };
}

describe('readiness on the Owner horse list (task 5.2, BR-080)', () => {
  test('each readiness value is shown in Vietnamese', async () => {
    vitest.mocked(api.horses).mockResolvedValue(pageOf([horse(1, 'A'), horse(2, 'B'), horse(3, 'C')]));
    vitest.mocked(api.readinessList).mockResolvedValue([
      { horseId: 1, readiness: 'READY' },
      { horseId: 2, readiness: 'UNDER_OBSERVATION' },
      { horseId: 3, readiness: 'NOT_READY' },
    ]);
    renderAs('HORSE_OWNER', <HorsesPage />);
    expect(await screen.findByText('Sẵn sàng')).toBeInTheDocument();
    expect(screen.getByText('Cần theo dõi')).toBeInTheDocument();
    expect(screen.getByText('Không sẵn sàng')).toBeInTheDocument();
  });
});

describe('own horses view (critic round 3)', () => {
  test('a Horse Owner sees their own horses without a repeated owner column', async () => {
    vitest.mocked(api.horses).mockResolvedValue(pageOf([horse(1, 'A')]));
    vitest.mocked(api.readinessList).mockResolvedValue([]);
    renderAs('HORSE_OWNER', <HorsesPage />);
    expect(await screen.findByRole('link', { name: 'A' })).toBeInTheDocument();
    expect(screen.getByText('Những con ngựa bạn sở hữu.')).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: /Chủ sở hữu/ })).not.toBeInTheDocument();
  });

  test('staff still see the owner column', async () => {
    vitest.mocked(api.horses).mockResolvedValue(pageOf([horse(1, 'A')]));
    vitest.mocked(api.readinessList).mockResolvedValue([]);
    renderAs('CLUB_MANAGER', <HorsesPage />);
    expect(await screen.findByRole('columnheader', { name: /Chủ sở hữu/ })).toBeInTheDocument();
    expect(screen.getByText('Chủ B')).toBeInTheDocument();
  });
});

describe('paged horse list (BR-185, AC-365 to AC-367, AC-370)', () => {
  const many = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, i) => horse(from + i, `Ngựa ${from + i}`));

  test('AC-365 25 horses: 20 rows, a way to the next 5, and the count', async () => {
    vitest.mocked(api.readinessList).mockResolvedValue([]);
    vitest.mocked(api.horses).mockImplementation(async (query) =>
      query?.page === 1 ? { items: many(21, 25), page: 1, size: 20, total: 25 } : { items: many(1, 20), page: 0, size: 20, total: 25 },
    );
    renderAs('CLUB_MANAGER', <HorsesPage />);
    expect(await screen.findByRole('link', { name: 'Ngựa 1' })).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(21); // header + 20
    expect(screen.getByText('25 kết quả')).toBeInTheDocument();
    expect(screen.getByText('1–20 / 25')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Trang sau' }));
    expect(await screen.findByRole('link', { name: 'Ngựa 25' })).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(6);
    expect(api.horses).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 }));
  });

  test('AC-366 a search that matches nothing shows a message and keeps the search text', async () => {
    vitest.mocked(api.readinessList).mockResolvedValue([]);
    vitest.mocked(api.horses).mockImplementation(async (query) =>
      query?.q ? { items: [], page: 0, size: 20, total: 0 } : { items: many(1, 3), page: 0, size: 20, total: 3 },
    );
    renderAs('CLUB_MANAGER', <HorsesPage />);
    await screen.findByRole('link', { name: 'Ngựa 1' });
    await userEvent.type(screen.getByRole('searchbox'), 'không có');
    expect(await screen.findByText('Không có kết quả phù hợp', undefined, { timeout: 2000 })).toBeInTheDocument();
    expect(screen.getByRole('searchbox')).toHaveValue('không có');
  });

  test('AC-367 the health filter and the area filter are sent to the service', async () => {
    vitest.mocked(api.readinessList).mockResolvedValue([]);
    vitest.mocked(api.horses).mockResolvedValue(pageOf(many(1, 2)));
    renderAs('CLUB_MANAGER', <HorsesPage />);
    await screen.findByRole('link', { name: 'Ngựa 1' });
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Trạng thái sức khỏe' }), 'INJURED');
    await vitest.waitFor(() => expect(api.horses).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'INJURED', page: 0 })));
    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Khu chuồng' }), 'A');
    await vitest.waitFor(() => expect(api.horses).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'INJURED', area: 'A' })));
  });

  test('AC-370 an Owner sees the count of their own horses only', async () => {
    vitest.mocked(api.readinessList).mockResolvedValue([]);
    vitest.mocked(api.horses).mockResolvedValue(pageOf(many(1, 3)));
    renderAs('HORSE_OWNER', <HorsesPage />);
    await screen.findByRole('link', { name: 'Ngựa 1' });
    expect(screen.getByText('3 kết quả')).toBeInTheDocument();
    // The Owner has no health filter (PERM-020): the service would refuse it.
    expect(screen.queryByRole('combobox', { name: 'Trạng thái sức khỏe' })).not.toBeInTheDocument();
  });
});
