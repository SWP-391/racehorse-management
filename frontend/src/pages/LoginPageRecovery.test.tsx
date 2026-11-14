import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from './LoginPage';
import { AuthProvider } from '../auth/AuthContext';

function renderAt(entry: string | { pathname: string; state: unknown }) {
  render(
    <AuthProvider initial={null}>
      <MemoryRouter initialEntries={[entry as never]}>
        <LoginPage />
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('sign-in and the forgotten password (BR-210, BR-216)', () => {
  test('the sign-in asks for an email and links to the recovery page', () => {
    renderAt('/');
    const email = screen.getByLabelText('Email');
    expect(email).toHaveAttribute('type', 'email');
    expect(email).toHaveAttribute('autocomplete', 'username');
    expect(screen.getByRole('link', { name: 'Quên mật khẩu?' })).toHaveAttribute('href', '/forgot-password');
    expect(screen.queryByText('Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.')).not.toBeInTheDocument();
  });

  test('after a reset the page says so', () => {
    renderAt({ pathname: '/', state: { passwordReset: true } });
    expect(screen.getByRole('status')).toHaveTextContent('Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.');
  });
});
