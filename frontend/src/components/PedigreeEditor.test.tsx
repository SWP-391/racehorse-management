import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import PedigreeEditor from './PedigreeEditor';
import { ApiError } from '../api/client';
import { api } from '../api/api';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({ api: { options: vitest.fn() } }));

/** Opens the search select and picks the horse offered (BR-186: chosen from the system's horses, not typed). */
async function choose(name: string) {
  await userEvent.click(screen.getByRole('combobox', { name }));
  await userEvent.click(await screen.findByRole('option', { name: /Thần Phong/ }));
}

beforeEach(() => {
  vitest.mocked(api.options).mockResolvedValue(pageOf([{ id: 2, label: 'Thần Phong', hint: 'R2' }]));
});

describe('pedigree editor (task 7.5, BR-004, BR-005)', () => {
  test('sends an in-system sire and a name-only dam; unknown positions are omitted', async () => {
    const onSave = vitest.fn().mockResolvedValue(undefined);
    render(<PedigreeEditor pedigree={[]} horseId={1} onSave={onSave} onCancel={() => {}} />);
    await userEvent.selectOptions(screen.getByLabelText('Cha kind'), 'horse');
    await choose('Cha horse');
    await userEvent.selectOptions(screen.getByLabelText('Mẹ kind'), 'name');
    await userEvent.type(screen.getByLabelText('Mẹ name'), 'Hoa Đồng Nội');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(onSave).toHaveBeenCalledWith([
      { position: 'SIRE', horseId: 2 },
      { position: 'DAM', name: 'Hoa Đồng Nội' },
    ]);
  });

  test('AC-010 male horse as dam: the message appears at the dam position', async () => {
    const onSave = vitest.fn().mockRejectedValue(
      new ApiError(400, 'PEDIGREE_SEX', 'Vị trí này phải là ngựa cái.', { 'ancestors.DAM': 'Vị trí này phải là ngựa cái.' }),
    );
    render(<PedigreeEditor pedigree={[]} horseId={1} onSave={onSave} onCancel={() => {}} />);
    await userEvent.selectOptions(screen.getByLabelText('Mẹ kind'), 'horse');
    await choose('Mẹ horse');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    const dam = screen.getByRole('group', { name: 'Mẹ' });
    expect(dam).toHaveTextContent('Vị trí này phải là ngựa cái.');
  });

  test('AC-009 horse as its own ancestor: the service message is shown', async () => {
    const onSave = vitest.fn().mockRejectedValue(
      new ApiError(400, 'PEDIGREE_SELF', 'Ngựa không thể là tổ tiên của chính nó.', {
        'ancestors.SIRE': 'Ngựa không thể là tổ tiên của chính nó.',
      }),
    );
    render(<PedigreeEditor pedigree={[]} horseId={1} onSave={onSave} onCancel={() => {}} />);
    await userEvent.click(screen.getByRole('button', { name: 'Lưu' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Ngựa không thể là tổ tiên của chính nó.');
    expect(screen.getByRole('group', { name: 'Cha' })).toHaveTextContent('Ngựa không thể là tổ tiên của chính nó.');
  });
});
