import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import HorseDetailPage from './HorseDetailPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import { PEDIGREE_POSITIONS, type Horse } from '../api/types';
import { pageOf } from '../test/page';
import { today } from '../lib/validate';

vitest.mock('../api/api', () => ({
  api: {
    horse: vitest.fn(),
    pedigree: vitest.fn(),
    horses: vitest.fn(),
    archiveHorse: vitest.fn(),
    changeTrainer: vitest.fn(),
    lendTrainer: vitest.fn(),
    endLoan: vitest.fn(),
    options: vitest.fn(),
    restoreHorse: vitest.fn(),
    updatePedigree: vitest.fn(),
    horseHealth: vitest.fn(),
    readiness: vitest.fn(),
    plansOf: vitest.fn().mockResolvedValue([]),
    schedule: vitest.fn().mockResolvedValue([]),
    examinations: vitest.fn().mockResolvedValue([]),
    bodyRegions: vitest.fn().mockResolvedValue([]),
    weights: vitest.fn().mockResolvedValue([]),
    thresholds: vitest.fn().mockResolvedValue([]),
    alerts: vitest.fn().mockResolvedValue([]),
    horseCosts: vitest.fn().mockResolvedValue({
      month: '2026-10', closed: false, ownerId: null, ownerName: null, total: 0, horses: [], invoiceId: null,
      invoiceNumber: null, dailyFeeThrough: '2026-10-24',
    }),
  },
}));

/** The horse's detail panels open as popups: press the button that names one. */
async function openPanel(name: string) {
  await userEvent.click(await screen.findByRole('button', { name: new RegExp(`^${name}`) }));
}

const base: Horse = {
  id: 9, name: 'Sao Mai', registrationNumber: 'VN-9', sex: 'FEMALE', birthDate: '2021-04-12', coatColour: null,
  breed: null, owner: { id: 3, displayName: 'Chủ ngựa B' }, archived: false, archiveReason: null, archiveNote: null,
  archivedAt: null, photoVersion: null,
  assignedTrainer: { id: 1, displayName: 'Test HEAD_TRAINER', active: true }, mainTrainer: { id: 1, displayName: 'Test HEAD_TRAINER', active: true }, temporaryTrainer: null, canWrite: true,
};

beforeEach(() => {
  vitest.mocked(api.pedigree).mockResolvedValue([]);
  vitest.mocked(api.horseHealth).mockResolvedValue({
    horseId: 9, horseName: 'Sao Mai', registrationNumber: 'VN-9', status: 'ELIGIBLE', lock: null, activity: 'IDLE',
    readiness: 'READY', newUnexamined: false, history: [],
  });
  vitest.mocked(api.readiness).mockResolvedValue({ horseId: 9, readiness: 'READY' });
  vitest.mocked(api.plansOf).mockResolvedValue(pageOf([]));
  vitest.mocked(api.schedule).mockResolvedValue(pageOf([]));
  vitest.mocked(api.examinations).mockResolvedValue(pageOf([]));
  vitest.mocked(api.weights).mockResolvedValue([]);
  vitest.mocked(api.thresholds).mockResolvedValue([]);
  vitest.mocked(api.alerts).mockResolvedValue(pageOf([]));
});

