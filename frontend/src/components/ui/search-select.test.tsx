import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { vi as vitest } from 'vitest';
import { SearchSelect, type Chosen } from './search-select';
import type { OptionItem, Page } from '@/api/types';

const option = (id: number, label: string): OptionItem => ({ id, label, hint: `H${id}` });

function Host({ load, initial = null }: { load: (q: string) => Promise<Page<OptionItem>>; initial?: Chosen | null }) {
  const [value, setValue] = useState<Chosen | null>(initial);
  return (
    <>
      <SearchSelect value={value} onChange={setValue} load={load} placeholder="Chọn chủ" aria-label="Chủ sở hữu" />
      <output aria-label="chosen">{value ? `${value.id}:${value.label}` : 'none'}</output>
    </>
  );
}

describe('search select (TECH-178, BR-186, AC-368)', () => {
  test('loads the choices when opened and chooses one without typing', async () => {
    const load = vitest.fn().mockResolvedValue({ items: [option(1, 'Chủ A'), option(2, 'Chủ B')], page: 0, size: 20, total: 2 });
    render(<Host load={load} />);
    await userEvent.click(screen.getByRole('combobox', { name: 'Chủ sở hữu' }));
    await userEvent.click(await screen.findByRole('option', { name: /Chủ B/ }));
    expect(screen.getByLabelText('chosen')).toHaveTextContent('2:Chủ B');
    expect(screen.getByRole('combobox')).toHaveTextContent('Chủ B');
  });

  test('the search box shows only when the choices do not fit on one page', async () => {
    const few = vitest.fn().mockResolvedValue({ items: [option(1, 'Chủ A')], page: 0, size: 20, total: 1 });
    const { unmount } = render(<Host load={few} />);
    await userEvent.click(screen.getByRole('combobox'));
    await screen.findByRole('option', { name: /Chủ A/ });
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    unmount();

    const many = vitest.fn().mockResolvedValue({ items: [option(1, 'Chủ A')], page: 0, size: 20, total: 57 });
    render(<Host load={many} />);
    await userEvent.click(screen.getByRole('combobox'));
    expect(await screen.findByRole('textbox', { name: 'Tìm kiếm' })).toBeInTheDocument();
  });

  test('a truncated list says how many more there are', async () => {
    const many = vitest.fn().mockResolvedValue({ items: [option(1, 'Chủ A'), option(2, 'Chủ B')], page: 0, size: 2, total: 57 });
    const { unmount } = render(<Host load={many} />);
    await userEvent.click(screen.getByRole('combobox'));
    expect(await screen.findByText('Còn 55 mục nữa — gõ để tìm')).toBeInTheDocument();
    unmount();

    const all = vitest.fn().mockResolvedValue({ items: [option(1, 'Chủ A')], page: 0, size: 20, total: 1 });
    render(<Host load={all} />);
    await userEvent.click(screen.getByRole('combobox'));
    await screen.findByRole('option', { name: /Chủ A/ });
    expect(screen.queryByTestId('more-options')).not.toBeInTheDocument();
  });

  test('typing asks the service for the matching choices', async () => {
    const load = vitest.fn().mockImplementation(async (q: string) => ({
      items: q ? [option(7, 'Linh Phong')] : [option(1, 'Chủ A')],
      page: 0,
      size: 20,
      total: 57,
    }));
    render(<Host load={load} />);
    await userEvent.click(screen.getByRole('combobox'));
    await userEvent.type(await screen.findByRole('textbox', { name: 'Tìm kiếm' }), 'linh');
    expect(await screen.findByRole('option', { name: /Linh Phong/ }, { timeout: 2000 })).toBeInTheDocument();
    expect(load).toHaveBeenLastCalledWith('linh');
  });

  test('a chosen value keeps its name when it is not among the loaded choices', async () => {
    const load = vitest.fn().mockResolvedValue({ items: [option(1, 'Chủ A')], page: 0, size: 20, total: 1 });
    render(<Host load={load} initial={{ id: 99, label: 'Chủ đã nghỉ' }} />);
    expect(screen.getByRole('combobox')).toHaveTextContent('Chủ đã nghỉ');
  });

  test('keyboard: arrows move, Enter chooses', async () => {
    const load = vitest.fn().mockResolvedValue({ items: [option(1, 'Chủ A'), option(2, 'Chủ B')], page: 0, size: 20, total: 2 });
    render(<Host load={load} />);
    await userEvent.click(screen.getByRole('combobox'));
    await screen.findByRole('option', { name: /Chủ B/ });
    await userEvent.keyboard('{ArrowDown}{Enter}');
    expect(screen.getByLabelText('chosen')).toHaveTextContent('2:Chủ B');
  });

  test('a list that cannot be loaded shows the message and no choices', async () => {
    const load = vitest.fn().mockRejectedValue(new Error('Máy chủ không phản hồi.'));
    render(<Host load={load} />);
    await userEvent.click(screen.getByRole('combobox'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được danh sách lựa chọn');
    expect(screen.queryByRole('option')).not.toBeInTheDocument();
  });
});
