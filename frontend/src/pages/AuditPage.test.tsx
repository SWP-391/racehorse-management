import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import AuditPage from './AuditPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({ api: { audit: vitest.fn(), accounts: vitest.fn(), options: vitest.fn() } }));

const entry = {
  id: 1,
  actorId: 5,
  actorName: 'Quản lý A',
  occurredAt: '2026-10-21T01:00:00Z',
  occurredAtClub: '21/10/2026 08:00:00',
  action: 'HORSE_UPDATED',
  targetType: 'HORSE',
  targetId: 9,
  details: '{"name":{"from":"A","to":"B"}}',
};

beforeEach(() => {
  vitest.mocked(api.audit).mockResolvedValue(pageOf([entry]));
  vitest.mocked(api.accounts).mockResolvedValue(pageOf([
    { id: 5, username: 'cm', displayName: 'Quản lý A', role: 'CLUB_MANAGER', active: true, createdAt: '' },
  ]));
});

beforeEach(() => {
  vitest.mocked(api.options).mockResolvedValue(pageOf([{ id: 5, label: 'Quản lý A', hint: 'cm' }]));
});

describe('audit log screen (task 7.3, BR-093)', () => {
  test('filters by date range and user', async () => {
    renderAs('CLUB_MANAGER', <AuditPage />);
    await screen.findByText('HORSE_UPDATED');
    await userEvent.type(screen.getByLabelText('Từ ngày'), '2026-10-01');
    await userEvent.type(screen.getByLabelText('Đến ngày'), '2026-10-21');
    await userEvent.click(screen.getByRole('combobox', { name: 'Người thực hiện' }));
    await userEvent.click(await screen.findByRole('option', { name: /Quản lý A/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Lọc' }));
    expect(api.audit).toHaveBeenLastCalledWith(expect.objectContaining({ from: '2026-10-01', to: '2026-10-21', actorId: '5' }));
  });

  test('AC-026 entries have no edit or delete controls', async () => {
    renderAs('CLUB_MANAGER', <AuditPage />);
    await screen.findByText('HORSE_UPDATED');
    // Only controls that filter or page the log; no row has an edit or a delete (AC-026).
    const buttons = screen.getAllByRole('button').map((b) => b.textContent);
    expect(buttons).toEqual(['Lọc']);
  });

  test('AC-027 other roles are refused', () => {
    renderAs('HEAD_TRAINER', <AuditPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền');
    expect(api.audit).not.toHaveBeenCalled();
  });
});

describe('readable details (critic round 3)', () => {
  test('codes and ids are shown as people read them', async () => {
    vitest.mocked(api.audit).mockResolvedValue(pageOf([
      {
        ...entry,
        id: 2,
        action: 'ACCOUNT_BOOTSTRAPPED',
        targetType: 'ACCOUNT',
        details: '{"examinationId":4,"horseId":9,"role":"GROOM","kind":"INTAKE","warningConfirmed":false}',
      },
    ]));
    renderAs('CLUB_MANAGER', <AuditPage />);
    expect(await screen.findByText('Khởi tạo tài khoản quản lý')).toBeInTheDocument();
    expect(screen.getByText('Vai trò:').parentElement).toHaveTextContent('Vai trò: Nhân viên Chăm sóc');
    expect(screen.getByText('Loại khóa:').parentElement).toHaveTextContent('Loại khóa: Nhập đàn');
    expect(screen.getByText('Đã xác nhận cảnh báo:').parentElement).toHaveTextContent('Không');
    // Internal record ids mean nothing to a reader.
    expect(screen.queryByText(/examinationId|horseId/)).not.toBeInTheDocument();
  });

  test('names the users of a responsible-person change', async () => {
    vitest.mocked(api.audit).mockResolvedValue(pageOf([
      { ...entry, id: 3, action: 'SESSION_RESPONSIBLE_CHANGED', details: JSON.stringify({ from: 5, to: 99 }) },
    ]));
    renderAs('CLUB_MANAGER', <AuditPage />);
    await screen.findByText('#99');
    expect(screen.getByText('#99')).toBeInTheDocument();
    expect(screen.getAllByText(/Quản lý A/).length).toBeGreaterThan(1);
  });

  test('shows the region name of an injury mark, not its code', async () => {
    vitest.mocked(api.audit).mockResolvedValue(pageOf([
      { ...entry, id: 4, action: 'INJURY_MARK_CREATED', details: JSON.stringify({ markId: 3, region: 'LF_FETLOCK', regionName: 'Khớp cổ chân trước trái' }) },
    ]));
    renderAs('CLUB_MANAGER', <AuditPage />);
    expect(await screen.findByText('Khớp cổ chân trước trái')).toBeInTheDocument();
    expect(screen.queryByText('LF_FETLOCK')).not.toBeInTheDocument();
  });
});
