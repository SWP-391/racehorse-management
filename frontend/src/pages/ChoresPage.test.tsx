import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import ChoresPage from './ChoresPage';
import { renderAs, renderWithout } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { ChoreDay, ChoreRangeHorse, ChoreState } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: {
    chores: vitest.fn(),
    markChore: vitest.fn(),
    cancelChore: vitest.fn(),
    choreHistory: vitest.fn(),
    rationsToday: vitest.fn(),
    choreRange: vitest.fn(),
    options: vitest.fn(),
    backfillChore: vitest.fn(),
  },
}));

const open = (chore: ChoreState['chore']): ChoreState => ({ chore, done: false, entryId: null, groomName: null, markedAt: null, groomId: null });
// The signed-in test user has id 1: this mark is theirs (BR-170).
const feedingDone: ChoreState = { chore: 'FEEDING', done: true, entryId: 31, groomName: 'Minh', markedAt: '2026-10-22T00:05:00Z', groomId: 1 };
const washingByOther: ChoreState = { chore: 'WASHING', done: true, entryId: 33, groomName: 'Bình', markedAt: '2026-10-22T00:06:00Z', groomId: 2 };

const day = (over: Partial<ChoreDay> = {}): ChoreDay => ({
  date: '2026-10-22',
  today: '2026-10-22',
  page: 0,
  size: 20,
  total: 1,
  horses: [
    {
      horseId: 9,
      horseName: 'Sao Mai',
      registrationNumber: 'VN-001',
      stall: { areaId: 1, area: 'Khu A', slot: 3 },
      chores: [feedingDone, open('STALL_CLEANING'), open('WASHING'), open('ICE_SOAK')],
    },
    {
      horseId: 10,
      horseName: 'Gió Núi',
      registrationNumber: 'VN-002',
      stall: null,
      chores: [open('FEEDING'), open('STALL_CLEANING'), open('WASHING'), open('ICE_SOAK')],
    },
  ],
  ...over,
});

beforeEach(() => {
  vitest.mocked(api.chores).mockReset().mockResolvedValue(day());
  vitest.mocked(api.markChore).mockReset().mockResolvedValue(open('FEEDING'));
  vitest.mocked(api.cancelChore).mockReset().mockResolvedValue(open('FEEDING'));
  vitest.mocked(api.rationsToday).mockReset().mockResolvedValue([]);
  vitest.mocked(api.choreRange).mockReset().mockResolvedValue(pageOf([]));
  vitest.mocked(api.options).mockReset().mockResolvedValue(pageOf([{ id: 9, label: 'Sao Mai', hint: 'VN-001' }]));
  vitest.mocked(api.backfillChore).mockReset().mockResolvedValue(open('FEEDING'));
});