describe('archive and restore (task 7.6, BR-007)', () => {
  test('AC-013 an archived horse is shown read-only to its owner', async () => {
    vitest.mocked(api.horse).mockResolvedValue({ ...base, archived: true, archiveReason: 'RETIRED' });
    renderAs('HORSE_OWNER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    expect(await screen.findByText(/Ngựa đã ngừng quản lý: hồ sơ chỉ xem./)).toHaveTextContent('Giải nghệ');
    expect(screen.queryByRole('link', { name: 'Sửa hồ sơ' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sửa dòng dõi' })).not.toBeInTheDocument();
  });

  test('AC-015 archiving without a reason shows the refusal', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    vitest.mocked(api.archiveHorse).mockRejectedValue(
      new ApiError(400, 'ARCHIVE_REASON_REQUIRED', 'Bắt buộc chọn lý do.', { reason: 'Bắt buộc chọn lý do.' }),
    );
    renderAs('CLUB_MANAGER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    await openPanel('Ngừng quản lý');
    await userEvent.click(await screen.findByRole('button', { name: 'Ngừng quản lý' }));
    expect(api.archiveHorse).toHaveBeenCalledWith(9, null, '');
    expect(await screen.findByRole('alert')).toHaveTextContent('Bắt buộc chọn lý do.');
  });

  test('Club Manager archives with a reason and can restore', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    vitest.mocked(api.archiveHorse).mockResolvedValue({ ...base, archived: true, archiveReason: 'SOLD' });
    vitest.mocked(api.restoreHorse).mockResolvedValue(base);
    renderAs('CLUB_MANAGER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    await openPanel('Ngừng quản lý');
    await userEvent.selectOptions(await screen.findByLabelText(/^Lý do/), 'SOLD');
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Ngừng quản lý' }));
    expect(api.archiveHorse).toHaveBeenCalledWith(9, 'SOLD', '');
    await userEvent.click(await screen.findByRole('button', { name: 'Khôi phục' }));
    expect(api.restoreHorse).toHaveBeenCalledWith(9);
  });

  test('a Veterinarian sees no archive controls', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    renderAs('VETERINARIAN', <HorseDetailPage />, '/horses/:id', '/horses/9');
    await screen.findByText('VN-9');
    expect(screen.queryByRole('button', { name: 'Ngừng quản lý' })).not.toBeInTheDocument();
  });

  test('the injury-marks link is shown to the Veterinarian and Head Trainer only (AC-142 to AC-145)', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    for (const [role, shown] of [
      ['VETERINARIAN', true],
      ['HEAD_TRAINER', true],
      ['HORSE_OWNER', false],
      ['CLUB_MANAGER', false],
    ] as const) {
      const { unmount } = renderAs(role, <HorseDetailPage />, '/horses/:id', '/horses/9');
      await screen.findByText('VN-9');
      const link = screen.queryByRole('link', { name: /^Dấu chấn thương trên mô hình 3D/ });
      if (shown) expect(link).toHaveAttribute('href', '/horses/9/injuries');
      else expect(link).not.toBeInTheDocument();
      unmount();
    }
  });
});

describe('pedigree (critic round 3)', () => {
  // The service lists all six positions, named or not.
  const six = (named?: string) =>
    PEDIGREE_POSITIONS.map((position) => ({ position, horse: null, name: position === 'SIRE' ? (named ?? null) : null }));

  test('six unknown ancestors show one empty state with the action, not six "Chưa rõ" rows', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    vitest.mocked(api.pedigree).mockResolvedValue(six());
    renderAs('CLUB_MANAGER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    await openPanel('Dòng dõi');
    expect(await screen.findByText('Chưa có dòng dõi')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sửa dòng dõi' })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Dòng dõi' })).not.toBeInTheDocument();
  });

  test('one known ancestor shows the table with the relation per row', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    vitest.mocked(api.pedigree).mockResolvedValue(six('Gió Bắc'));
    renderAs('CLUB_MANAGER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    await openPanel('Dòng dõi');
    const table = await screen.findByRole('table', { name: 'Dòng dõi' });
    expect(table).toHaveTextContent('Gió Bắc');
    expect(screen.queryByText('Chưa có dòng dõi')).not.toBeInTheDocument();
  });
});

