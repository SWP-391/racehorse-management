import { screen } from '@testing-library/react';
import { pageOf } from '../test/page';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import ExaminationPanel from './ExaminationPanel';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';

vitest.mock('../api/api', () => ({
  api: {
    examinations: vitest.fn(),
    recordExamination: vitest.fn(),
    weights: vitest.fn(),
    examFeeTypes: vitest.fn().mockResolvedValue([]),
    bodyRegions: vitest.fn().mockResolvedValue([
      { code: 'LF_FETLOCK', vi: 'Khớp bàn trước trái', en: 'Left fore fetlock' },
      { code: 'RH_HOCK', vi: 'Khớp cổ chân sau (hock) phải', en: 'Right hind hock' },
    ]),
  },
}));

const recorded = {
  id: 1, horseId: 9, examDate: '2026-10-20', diagnosis: 'Sưng khớp gối', bodyRegion: null, bodyDetail: null,
  bodyAreaLegacy: null, weightKg: 470, treatment: 'Chườm lạnh', prescription: 'Phenylbutazone', recordedBy: 'BS A',
};

describe('examinations and weights (task 5.1)', () => {
  test('AC-078 an empty diagnosis is refused and named', async () => {
    vitest.mocked(api.examinations).mockResolvedValue(pageOf([]));
    vitest.mocked(api.recordExamination).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Thông tin khám chưa hợp lệ.', { diagnosis: 'Bắt buộc nhập chẩn đoán.' }),
    );
    renderAs('VETERINARIAN', <ExaminationPanel horseId={9} archived={false} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Ghi nhận lần khám' }));
    expect(screen.getByLabelText(/^Chẩn đoán/)).toHaveAccessibleDescription(/Bắt buộc nhập chẩn đoán\./);
  });

  test('AC-117 the Head Trainer reads the details but has no recording form', async () => {
    vitest.mocked(api.examinations).mockResolvedValue(pageOf([{ ...recorded, bodyRegion: 'RH_HOCK' }]));
    renderAs('HEAD_TRAINER', <ExaminationPanel horseId={9} archived={false} />);
    expect(await screen.findByText('Sưng khớp gối')).toBeInTheDocument();
    expect(screen.getByText('Phenylbutazone')).toBeInTheDocument();
    expect(await screen.findByText('Khớp cổ chân sau (hock) phải')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ghi nhận lần khám' })).not.toBeInTheDocument();
  });

  test('AC-169 the body area is chosen from the catalog and shown with its detail', async () => {
    vitest.mocked(api.examinations).mockResolvedValue(pageOf([]));
    vitest.mocked(api.recordExamination).mockResolvedValue({ ...recorded, bodyRegion: 'LF_FETLOCK', bodyDetail: 'vết rách 3 cm' });
    renderAs('VETERINARIAN', <ExaminationPanel horseId={9} archived={false} />);
    await userEvent.type(screen.getByLabelText(/^Chẩn đoán/), 'Rách da');
    await userEvent.click(await screen.findByRole('combobox', { name: 'Vùng cơ thể' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Khớp bàn trước trái' }));
    await userEvent.type(screen.getByLabelText('Chi tiết vùng (không bắt buộc)'), 'vết rách 3 cm');
    vitest.mocked(api.examinations).mockResolvedValue(pageOf([{ ...recorded, bodyRegion: 'LF_FETLOCK', bodyDetail: 'vết rách 3 cm' }]));
    await userEvent.click(screen.getByRole('button', { name: 'Ghi nhận lần khám' }));
    expect(api.recordExamination).toHaveBeenCalledWith(9, expect.objectContaining({ bodyRegion: 'LF_FETLOCK', bodyDetail: 'vết rách 3 cm' }));
    expect(await screen.findByText('Khớp bàn trước trái — vết rách 3 cm')).toBeInTheDocument();
  });

  test('AC-171 an examination from before the catalog shows its free text unchanged', async () => {
    vitest.mocked(api.examinations).mockResolvedValue(pageOf([{ ...recorded, bodyAreaLegacy: 'left foreleg' }]));
    renderAs('HEAD_TRAINER', <ExaminationPanel horseId={9} archived={false} />);
    expect(await screen.findByText('left foreleg')).toBeInTheDocument();
  });

  test('AC-118 the Club Manager never receives examination details, only weights (PERM-021)', async () => {
    vitest.mocked(api.weights).mockResolvedValue([{ date: '2026-10-01', weightKg: 480 }]);
    renderAs('CLUB_MANAGER', <ExaminationPanel horseId={9} archived={false} />);
    expect(await screen.findByText('2026-10-01: 480 kg')).toBeInTheDocument();
    expect(screen.queryByText(/Chẩn đoán/)).not.toBeInTheDocument();
    expect(api.examinations).not.toHaveBeenCalled();
  });

  test('AC-107 the Owner sees the weights over time and nothing else', async () => {
    vitest.mocked(api.weights).mockResolvedValue([
      { date: '2026-09-01', weightKg: 470 },
      { date: '2026-09-15', weightKg: 475 },
      { date: '2026-10-01', weightKg: 480 },
    ]);
    renderAs('HORSE_OWNER', <ExaminationPanel horseId={9} archived={false} />);
    expect(await screen.findByText('2026-09-01: 470 kg')).toBeInTheDocument();
    expect(screen.getByText('2026-10-01: 480 kg')).toBeInTheDocument();
    expect(api.examinations).not.toHaveBeenCalled();
  });

  test('AC-360 the fee type is chosen from names only and sent with the examination', async () => {
    vitest.mocked(api.examinations).mockResolvedValue(pageOf([]));
    vitest.mocked(api.examFeeTypes).mockResolvedValue([{ id: 3, name: 'Khám tổng quát' }]);
    vitest.mocked(api.recordExamination).mockResolvedValue(recorded);
    renderAs('VETERINARIAN', <ExaminationPanel horseId={9} archived={false} />);
    const select = await screen.findByLabelText(/^Loại phí khám/);
    expect(screen.queryByText(/₫|đồng/)).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/^Chẩn đoán/), 'Khám định kỳ');
    await userEvent.selectOptions(select, 'Khám tổng quát');
    await userEvent.click(screen.getByRole('button', { name: 'Ghi nhận lần khám' }));
    expect(api.recordExamination).toHaveBeenCalledWith(9, expect.objectContaining({ feeTypeId: 3 }));
  });

  test('without priced fee types the field is not shown and nothing is required', async () => {
    vitest.mocked(api.examinations).mockResolvedValue(pageOf([]));
    vitest.mocked(api.examFeeTypes).mockResolvedValue([]);
    renderAs('VETERINARIAN', <ExaminationPanel horseId={9} archived={false} />);
    await screen.findByRole('button', { name: 'Ghi nhận lần khám' });
    expect(screen.queryByLabelText(/^Loại phí khám/)).not.toBeInTheDocument();
  });

  test('a missing fee type is named by the service message', async () => {
    vitest.mocked(api.examinations).mockResolvedValue(pageOf([]));
    vitest.mocked(api.examFeeTypes).mockResolvedValue([{ id: 3, name: 'Khám tổng quát' }]);
    vitest.mocked(api.recordExamination).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Thông tin khám chưa hợp lệ.', { feeTypeId: 'Chọn loại phí khám.' }),
    );
    renderAs('VETERINARIAN', <ExaminationPanel horseId={9} archived={false} />);
    await userEvent.type(await screen.findByLabelText(/^Chẩn đoán/), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Ghi nhận lần khám' }));
    expect(await screen.findByLabelText(/^Loại phí khám/)).toHaveAccessibleDescription(/Chọn loại phí khám\./);
  });
});
