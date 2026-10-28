import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { vi as vitest } from 'vitest';
import { usePagedList } from './usePagedList';
import { pageOf } from '../test/page';

const fetcher = vitest.fn();

function Harness() {
  const list = usePagedList(fetcher, { filters: ['status'] });
  const location = useLocation();
  return (
    <div>
      <input aria-label="search" value={list.query} onChange={(e) => list.setQuery(e.target.value)} />
      <button onClick={() => list.setFilter('status', 'ELIGIBLE')}>filter</button>
      <button onClick={() => list.setPage(list.page + 1)}>next</button>
      <output aria-label="page">{list.page}</output>
      <output aria-label="url">{location.search}</output>
      <output aria-label="narrowed">{String(list.narrowed)}</output>
    </div>
  );
}

const renderAt = (url = '/') =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <Harness />
    </MemoryRouter>,
  );

beforeEach(() => {
  fetcher.mockReset().mockResolvedValue({ ...pageOf([{ id: 1 }], 45), size: 20 });
});

describe('paged list hook (TECH-177, BR-185)', () => {
  test('asks for page 0 with 20 rows and no search first', async () => {
    renderAt();
    await vitest.waitFor(() => expect(fetcher).toHaveBeenCalled());
    expect(fetcher).toHaveBeenLastCalledWith({ status: '', q: '', page: 0, size: 20 });
  });

  test('search is sent 300 ms after typing stops and returns to page 0', async () => {
    renderAt('/?page=2');
    await vitest.waitFor(() => expect(fetcher).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 })));
    vitest.useFakeTimers({ shouldAdvanceTime: true });
    try {
      await userEvent.type(screen.getByLabelText('search'), 'sam');
      expect(fetcher).not.toHaveBeenCalledWith(expect.objectContaining({ q: 'sam' }));
      await act(async () => {
        await vitest.advanceTimersByTimeAsync(350);
      });
    } finally {
      vitest.useRealTimers();
    }
    await vitest.waitFor(() => expect(fetcher).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'sam', page: 0 })));
    expect(screen.getByLabelText('page')).toHaveTextContent('0');
    expect(screen.getByLabelText('narrowed')).toHaveTextContent('true');
  });

  test('a filter change returns to page 0 and lives in the URL', async () => {
    renderAt('/?page=1');
    await vitest.waitFor(() => expect(fetcher).toHaveBeenCalled());
    await userEvent.click(screen.getByText('filter'));
    await vitest.waitFor(() => expect(fetcher).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'ELIGIBLE', page: 0 })));
    expect(screen.getByLabelText('url')).toHaveTextContent('status=ELIGIBLE');
  });

  test('reload keeps the place: search, filter and page are read from the URL', async () => {
    renderAt('/?q=chop&status=INJURED&page=1');
    await vitest.waitFor(() => expect(fetcher).toHaveBeenCalled());
    expect(fetcher).toHaveBeenLastCalledWith({ status: 'INJURED', q: 'chop', page: 1, size: 20 });
    expect(screen.getByLabelText('search')).toHaveValue('chop');
  });

  test('a hand-edited page falls back to the first page', async () => {
    renderAt('/?page=-3');
    await vitest.waitFor(() => expect(fetcher).toHaveBeenCalled());
    expect(fetcher).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }));
    fetcher.mockClear();
    renderAt('/?page=abc');
    await vitest.waitFor(() => expect(fetcher).toHaveBeenCalled());
    expect(fetcher).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }));
  });

  test('a page past the end returns to the last page that exists', async () => {
    fetcher.mockImplementation(async (q: { page: number }) => (q.page > 2 ? { items: [], page: q.page, size: 20, total: 45 } : { items: [{ id: 1 }], page: q.page, size: 20, total: 45 }));
    renderAt('/?page=9');
    await vitest.waitFor(() => expect(fetcher).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 })));
  });
});