describe('who trains the horse (BR-152 to BR-156, AC-283 to AC-290)', () => {
  const other = { id: 2, displayName: 'Trần Quốc Hoàng', active: true };

  test('AC-290 a trainer who is not assigned reads the horse with a notice naming the assigned one', async () => {
    vitest.mocked(api.horse).mockResolvedValue({ ...base, canWrite: false, assignedTrainer: other, mainTrainer: other });
    renderAs('HEAD_TRAINER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    expect(await screen.findByText('Ngựa này do HLV Trần Quốc Hoàng phụ trách. Bạn chỉ có quyền xem.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Tạo giáo án' })).not.toBeInTheDocument();
  });

  test('AC-289 the assigned trainer sees no notice', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    renderAs('HEAD_TRAINER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    await screen.findByRole('heading', { level: 1, name: 'Sao Mai' });
    expect(screen.queryByText(/Bạn chỉ có quyền xem/)).not.toBeInTheDocument();
  });

  test('AC-285 the Club Manager hands the horse over to another trainer', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    vitest.mocked(api.options).mockResolvedValue(pageOf([{ id: 2, label: 'Trần Quốc Hoàng', hint: 'htl-hoang' }]));
    vitest.mocked(api.changeTrainer).mockResolvedValue({ ...base, assignedTrainer: other, mainTrainer: other, canWrite: false });
    renderAs('CLUB_MANAGER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    await openPanel('HLV phụ trách');
    await userEvent.click(await screen.findByRole('button', { name: 'Đổi HLV' }));
    const dialog = await screen.findByRole('dialog', { name: 'Đổi HLV phụ trách' });
    await userEvent.click(within(dialog).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: /Trần Quốc Hoàng/ }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(api.changeTrainer).toHaveBeenCalledWith(9, 2);
    await vitest.waitFor(() => expect(screen.queryByRole('dialog', { name: 'Đổi HLV phụ trách' })).not.toBeInTheDocument());
  });

  test('AC-286 a loan needs a trainer and a return date, and can end early', async () => {
    const loan = { id: 2, displayName: 'Trần Quốc Hoàng', until: '2026-11-03' };
    vitest.mocked(api.horse).mockResolvedValue({ ...base, temporaryTrainer: loan, assignedTrainer: other });
    vitest.mocked(api.endLoan).mockResolvedValue(base);
    renderAs('CLUB_MANAGER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    // The loan shows on the horse's card and in the Club Manager's trainer panel.
    await openPanel('HLV phụ trách');
    expect(await screen.findAllByText(/Trần Quốc Hoàng \(phụ trách tạm đến trước ngày 2026-11-03\)/)).not.toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Trả ngựa về HLV chính' }));
    expect(api.endLoan).toHaveBeenCalledWith(9);
  });

  test('AC-286 a loan return date must be after today and is refused on the field, nothing is sent', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    vitest.mocked(api.options).mockResolvedValue(pageOf([{ id: 2, label: 'Trần Quốc Hoàng', hint: 'htl-hoang' }]));
    vitest.mocked(api.lendTrainer).mockReset();
    renderAs('CLUB_MANAGER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    await openPanel('HLV phụ trách');
    await userEvent.click(await screen.findByRole('button', { name: 'Giao tạm' }));
    const dialog = await screen.findByRole('dialog', { name: 'Giao ngựa cho HLV khác tạm thời' });
    await userEvent.click(within(dialog).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: /Trần Quốc Hoàng/ }));
    const until = within(dialog).getByLabelText(/^Trả ngựa vào ngày/);
    expect(until).toHaveAttribute('min', today(1));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(until).toHaveAccessibleDescription('Bắt buộc nhập.');
    await userEvent.type(until, today());
    await userEvent.click(within(dialog).getByRole('button', { name: 'Lưu' }));
    expect(until).toHaveAccessibleDescription(/không được trước/);
    expect(api.lendTrainer).not.toHaveBeenCalled();
  });

  test('AC-288 other roles have no trainer controls', async () => {
    vitest.mocked(api.horse).mockResolvedValue(base);
    renderAs('HEAD_TRAINER', <HorseDetailPage />, '/horses/:id', '/horses/9');
    await screen.findByRole('heading', { level: 1, name: 'Sao Mai' });
    expect(screen.queryByRole('button', { name: 'Đổi HLV' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Giao tạm' })).not.toBeInTheDocument();
  });
});
