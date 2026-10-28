import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import InjuryPage from './InjuryPage';
import { renderAs } from '../test/render';
import { pageOf } from '../test/page';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { InjuryMark } from '../api/types';
import type { ViewerProps } from './HorseViewer';

// jsdom has no WebGL: the 3D canvas is replaced by a stub that shows what it was asked to draw (TECH-051).
const webGl = vitest.hoisted(() => ({ supported: true, crash: false }));
vitest.mock('./injuryModel', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./injuryModel')>()),
  supportsWebGl: () => webGl.supported,
}));
vitest.mock('./HorseViewer', () => ({
  default: (p: ViewerProps) => {
    if (webGl.crash) throw new Error('WebGL context lost');
    return (
      <div data-testid="viewer">
        <span>selected:{p.selected ?? '-'}</span>
        <span>layers:{Object.entries(p.layers).filter(([, on]) => on).map(([l]) => l).join(',')}</span>
        <span>markers:{p.marks.map((m) => `${m.regionCode}/${m.status}`).join(',')}</span>
        <button type="button" onClick={() => p.onSelect('RH_HOCK')}>pick-hock</button>
      </div>
    );
  },
}));
vitest.mock('../api/api', () => ({
  api: {
    horse: vitest.fn(),
    bodyRegions: vitest.fn(),
    injuryMarks: vitest.fn(),
    examinations: vitest.fn(),
    createInjuryMark: vitest.fn(),
    changeMarkStatus: vitest.fn(),
    cancelMark: vitest.fn(),
  },
}));

const regions = [
  { code: 'LF_FETLOCK', vi: 'Khớp bàn trước trái', en: 'Left fore fetlock' },
  { code: 'RH_HOCK', vi: 'Khớp cổ chân sau (hock) phải', en: 'Right hind hock' },
];
const exam = { id: 31, horseId: 9, examDate: '2026-10-21', diagnosis: 'Viêm khớp bàn' };
const mark = (over: Partial<InjuryMark> = {}): InjuryMark => ({
  id: 1, horseId: 9, regionCode: 'LF_FETLOCK', status: 'NEW', examinationId: 31, examinationDate: '2026-10-21',
  examinationDiagnosis: 'Viêm khớp bàn', createdBy: 'BS Lan', createdAt: '2026-10-21T02:00:00Z',
  history: [{ from: null, to: 'NEW', date: '2026-10-21', note: null, by: 'BS Lan', at: '2026-10-21T02:00:00Z' }],
  ...over,
});

/** The service answers the standing marks and the cancelled ones as separate lists (`cancelled` filter). */
function mockMarks(marks: InjuryMark[]) {
  vitest.mocked(api.injuryMarks).mockImplementation(async (_id, query) =>
    pageOf(marks.filter((m) => (m.status === 'CANCELLED') === Boolean(query?.cancelled))),
  );
}

function setup(marks: InjuryMark[], archived = false) {
  vitest.mocked(api.horse).mockResolvedValue({ id: 9, name: 'Sao Mai', archived } as never);
  vitest.mocked(api.bodyRegions).mockResolvedValue(regions);
  mockMarks(marks);
  vitest.mocked(api.examinations).mockResolvedValue(pageOf([exam]) as never);
}
const open = (role: Parameters<typeof renderAs>[0]) =>
  renderAs(role, <InjuryPage />, '/horses/:id/injuries', '/horses/9/injuries');

beforeEach(() => {
  webGl.supported = true;
  webGl.crash = false;
});

