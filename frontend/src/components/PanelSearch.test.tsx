import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { vi as vitest } from 'vitest';
import { PanelSearch } from './PanelSearch';
import { pageOf } from '../test/page';

function Host({ total, onSearch }: { total: number; onSearch: (q: string) => void }) {
  const [q, setQ] = useState('');
  return (
    <PanelSearch
      data={pageOf([], total)}
      q={q}
      onSearch={(next) => {
        setQ(next);
        onSearch(next);
      }}
    />
  );
}

describe('panel search (BR-185, TECH-177)', () => {
  test('a list that fits on one page has no search box', () => {
    render(<Host total={5} onSearch={vitest.fn()} />);
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument();
  });

  test('a longer list shows the box and sends the text once, after the last key', async () => {
    const onSearch = vitest.fn();
    render(<Host total={57} onSearch={onSearch} />);
    await userEvent.type(screen.getByRole('searchbox', { name: 'Tìm kiếm' }), 'cưới');
    await vitest.waitFor(() => expect(onSearch).toHaveBeenCalledWith('cưới'));
    expect(onSearch).toHaveBeenCalledTimes(1);
  });

  test('the box stays while a search matches nothing, so it can be cleared', async () => {
    const onSearch = vitest.fn();
    const { rerender } = render(<PanelSearch data={pageOf([], 57)} q="" onSearch={onSearch} />);
    rerender(<PanelSearch data={pageOf([], 0)} q="zzz" onSearch={onSearch} />);
    expect(screen.getByRole('searchbox')).toHaveValue('zzz');
    await userEvent.click(screen.getByRole('button', { name: 'Xóa tìm kiếm' }));
    await vitest.waitFor(() => expect(onSearch).toHaveBeenCalledWith(''));
  });
});
