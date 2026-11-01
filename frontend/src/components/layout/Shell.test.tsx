import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, Route, Routes } from 'react-router-dom';
import { vi as vitest } from 'vitest';
import Layout from '../Layout';
import { renderAs } from '../../test/render';

vitest.mock('../../api/api', () => ({
  api: { logout: vitest.fn(), notifications: vitest.fn().mockResolvedValue({ unread: 0, items: [] }) },
}));

function Pages() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<><h1>Danh sách ngựa</h1><Link to="/health">đi</Link></>} />
        <Route path="/health" element={<h1>Sức khỏe đàn ngựa</h1>} />
      </Route>
    </Routes>
  );
}

describe('ledger shell (task 3.1)', () => {
  test('the skip link targets the main region', () => {
    renderAs('HEAD_TRAINER', <Layout />);
    expect(screen.getByRole('link', { name: 'Bỏ qua, đến nội dung chính' })).toHaveAttribute('href', '#main');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main');
  });

  test('the user menu shows the name, the role and a sign-out button', () => {
    renderAs('VETERINARIAN', <Layout />);
    const menu = screen.getByTestId('user-menu');
    expect(menu).toHaveTextContent('Test VETERINARIAN');
    expect(menu).toHaveTextContent('Bác sĩ Thú y');
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeInTheDocument();
  });

  test('after a route change focus moves to the new page heading', async () => {
    renderAs('HEAD_TRAINER', <Pages />, '/*', '/');
    await userEvent.click(screen.getByRole('link', { name: 'đi' }));
    const h1 = await screen.findByRole('heading', { level: 1, name: 'Sức khỏe đàn ngựa' });
    await vitest.waitFor(() => expect(h1).toHaveFocus());
  });
});

describe('timetable menu entry (PERM-056)', () => {
  test.each([
    ['GROOM', true],
    ['HEAD_TRAINER', true],
    ['VETERINARIAN', false],
    ['HORSE_OWNER', false],
    ['CLUB_MANAGER', false],
  ] as const)('%s sees the entry: %s', (role, shown) => {
    renderAs(role, <Layout />);
    const links = screen.queryAllByRole('link', { name: 'Thời gian biểu' });
    expect(links.length > 0).toBe(shown);
  });
});
