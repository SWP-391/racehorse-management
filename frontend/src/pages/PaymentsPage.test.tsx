import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import PaymentsPage from './PaymentsPage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { TransferRow } from '../api/types';

vitest.mock('../api/api', () => ({
  api: {
    receivedPayments: vitest.fn(),
    unmatchedPayments: vitest.fn(),
    unpaidInvoices: vitest.fn(),
    assignTransfer: vitest.fn(),
  },
}));

const page = (items: TransferRow[]) => ({ items, page: 0, size: 20, total: items.length });
const row = (over: Partial<TransferRow>): TransferRow => ({
  id: 1,
  amount: 1000000,
  content: 'chuyen tien',
  transferredAt: '2026-11-12T07:02:37Z',
  receivedAt: '2026-11-12T07:02:40Z',
  simulated: false,
  status: 'UNMATCHED',
  reason: 'AMOUNT_MISMATCH',
  reasonText: 'số tiền khác tổng',
  invoiceId: null,
  invoiceNumber: null,
  ownerName: null,
  ...over,
});

beforeEach(() => {
  vitest.mocked(api.receivedPayments).mockReset().mockResolvedValue(page([]));
  vitest.mocked(api.unmatchedPayments).mockReset().mockResolvedValue(page([row({})]));
  vitest.mocked(api.unpaidInvoices).mockReset().mockResolvedValue([
    { id: 9, number: 'HD-2026-10-0009', month: '2026-10', total: 1500000, ownerId: 7, ownerName: 'Bà Lý Thị Hoa' },
  ]);
  vitest.mocked(api.assignTransfer).mockReset().mockResolvedValue(undefined);
});

async function openUnmatched() {
  renderAs('CLUB_MANAGER', <PaymentsPage />, '/payments', '/payments?tab=unmatched');
  return screen.findAllByText('số tiền khác tổng');
}

describe('received payments and unmatched transfers (BR-221, BR-222, BR-226)', () => {
  test('AC-483 a test-mode payment is labelled "mô phỏng" in the received list', async () => {
    vitest.mocked(api.receivedPayments).mockResolvedValue(
      page([row({ status: 'APPLIED', reason: null, reasonText: null, simulated: true, invoiceId: 9, invoiceNumber: 'HD-2026-10-0009', ownerName: 'Bà Lý Thị Hoa' })]),
    );
    renderAs('CLUB_MANAGER', <PaymentsPage />);
    expect(await screen.findByText('HD-2026-10-0009')).toBeInTheDocument();
    expect(screen.getByText('mô phỏng')).toBeInTheDocument();
    expect(screen.getByText('Bà Lý Thị Hoa')).toBeInTheDocument();
  });

  test('the unmatched list names the reason and offers "Gán" only for an open transfer', async () => {
    vitest.mocked(api.unmatchedPayments).mockResolvedValue(page([row({ id: 1 }), row({ id: 2, status: 'ASSIGNED', amount: 2000000 })]));
    await openUnmatched();
    expect(screen.getAllByRole('button', { name: /^Gán / })).toHaveLength(1);
    expect(screen.getByText('Đã gán')).toBeInTheDocument();
  });

  test('AC-476 assigning an unmatched transfer with a reason', async () => {
    await openUnmatched();
    await userEvent.click(screen.getByRole('button', { name: /^Gán / }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.selectOptions(await within(dialog).findByRole('combobox'), '9');
    await userEvent.type(within(dialog).getByLabelText('Lý do gán'), 'Đã nhận tiền mặt phần còn lại');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Gán và đánh dấu đã thanh toán' }));
    expect(api.assignTransfer).toHaveBeenCalledWith(1, 9, 'Đã nhận tiền mặt phần còn lại');
  });

  test('AC-477 no reason is refused before anything is sent', async () => {
    await openUnmatched();
    await userEvent.click(screen.getByRole('button', { name: /^Gán / }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.selectOptions(await within(dialog).findByRole('combobox'), '9');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Gán và đánh dấu đã thanh toán' }));
    expect(await within(dialog).findByText('Bắt buộc nhập lý do.')).toBeInTheDocument();
    expect(api.assignTransfer).not.toHaveBeenCalled();
  });

  test('AC-478 a refusal of the service is shown in the dialog', async () => {
    vitest.mocked(api.assignTransfer).mockRejectedValue(new ApiError(409, 'INVOICE_ALREADY_PAID', 'Hóa đơn này đã thanh toán.', {}));
    await openUnmatched();
    await userEvent.click(screen.getByRole('button', { name: /^Gán / }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.selectOptions(await within(dialog).findByRole('combobox'), '9');
    await userEvent.type(within(dialog).getByLabelText('Lý do gán'), 'thử');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Gán và đánh dấu đã thanh toán' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Hóa đơn này đã thanh toán.');
  });

  test('AC-479 anyone but the Club Manager sees access denied', () => {
    renderAs('HORSE_OWNER', <PaymentsPage />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(api.receivedPayments).not.toHaveBeenCalled();
  });
});
