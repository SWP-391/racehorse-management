import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import IncidentsPage from './IncidentsPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { Incident } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: {
    incidents: vitest.fn(),
    options: vitest.fn(),
    sendIncident: vitest.fn(),
    openIncident: vitest.fn(),
    closeIncident: vitest.fn(),
    bodyRegions: vitest.fn().mockResolvedValue([{ code: 'LF_HOOF', vi: 'Móng trước trái', en: 'Left fore hoof' }]),
  },
}));

const base: Incident = {
  id: 3, horseId: 9, horseName: 'Sao Mai', groom: 'Groom A', description: 'Ngựa bỏ ăn', bodyRegion: null, state: 'NEW',
  createdAt: '', closingNote: null, linkedExaminationId: null, photoIds: [],
};

beforeEach(() => {
  vitest.mocked(api.incidents).mockResolvedValue(pageOf([]));
  vitest.mocked(api.options).mockResolvedValue(pageOf([{ id: 9, label: 'Sao Mai', hint: 'VN-9' }]));
});

describe('incident reports (task 5.3)', () => {
  test('AC-106 the Groom picks from name and registration number only', async () => {
    renderAs('GROOM', <IncidentsPage />);
    await userEvent.click(await screen.findByRole('combobox', { name: 'Ngựa' }));
    // The options carry the name and the registration number and nothing else of the horse.
    expect(await screen.findByRole('option', { name: /Sao Mai.*VN-9/ })).toBeInTheDocument();
  });

  test('AC-105 a missing description is refused', async () => {
    vitest.mocked(api.sendIncident).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Báo cáo sự cố chưa hợp lệ.', { description: 'Bắt buộc nhập mô tả.' }),
    );
    renderAs('GROOM', <IncidentsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Gửi báo cáo' }));
    expect(screen.getByLabelText(/^Mô tả sự cố/)).toHaveAccessibleDescription(/Bắt buộc nhập mô tả./);
  });

  test('AC-120 photo refusals from the service are shown', async () => {
    vitest.mocked(api.sendIncident).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Báo cáo sự cố chưa hợp lệ.', {
        photos: 'Tối đa 3 ảnh cho mỗi báo cáo. Ảnh thứ 4 bị từ chối.',
        'photos[0]': 'Ảnh "lon.jpg" vượt quá 10 MB.',
      }),
    );
    renderAs('GROOM', <IncidentsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Gửi báo cáo' }));
    const alerts = await screen.findAllByRole('alert');
    expect(alerts.map((a) => a.textContent).join(' ')).toContain('Tối đa 3 ảnh');
    expect(alerts.map((a) => a.textContent).join(' ')).toContain('vượt quá 10 MB');
  });

  test('AC-103 the Veterinarian opens, then closes with a note', async () => {
    vitest.mocked(api.incidents).mockResolvedValue(pageOf([base]));
    vitest.mocked(api.openIncident).mockResolvedValue({ ...base, state: 'ACKNOWLEDGED' });
    vitest.mocked(api.closeIncident).mockResolvedValue({ ...base, state: 'HANDLED' });
    renderAs('VETERINARIAN', <IncidentsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Mở' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Mở' }));
    expect(api.openIncident).toHaveBeenCalledWith(3);
    await userEvent.click(screen.getAllByRole('button', { name: 'Đóng báo cáo' })[0]);
    await userEvent.type(screen.getByLabelText('Ghi chú xử lý'), 'Đã khám');
    await userEvent.click(screen.getAllByRole('button', { name: 'Đóng báo cáo' }).at(-1)!);
    expect(api.closeIncident).toHaveBeenCalledWith(3, 'Đã khám', null);
  });

  test('AC-172 the Groom may choose a catalog region; the Veterinarian sees the same name', async () => {
    vitest.mocked(api.sendIncident).mockResolvedValue({ ...base, description: 'Móng bị xước', bodyRegion: 'LF_HOOF' });
    renderAs('GROOM', <IncidentsPage />);
    await userEvent.click(await screen.findByRole('combobox', { name: 'Ngựa' }));
    await userEvent.click(await screen.findByRole('option', { name: /Sao Mai/ }));
    await userEvent.type(screen.getByLabelText(/^Mô tả sự cố/), 'Móng bị xước');
    await userEvent.click(await screen.findByRole('combobox', { name: 'Vùng cơ thể' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Móng trước trái' }));
    await userEvent.click(screen.getByRole('button', { name: 'Gửi báo cáo' }));
    expect(api.sendIncident).toHaveBeenCalledWith(9, 'Móng bị xước', [], 'LF_HOOF');

    vitest.mocked(api.incidents).mockResolvedValue(pageOf([{ ...base, description: 'Móng bị xước', bodyRegion: 'LF_HOOF' }]));
    renderAs('VETERINARIAN', <IncidentsPage />);
    expect(await screen.findByRole('cell', { name: 'Móng trước trái' })).toBeInTheDocument();
  });

  test('other roles are refused', () => {
    renderAs('HEAD_TRAINER', <IncidentsPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
  });
});