describe('feeding takes the ration out of stock (BR-173, BR-174)', () => {
  test('AC-346 a refused mark names the short items and stays on screen', async () => {
    vitest.mocked(api.markChore).mockRejectedValue(
      new ApiError(409, 'INSUFFICIENT_STOCK', 'Không đủ vật tư trong kho để cho ăn: "Cỏ khô" (thiếu 2 kg).'),
    );
    renderAs('GROOM', <ChoresPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đánh dấu Cho ăn cho Gió Núi' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('"Cỏ khô" (thiếu 2 kg)');
  });

  test('AC-349 and AC-350 the Club Manager fills a past day; a reason is mandatory', async () => {
    renderAs('CLUB_MANAGER', <ChoresPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Bổ sung ngày đã qua' }));
    const dialog = await screen.findByRole('dialog', { name: 'Bổ sung việc của ngày đã qua' });
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Ngựa' }));
    await userEvent.click(await screen.findByRole('option', { name: /Sao Mai/ }));
    fireEvent.change(within(dialog).getByLabelText('Ngày'), { target: { value: '2026-10-20' } });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Bổ sung' }));
    expect(within(dialog).getByText('Bắt buộc nhập lý do bổ sung.')).toBeInTheDocument();
    expect(api.backfillChore).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText('Lý do bổ sung'), 'quên đánh dấu');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Bổ sung' }));
    expect(api.backfillChore).toHaveBeenCalledWith({ horseId: 9, chore: 'FEEDING', date: '2026-10-20', reason: 'quên đánh dấu' });
  });

  test('AC-351 a Groom has no back-fill button', async () => {
    renderAs('GROOM', <ChoresPage />);
    await screen.findByRole('table');
    expect(screen.queryByRole('button', { name: 'Bổ sung ngày đã qua' })).not.toBeInTheDocument();
  });
});

describe('ration beside the chore "Cho ăn" (BR-144)', () => {
  test('AC-260 today\'s meals appear beside Cho ăn, a horse without a ration says so', async () => {
    vitest.mocked(api.rationsToday).mockResolvedValue([
      {
        horseId: 9,
        meals: [
          {
            name: 'Bữa sáng',
            time: '06:00',
            items: [{ type: 'GRAIN', supplyItemId: 1, supplyItemName: 'Yến mạch', unit: 'kg', active: true, quantity: 2.5 }],
          },
        ],
      },
    ]);
    renderAs('GROOM', <ChoresPage />);
    const table = await screen.findByRole('table', { name: 'Việc chăm sóc hằng ngày' });
    expect(await within(table).findByText(/06:00 Bữa sáng: Yến mạch 2.5 kg/)).toBeInTheDocument();
    expect(within(table).getByText('Chưa có khẩu phần')).toBeInTheDocument();
  });

  test('AC-261 marking Cho ăn is not blocked by a missing ration', async () => {
    renderAs('GROOM', <ChoresPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đánh dấu Cho ăn cho Gió Núi' }));
    expect(api.markChore).toHaveBeenCalledWith(10, 'FEEDING', '2026-10-22');
  });
});

describe('chores screen (task 6.2)', () => {
  test('AC-208 four chores per horse, done and not done with text', async () => {
    renderAs('GROOM', <ChoresPage />);
    const table = await screen.findByRole('table', { name: 'Việc chăm sóc hằng ngày' });
    for (const name of ['Cho ăn', 'Vệ sinh chuồng', 'Tắm rửa', 'Ngâm chân nước đá']) {
      expect(within(table).getByRole('columnheader', { name })).toBeInTheDocument();
    }
    expect(within(table).getAllByText('Đã xong')).toHaveLength(1);
    // An unfinished chore is a single "Đánh dấu xong" button for the Groom (no badge beside it).
    expect(within(table).getAllByRole('button', { name: /^Đánh dấu .* cho / })).toHaveLength(7);
    expect(within(table).queryByText('Chưa xong')).not.toBeInTheDocument();
  });

  test('read-only roles still see a "Chưa xong" badge for an unfinished chore', async () => {
    renderAs('HEAD_TRAINER', <ChoresPage />);
    const table = await screen.findByRole('table', { name: 'Việc chăm sóc hằng ngày' });
    expect(within(table).getAllByText('Chưa xong')).toHaveLength(7);
  });

  test('AC-207 a Groom marks a chore with one tap, then the list reloads', async () => {
    renderAs('GROOM', <ChoresPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đánh dấu Vệ sinh chuồng cho Sao Mai' }));
    expect(api.markChore).toHaveBeenCalledWith(9, 'STALL_CLEANING', '2026-10-22');
    expect(api.chores).toHaveBeenCalledTimes(2);
  });

  test('AC-209 a refused mark is shown and the list reloads', async () => {
    vitest.mocked(api.markChore).mockRejectedValue(new ApiError(409, 'CHORE_ALREADY_DONE', 'Việc này đã được đánh dấu.'));
    renderAs('GROOM', <ChoresPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Đánh dấu Cho ăn cho Gió Núi' }));
    await vitest.waitFor(() => expect(api.chores).toHaveBeenCalledTimes(2));
  });

  test('AC-211 and AC-212 cancelling needs a reason', async () => {
    renderAs('GROOM', <ChoresPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Hủy đánh dấu Cho ăn của Sao Mai' }));
    const dialog = await screen.findByRole('dialog', { name: 'Hủy đánh dấu đã làm' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Hủy đánh dấu' }));
    expect(within(dialog).getByText('Nhập lý do hủy đánh dấu.')).toBeInTheDocument();
    expect(api.cancelChore).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText('Lý do hủy'), 'nhầm ngựa');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Hủy đánh dấu' }));
    expect(api.cancelChore).toHaveBeenCalledWith(31, 'nhầm ngựa');
    await vitest.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  test('AC-327 and AC-328 a Groom cancels only a mark of their own', async () => {
    vitest.mocked(api.chores).mockResolvedValue(
      day({
        horses: [
          {
            horseId: 9,
            horseName: 'Sao Mai',
            registrationNumber: 'VN-001',
            stall: null,
            chores: [feedingDone, open('STALL_CLEANING'), washingByOther, open('ICE_SOAK')],
          },
        ],
      }),
    );
    renderAs('GROOM', <ChoresPage />);
    expect(await screen.findByRole('button', { name: 'Hủy đánh dấu Cho ăn của Sao Mai' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Hủy đánh dấu Tắm rửa của Sao Mai' })).not.toBeInTheDocument();
  });

  test('AC-329 the Club Manager cancels any mark of the day, and only on today', async () => {
    vitest.mocked(api.chores).mockResolvedValue(
      day({
        horses: [
          {
            horseId: 9,
            horseName: 'Sao Mai',
            registrationNumber: 'VN-001',
            stall: null,
            chores: [feedingDone, open('STALL_CLEANING'), washingByOther, open('ICE_SOAK')],
          },
        ],
      }),
    );
    renderAs('CLUB_MANAGER', <ChoresPage />);
    expect(await screen.findByRole('button', { name: 'Hủy đánh dấu Cho ăn của Sao Mai' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Hủy đánh dấu Tắm rửa của Sao Mai' })).toBeInTheDocument();
    // The Club Manager does not mark: no "Đánh dấu" button anywhere.
    expect(screen.queryByRole('button', { name: /^Đánh dấu / })).not.toBeInTheDocument();
  });

  test('AC-210 a past day is read-only and says so', async () => {
    vitest.mocked(api.chores).mockResolvedValue(day({ date: '2026-10-21' }));
    renderAs('GROOM', <ChoresPage />);
    expect(await screen.findByText(/chỉ để xem/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Đánh dấu|Hủy đánh dấu/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Về hôm nay' })).toBeInTheDocument();
  });

  test('AC-214 and AC-215 other roles read who and when, with no controls', async () => {
    for (const role of ['HEAD_TRAINER', 'VETERINARIAN', 'CLUB_MANAGER'] as const) {
      const view = renderAs(role, <ChoresPage />);
      expect(await screen.findByText('Minh, 07:05')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Đánh dấu/ })).not.toBeInTheDocument();
      view.unmount();
    }
  });

  test('AC-216 the Owner sees done or not done and no Groom names', async () => {
    vitest.mocked(api.chores).mockResolvedValue(
      day({
        horses: [
          {
            horseId: 9,
            horseName: 'Sao Mai',
            registrationNumber: 'VN-001',
            stall: null,
            chores: [{ chore: 'FEEDING', done: true, entryId: null, groomName: null, markedAt: null, groomId: null }, open('WASHING')],
          },
        ],
      }),
    );
    renderAs('HORSE_OWNER', <ChoresPage />);
    await screen.findByRole('table');
    expect(screen.getByText('Đã xong')).toBeInTheDocument();
    expect(screen.queryByText(/Minh/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Đánh dấu/ })).not.toBeInTheDocument();
    // The day list is today only (PERM-036): no date input and no per-horse history of marks; the range view is read-only (AC-376).
    expect(screen.queryByLabelText('Ngày')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Lịch sử việc chăm sóc của/ })).not.toBeInTheDocument();
  });

  test('AC-211 the history shows a cancelled mark with its reason', async () => {
    vitest.mocked(api.choreHistory).mockResolvedValue(pageOf([
      { entryId: 32, date: '2026-10-22', chore: 'STALL_CLEANING', groomName: 'Minh', markedAt: '2026-10-22T00:10:00Z',
        cancelledAt: '2026-10-22T00:20:00Z', cancelReason: 'Nhầm ngựa', cancelledByName: 'Minh', groomId: 1 },
    ]));
    renderAs('HEAD_TRAINER', <ChoresPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Lịch sử việc chăm sóc của Sao Mai' }));
    expect(api.choreHistory).toHaveBeenCalledWith(9, 0);
    const list = await screen.findByRole('list', { name: 'Lịch sử chăm sóc' });
    expect(list).toHaveTextContent('Vệ sinh chuồng');
    expect(list).toHaveTextContent('Nhầm ngựa');
    expect(list).toHaveTextContent('Đã hủy');
  });

  test('states: error with retry, and empty', async () => {
    vitest.mocked(api.chores).mockRejectedValueOnce(new ApiError(500, 'X', 'Máy chủ không phản hồi.'));
    renderAs('GROOM', <ChoresPage />);
    expect(await screen.findByText('Máy chủ không phản hồi.')).toBeInTheDocument();
    vitest.mocked(api.chores).mockResolvedValue(day({ horses: [] }));
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Chưa có ngựa nào cần chăm sóc')).toBeInTheDocument();
  });

  test('a user without VIEW_CHORES gets the access-denied state and no request', () => {
    renderWithout('GROOM', ['VIEW_CHORES'], <ChoresPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
    expect(api.chores).not.toHaveBeenCalled();
  });
});

describe('chore history by date range (BR-188, AC-373 to AC-376)', () => {
  const doneOf = (...codes: ChoreState['chore'][]): ChoreState[] =>
    (['FEEDING', 'STALL_CLEANING', 'WASHING', 'ICE_SOAK'] as const).map((chore) => ({
      chore,
      done: codes.includes(chore),
      entryId: null,
      groomName: codes.includes(chore) ? 'Minh' : null,
      groomId: null,
      markedAt: null,
    }));
  const row = (days: { date: string; chores: ChoreState[] }[]): ChoreRangeHorse => ({
    horseId: 9, horseName: 'Sao Mai', registrationNumber: 'VN-001', days,
  });

  async function openHistory(role: 'GROOM' | 'HORSE_OWNER' | 'HEAD_TRAINER') {
    renderAs(role, <ChoresPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Lịch sử' }));
  }

  test('AC-373 the last 7 days are asked for by default and each day shows how many chores were done', async () => {
    vitest.mocked(api.choreRange).mockResolvedValue(
      pageOf([row([{ date: '2026-10-22', chores: doneOf('FEEDING', 'WASHING') }, { date: '2026-10-21', chores: doneOf() }])]),
    );
    await openHistory('HEAD_TRAINER');
    const days = await screen.findByRole('list', { name: 'Các ngày của Sao Mai' });
    expect(days).toHaveTextContent('22/10: 2/4');
    expect(days).toHaveTextContent('21/10: 0/4');
    expect(api.choreRange).toHaveBeenLastCalledWith(expect.objectContaining({ from: '2026-10-16', to: '2026-10-22' }));
  });

  test('AC-373 the history can be narrowed to one horse', async () => {
    vitest.mocked(api.choreRange).mockResolvedValue(pageOf([row([{ date: '2026-10-22', chores: doneOf('FEEDING') }])]));
    await openHistory('HEAD_TRAINER');
    await userEvent.click(await screen.findByRole('combobox', { name: 'Ngựa' }));
    await userEvent.click(await screen.findByRole('option', { name: /Sao Mai/ }));
    await vitest.waitFor(() => expect(api.choreRange).toHaveBeenLastCalledWith(expect.objectContaining({ horseId: 9 })));
  });

  test('AC-374 an end before the start is refused with a message and the earlier result stays', async () => {
    vitest.mocked(api.choreRange).mockResolvedValueOnce(pageOf([row([{ date: '2026-10-22', chores: doneOf('FEEDING') }])]));
    vitest.mocked(api.choreRange).mockRejectedValue(new ApiError(400, 'INVALID_RANGE', 'Ngày kết thúc phải sau ngày bắt đầu.', { to: 'Ngày kết thúc phải sau ngày bắt đầu.' }));
    await openHistory('HEAD_TRAINER');
    await screen.findByRole('list', { name: 'Các ngày của Sao Mai' });
    fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-10-01' } });
    expect((await screen.findAllByText('Ngày kết thúc phải sau ngày bắt đầu.')).length).toBeGreaterThan(0);
    expect(screen.getByRole('list', { name: 'Các ngày của Sao Mai' })).toBeInTheDocument();
  });

  test('AC-376 the Owner reads done or not done and no horse picker or Groom name', async () => {
    vitest.mocked(api.choreRange).mockResolvedValue(
      pageOf([row([{ date: '2026-10-22', chores: doneOf('FEEDING').map((c) => ({ ...c, groomName: null })) }])]),
    );
    await openHistory('HORSE_OWNER');
    const days = await screen.findByRole('list', { name: 'Các ngày của Sao Mai' });
    expect(days).toHaveTextContent('22/10: 1/4');
    expect(screen.queryByRole('combobox', { name: 'Ngựa' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Minh/)).not.toBeInTheDocument();
  });

  test('AC-375 the history has no mark or cancel button; today stays the only place to mark', async () => {
    vitest.mocked(api.choreRange).mockResolvedValue(pageOf([row([{ date: '2026-10-20', chores: doneOf() }])]));
    await openHistory('GROOM');
    await screen.findByRole('list', { name: 'Các ngày của Sao Mai' });
    expect(screen.queryByRole('button', { name: /Đánh dấu/ })).not.toBeInTheDocument();
  });
});

describe('"làm thay" (BR-169, AC-216)', () => {
  test('staff see the label on a mark made outside the barn hours, the Horse Owner never does', async () => {
    const onBehalf: ChoreState = { ...feedingDone, onBehalf: true };
    const horses = day().horses.map((h, i) => (i === 0 ? { ...h, chores: [onBehalf, ...h.chores.slice(1)] } : h));
    vitest.mocked(api.chores).mockResolvedValue(day({ horses }));
    renderAs('HEAD_TRAINER', <ChoresPage />);
    const table = await screen.findByRole('table', { name: 'Việc chăm sóc hằng ngày' });
    expect(within(table).getByText('làm thay')).toBeInTheDocument();
  });

  test('a mark made inside the hours carries no label', async () => {
    renderAs('HEAD_TRAINER', <ChoresPage />);
    const table = await screen.findByRole('table', { name: 'Việc chăm sóc hằng ngày' });
    expect(within(table).queryByText('làm thay')).not.toBeInTheDocument();
  });
});
