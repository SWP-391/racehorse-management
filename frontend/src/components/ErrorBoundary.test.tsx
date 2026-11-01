import { render, screen } from '@testing-library/react';
import { vi as vitest } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary';

function Boom(): never {
  throw new Error('boom');
}

beforeEach(() => {
  vitest.spyOn(console, 'error').mockImplementation(() => undefined);
});
afterEach(() => vitest.restoreAllMocks());

describe('ErrorBoundary (BR-191: never a blank page)', () => {
  test('a failing page shows a message with a retry', () => {
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Đã có lỗi xảy ra. Vui lòng thử lại.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
  });

  test('a failing part of the shell disappears and leaves its neighbours alone', () => {
    render(
      <div>
        <ErrorBoundary compact>
          <Boom />
        </ErrorBoundary>
        <p>nội dung trang</p>
      </div>,
    );
    expect(screen.getByText('nội dung trang')).toBeInTheDocument();
    expect(screen.queryByText('Đã có lỗi xảy ra. Vui lòng thử lại.')).toBeNull();
  });
});
