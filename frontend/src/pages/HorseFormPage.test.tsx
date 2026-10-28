import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import HorseFormPage from './HorseFormPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import { pageOf } from '../test/page';
import { notify } from '../lib/notify';

vitest.mock('../api/api', () => ({
  api: { options: vitest.fn(), horse: vitest.fn(), registerHorse: vitest.fn(), updateHorse: vitest.fn(), uploadHorsePhoto: vitest.fn() },
}));

beforeEach(() => {
  // One mock for both selects: owners and trainers come from the same endpoint with another kind (BR-186).
  vitest.mocked(api.options).mockImplementation((kind) =>
    Promise.resolve(
      kind === 'trainers'
        ? pageOf([{ id: 7, label: 'HLV Minh', hint: 'htl-minh' }])
        : pageOf([{ id: 3, label: 'Chủ ngựa B', hint: 'owner' }]),
    ),
  );
});

async function fillAndSubmit() {
  await userEvent.type(screen.getByLabelText(/^Tên ngựa/), 'Sao Mai');
  await userEvent.type(screen.getByLabelText(/^Số đăng ký/), 'VN-001');
  await userEvent.selectOptions(screen.getByLabelText(/^Giới tính/), 'FEMALE');
  // The owner is chosen from the Club Manager's accounts with a search select, not typed (BR-186).
  await userEvent.click(await screen.findByRole('combobox', { name: /^Chủ sở hữu/ }));
  await userEvent.click(await screen.findByRole('option', { name: /Chủ ngựa B/ }));
  await userEvent.click(await screen.findByRole('combobox', { name: /^HLV phụ trách/ }));
  await userEvent.click(await screen.findByRole('option', { name: /HLV Minh/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));
}

vitest.mock('../lib/notify', () => ({ notify: { success: vitest.fn(), error: vitest.fn() } }));

describe('horse form (task 7.4, BR-002, BR-003)', () => {
  test('AC-283 the assigned trainer is chosen from the Head Trainers and sent with the horse', async () => {
    vitest.mocked(api.registerHorse).mockResolvedValue({ id: 5 } as never);
    renderAs('CLUB_MANAGER', <HorseFormPage />, '/horses/new', '/horses/new');
    await userEvent.type(await screen.findByLabelText(/^Ngày sinh/), '2021-04-12');
    await fillAndSubmit();
    expect(vitest.mocked(api.registerHorse).mock.calls[0][0]).toMatchObject({ ownerId: 3, trainerId: 7 });
    expect(api.options).toHaveBeenCalledWith('trainers', expect.anything());
  });

  test('AC-284 a missing trainer is named under the field', async () => {
    vitest.mocked(api.registerHorse).mockRejectedValue(
      new ApiError(400, 'TRAINER_REQUIRED', 'Bắt buộc chọn HLV phụ trách.', { trainerId: 'Bắt buộc chọn HLV phụ trách.' }),
    );
    renderAs('CLUB_MANAGER', <HorseFormPage />, '/horses/new', '/horses/new');
    await userEvent.type(await screen.findByLabelText(/^Ngày sinh/), '2021-04-12');
    await userEvent.type(screen.getByLabelText(/^Tên ngựa/), 'Sao Mai');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect((await screen.findAllByText('Bắt buộc chọn HLV phụ trách.')).length).toBeGreaterThan(0);
  });

  test('AC-004 missing birth date: the field is named', async () => {
    vitest.mocked(api.registerHorse).mockRejectedValue(
      new ApiError(400, 'VALIDATION_FAILED', 'Thông tin chưa hợp lệ: birthDate.', { birthDate: 'Bắt buộc nhập.' }),
    );
    renderAs('CLUB_MANAGER', <HorseFormPage />, '/horses/new', '/horses/new');
    await fillAndSubmit();
    expect(screen.getByLabelText(/^Ngày sinh/)).toHaveAccessibleDescription(/Bắt buộc nhập./);
    expect(vitest.mocked(api.registerHorse).mock.calls[0][0]).toMatchObject({ birthDate: null, ownerId: 3 });
  });

  test('AC-003 duplicate registration number: the duplicate horse is named under the field', async () => {
    const message = 'Số đăng ký đã được dùng cho ngựa "Gió Bắc".';
    vitest.mocked(api.registerHorse).mockRejectedValue(
      new ApiError(409, 'DUPLICATE_REGISTRATION', message, { registrationNumber: message }),
    );
    renderAs('CLUB_MANAGER', <HorseFormPage />, '/horses/new', '/horses/new');
    await userEvent.type(screen.getByLabelText(/^Ngày sinh/), '2021-04-12');
    await fillAndSubmit();
    expect(screen.getByLabelText(/^Số đăng ký/)).toHaveAccessibleDescription(/Gió Bắc/);
  });

  test('AC-005 a Head Trainer cannot open the form', () => {
    renderAs('HEAD_TRAINER', <HorseFormPage />, '/horses/new', '/horses/new');
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
  });

  test('AC-361 a photo chosen with the form is uploaded after the horse is saved', async () => {
    vitest.mocked(api.registerHorse).mockResolvedValue({ id: 5 } as never);
    vitest.mocked(api.uploadHorsePhoto).mockResolvedValue({ id: 5 } as never);
    renderAs('CLUB_MANAGER', <HorseFormPage />, '/horses/new', '/horses/new');
    await userEvent.type(await screen.findByLabelText(/^Ngày sinh/), '2021-04-12');
    const file = new File([new Uint8Array([137, 80, 78, 71])], 'ngua.png', { type: 'image/png' });
    await userEvent.upload(screen.getByLabelText(/^Chọn ảnh/), file);
    await fillAndSubmit();
    await vitest.waitFor(() => expect(api.uploadHorsePhoto).toHaveBeenCalledWith(5, file));
    expect(api.registerHorse).toHaveBeenCalledTimes(1);
  });

  test('AC-362 a refused photo is reported and the saved horse is not undone', async () => {
    vitest.mocked(api.registerHorse).mockResolvedValue({ id: 5 } as never);
    vitest.mocked(api.uploadHorsePhoto).mockRejectedValue(new ApiError(400, 'PHOTO_TYPE', 'Chỉ nhận ảnh JPG hoặc PNG.', { file: 'Chỉ nhận ảnh JPG hoặc PNG.' }));
    renderAs('CLUB_MANAGER', <HorseFormPage />, '/horses/new', '/horses/new');
    await userEvent.type(await screen.findByLabelText(/^Ngày sinh/), '2021-04-12');
    await userEvent.upload(screen.getByLabelText(/^Chọn ảnh/), new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0])], 'hong.jpg', { type: 'image/jpeg' }));
    await fillAndSubmit();
    await vitest.waitFor(() => expect(notify.error).toHaveBeenCalled());
    expect(vitest.mocked(notify.error).mock.calls[0][0]).toMatchObject({ message: 'Chỉ nhận ảnh JPG hoặc PNG.' });
    expect(api.registerHorse).toHaveBeenCalledTimes(1);
  });

  test('a text file renamed .jpg is refused at once, before anything is saved or sent', async () => {
    vitest.mocked(api.registerHorse).mockResolvedValue({ id: 5 } as never);
    renderAs('CLUB_MANAGER', <HorseFormPage />, '/horses/new', '/horses/new');
    await userEvent.type(await screen.findByLabelText(/^Ngày sinh/), '2021-04-12');
    await userEvent.upload(screen.getByLabelText(/^Chọn ảnh/), new File(['ghi chu'], 'ghi-chu.jpg', { type: 'image/jpeg' }));
    expect(await screen.findByText(/không phải ảnh JPG hoặc PNG thật/)).toBeInTheDocument();
    await fillAndSubmit();
    await vitest.waitFor(() => expect(api.registerHorse).toHaveBeenCalledTimes(1));
    expect(api.uploadHorsePhoto).not.toHaveBeenCalled();
  });
});