describe('injury marks page (tasks 4.5, 4.6)', () => {
  test('AC-126 the Veterinarian marks the selected region and sees it in the list', async () => {
    setup([]);
    vitest.mocked(api.createInjuryMark).mockResolvedValue(mark());
    open('VETERINARIAN');
    await userEvent.click(await screen.findByRole('combobox', { name: 'Vùng cơ thể' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Khớp bàn trước trái' }));
    expect(screen.getByText('selected:LF_FETLOCK')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Hồ sơ khám'), '31');
    mockMarks([mark()]);
    await userEvent.click(screen.getByRole('button', { name: 'Đánh dấu chấn thương' }));
    expect(api.createInjuryMark).toHaveBeenCalledWith(9, 'LF_FETLOCK', 31);
    const item = await screen.findByRole('listitem', { name: 'Khớp bàn trước trái' });
    expect(item).toHaveTextContent('Mới');
    expect(item).toHaveTextContent('2026-10-21');
    expect(screen.getByText('markers:LF_FETLOCK/NEW')).toBeInTheDocument();
  });

  test('AC-126 picking a part on the model selects the same region as the list', async () => {
    setup([]);
    open('VETERINARIAN');
    await userEvent.click(await screen.findByRole('button', { name: 'pick-hock' }));
    expect(screen.getByRole('combobox', { name: 'Vùng cơ thể' })).toHaveTextContent('Khớp cổ chân sau (hock) phải');
  });

  test('AC-127 layers are switched one by one and the mark stays visible', async () => {
    setup([mark()]);
    open('VETERINARIAN');
    const layers = await screen.findByRole('group', { name: 'Lớp hiển thị' });
    expect(screen.getByText('layers:skin,muscle,skeleton')).toBeInTheDocument();
    for (const [name, expected] of [
      ['Da', 'layers:muscle,skeleton'],
      ['Cơ', 'layers:skeleton'],
      ['Xương', 'layers:'],
    ] as const) {
      const button = within(layers).getByRole('button', { name });
      await userEvent.click(button);
      expect(button).toHaveAttribute('aria-pressed', 'false');
      expect(screen.getByText(expected)).toBeInTheDocument();
      expect(screen.getByText('markers:LF_FETLOCK/NEW')).toBeInTheDocument();
      expect(screen.getByRole('listitem', { name: 'Khớp bàn trước trái' })).toBeInTheDocument();
    }
    await userEvent.click(within(layers).getByRole('button', { name: 'Da' }));
    expect(within(layers).getByRole('button', { name: 'Da' })).toHaveAttribute('aria-pressed', 'true');
  });

  test('AC-129 a missing examination is named', async () => {
    setup([]);
    vitest.mocked(api.createInjuryMark).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Dấu chấn thương chưa hợp lệ.', { examinationId: 'Chọn hồ sơ khám của ngựa này.' }),
    );
    open('VETERINARIAN');
    await userEvent.click(await screen.findByRole('combobox', { name: 'Vùng cơ thể' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Khớp bàn trước trái' }));
    await userEvent.click(screen.getByRole('button', { name: 'Đánh dấu chấn thương' }));
    expect(api.createInjuryMark).toHaveBeenCalledWith(9, 'LF_FETLOCK', null);
    expect(await screen.findByText('Chọn hồ sơ khám của ngựa này.')).toBeInTheDocument();
  });

  test('AC-130 a region error is shown at the region field', async () => {
    setup([]);
    vitest.mocked(api.createInjuryMark).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Dấu chấn thương chưa hợp lệ.', { regionCode: 'Chọn một vùng cơ thể có trong danh mục.' }),
    );
    open('VETERINARIAN');
    await userEvent.selectOptions(await screen.findByLabelText('Hồ sơ khám'), '31');
    await userEvent.click(screen.getByRole('button', { name: 'Đánh dấu chấn thương' }));
    expect(api.createInjuryMark).toHaveBeenCalledWith(9, '', 31);
    expect(await screen.findByText('Chọn một vùng cơ thể có trong danh mục.')).toBeInTheDocument();
  });

  test('AC-135 a status change with a note appears in the history', async () => {
    setup([mark()]);
    const recovering = mark({
      status: 'RECOVERING',
      history: [
        ...mark().history,
        { from: 'NEW', to: 'RECOVERING', date: '2026-10-22', note: 'Đi bộ nhẹ', by: 'BS Lan', at: '2026-10-22T02:00:00Z' },
      ],
    });
    vitest.mocked(api.changeMarkStatus).mockResolvedValue(recovering);
    open('VETERINARIAN');
    const item = await screen.findByRole('listitem', { name: 'Khớp bàn trước trái' });
    await userEvent.selectOptions(within(item).getByLabelText('Trạng thái mới'), 'RECOVERING');
    await userEvent.type(within(item).getByLabelText('Ghi chú'), 'Đi bộ nhẹ');
    mockMarks([recovering]);
    await userEvent.click(within(item).getByRole('button', { name: 'Cập nhật trạng thái' }));
    expect(api.changeMarkStatus).toHaveBeenCalledWith(1, 'RECOVERING', 'Đi bộ nhẹ');
    expect(await screen.findByText(/2026-10-22: Đang hồi phục — Bác sĩ BS Lan — Đi bộ nhẹ/)).toBeInTheDocument();
  });

  test('AC-138 a recovered mark offers no reopening and suggests a new mark; a server refusal is shown', async () => {
    setup([mark({ status: 'RECOVERED' }), mark({ id: 2, regionCode: 'RH_HOCK' })]);
    vitest.mocked(api.changeMarkStatus).mockRejectedValue(
      new ApiError(409, 'INVALID_MARK_TRANSITION', 'Dấu đã hồi phục không mở lại được. Nếu là chấn thương mới, hãy tạo dấu mới.'),
    );
    open('VETERINARIAN');
    const recovered = await screen.findByRole('listitem', { name: 'Khớp bàn trước trái' });
    expect(within(recovered).queryByLabelText('Trạng thái mới')).not.toBeInTheDocument();
    expect(recovered).toHaveTextContent('hãy tạo dấu mới');
    const other = screen.getByRole('listitem', { name: 'Khớp cổ chân sau (hock) phải' });
    await userEvent.selectOptions(within(other).getByLabelText('Trạng thái mới'), 'RECOVERED');
    await userEvent.click(within(other).getByRole('button', { name: 'Cập nhật trạng thái' }));
    expect(await within(other).findByRole('alert')).toHaveTextContent('hãy tạo dấu mới');
  });

  test('AC-174 a cancelled mark leaves the model and the active list but keeps its reason in history', async () => {
    setup([mark(), mark({ id: 2, regionCode: 'RH_HOCK' })]);
    const cancelled = mark({
      status: 'CANCELLED',
      history: [
        ...mark().history,
        { from: 'NEW', to: 'CANCELLED', date: '2026-10-22', note: 'chọn nhầm chân', by: 'BS Lan', at: '2026-10-22T02:00:00Z' },
      ],
    });
    vitest.mocked(api.cancelMark).mockResolvedValue(cancelled);
    open('VETERINARIAN');
    const item = await screen.findByRole('listitem', { name: 'Khớp bàn trước trái' });
    await userEvent.type(within(item).getByLabelText('Lý do hủy'), 'chọn nhầm chân');
    mockMarks([cancelled, mark({ id: 2, regionCode: 'RH_HOCK' })]);
    await userEvent.click(within(item).getByRole('button', { name: 'Hủy dấu nhập nhầm' }));
    expect(api.cancelMark).toHaveBeenCalledWith(1, 'chọn nhầm chân');
    expect(await screen.findByText('markers:RH_HOCK/NEW')).toBeInTheDocument();
    const active = screen.getByRole('region', { name: 'Danh sách dấu' });
    expect(within(active).queryByRole('listitem', { name: 'Khớp bàn trước trái' })).not.toBeInTheDocument();
    const history = screen.getByRole('region', { name: 'Dấu đã hủy (nhập nhầm)' });
    expect(history).toHaveTextContent('Khớp bàn trước trái');
    expect(history).toHaveTextContent('chọn nhầm chân');
  });

  test('AC-175 a missing reason is named; only Mới marks offer cancelling', async () => {
    setup([mark(), mark({ id: 2, regionCode: 'RH_HOCK', status: 'RECOVERING' })]);
    vitest.mocked(api.cancelMark).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Thiếu lý do.', { reason: 'Bắt buộc nhập lý do hủy.' }),
    );
    open('VETERINARIAN');
    const item = await screen.findByRole('listitem', { name: 'Khớp bàn trước trái' });
    await userEvent.click(within(item).getByRole('button', { name: 'Hủy dấu nhập nhầm' }));
    expect(await within(item).findByText('Bắt buộc nhập lý do hủy.')).toBeInTheDocument();
    const recovering = screen.getByRole('listitem', { name: 'Khớp cổ chân sau (hock) phải' });
    expect(within(recovering).queryByRole('button', { name: 'Hủy dấu nhập nhầm' })).not.toBeInTheDocument();
  });

  test('AC-142 the Head Trainer sees marks and history with no forms', async () => {
    setup([mark()]);
    open('HEAD_TRAINER');
    const item = await screen.findByRole('listitem', { name: 'Khớp bàn trước trái' });
    expect(item).toHaveTextContent('Mới');
    expect(within(item).getByText('Lịch sử')).toBeInTheDocument();
    expect(screen.getByTestId('viewer')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Đánh dấu chấn thương' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Trạng thái mới')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Hủy dấu nhập nhầm' })).not.toBeInTheDocument();
  });

  test('AC-146 a horse without marks shows the empty message', async () => {
    setup([]);
    open('HEAD_TRAINER');
    expect(await screen.findByText('Chưa có dấu chấn thương nào.')).toBeInTheDocument();
  });

  test('AC-134 without WebGL a notice is shown and the list stays', async () => {
    webGl.supported = false;
    setup([mark()]);
    open('VETERINARIAN');
    expect(await screen.findByText(/không hiển thị được mô hình 3D/)).toBeInTheDocument();
    expect(await screen.findByRole('listitem', { name: 'Khớp bàn trước trái' })).toHaveTextContent('Viêm khớp bàn');
    expect(screen.queryByTestId('viewer')).not.toBeInTheDocument();
  });

  test('AC-134 when the 3D view fails to start the list stays', async () => {
    webGl.crash = true;
    const quiet = vitest.spyOn(console, 'error').mockImplementation(() => {});
    setup([mark()]);
    open('VETERINARIAN');
    expect(await screen.findByText(/Không khởi động được mô hình 3D/)).toBeInTheDocument();
    expect(await screen.findByRole('listitem', { name: 'Khớp bàn trước trái' })).toHaveTextContent('Mới');
    quiet.mockRestore();
  });

  test.each([
    ['AC-143', 'HORSE_OWNER'],
    ['AC-144', 'CLUB_MANAGER'],
    ['AC-145', 'GROOM'],
  ] as const)('%s %s cannot open injury marks', async (_ac, role) => {
    setup([mark()]);
    open(role);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
    await waitFor(() => expect(api.injuryMarks).not.toHaveBeenCalled());
  });
});

describe('desktop-only 3D view (NFR-013, BR-102; verifier finding)', () => {
  const originalMatchMedia = window.matchMedia;
  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  test('on a phone the 3D view is replaced by a note and the marks list is complete', async () => {
    window.matchMedia = ((query: string) => ({ ...originalMatchMedia(query), matches: false })) as typeof window.matchMedia;
    setup([mark()]);
    open('VETERINARIAN');
    expect(await screen.findByRole('listitem', { name: 'Khớp bàn trước trái' })).toHaveTextContent('Mới');
    expect(screen.getByText(/Mô hình 3D chỉ hiển thị trên máy tính/)).toBeInTheDocument();
    expect(screen.queryByTestId('viewer')).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Lớp hiển thị' })).not.toBeInTheDocument();
  });

  test('on a desktop the 3D view is shown beside the same list', async () => {
    setup([mark()]);
    open('VETERINARIAN');
    expect(await screen.findByTestId('viewer')).toBeInTheDocument();
    expect(screen.getByRole('listitem', { name: 'Khớp bàn trước trái' })).toBeInTheDocument();
    expect(screen.queryByText(/Mô hình 3D chỉ hiển thị trên máy tính/)).not.toBeInTheDocument();
  });
});

