import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import { pageNumbers, Pagination } from './data-table';
import { ErrorBoundary } from '../ErrorBoundary';

describe('numbered pagination', () => {
  test('shows the first, last and neighbours of the current page with gaps', () => {
    expect(pageNumbers(1, 3)).toEqual([1, 2, 3]);
    expect(pageNumbers(1, 10)).toEqual([1, 2, null, 10]);
    expect(pageNumbers(5, 10)).toEqual([1, null, 4, 5, 6, null, 10]);
    expect(pageNumbers(10, 10)).toEqual([1, null, 9, 10]);
  });

  test('clicking a number goes to that page and the size select offers 10, 20, 50 and 100', async () => {
    const onPage = vitest.fn();
    const onSize = vitest.fn();
    render(<Pagination page={2} total={5} onPage={onPage} size={20} onSize={onSize} />);
    await userEvent.click(screen.getByRole('button', { name: 'Trang 3' }));
    expect(onPage).toHaveBeenCalledWith(3);
    expect(screen.getByRole('button', { name: 'Trang 2' })).toHaveAttribute('aria-current', 'page');
    const select = screen.getByLabelText('Số dòng mỗi trang');
    expect([...select.querySelectorAll('option')].map((o) => o.value)).toEqual(['10', '20', '50', '100']);
    await userEvent.selectOptions(select, '50');
    expect(onSize).toHaveBeenCalledWith(50);
  });
});

describe('ErrorBoundary', () => {
  test('a render error shows a message with a retry instead of a blank page', () => {
    const spy = vitest.spyOn(console, 'error').mockImplementation(() => undefined);
    const Boom = () => {
      throw new Error('boom');
    };
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
    spy.mockRestore();
  });
});
