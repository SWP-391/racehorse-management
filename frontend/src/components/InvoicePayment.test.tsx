import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import { InvoicePayment } from './InvoicePayment';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import type { PaymentView } from '../api/types';

vitest.mock('../api/api', () => ({ api: { openPayment: vitest.fn(), paymentStatus: vitest.fn() } }));

const UNPAID: PaymentView = {
  invoiceId: 5,
  number: 'HD-2026-10-0001',
  month: '2026-10',
  total: 1500000,
  status: 'UNPAID',
  paymentCode: 'RH12345678',
  paidAt: null,
  simulated: true,
  transfer: { bankCode: 'VietinBank', accountNumber: '0000000001', accountName: '', amount: 1500000, content: 'SEVQR RH12345678' },
  qrUrl: 'https://vietqr.app/img?bank=VietinBank&acc=0000000001&amount=1500000&des=SEVQR%20RH12345678&template=qronly',
};
const PAID: PaymentView = { ...UNPAID, status: 'PAID', paidAt: '2026-11-12T07:00:00Z', transfer: null, qrUrl: null };

beforeEach(() => {
  vitest.mocked(api.openPayment).mockReset().mockResolvedValue(UNPAID);
  vitest.mocked(api.paymentStatus).mockReset().mockResolvedValue({ ...UNPAID, transfer: null, qrUrl: null, paymentCode: null });
});

describe('payment of a closed invoice (BR-218, BR-219, BR-223)', () => {
  test('AC-469 the owner opens the payment and sees the QR for the full total and the transfer details', async () => {
    renderAs('HORSE_OWNER', <InvoicePayment invoiceId={5} number="HD-2026-10-0001" />);
    expect(await screen.findByText('Chưa thanh toán')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Thanh toán' }));
    expect(api.openPayment).toHaveBeenCalledWith(5);
    const qr = await screen.findByRole('img', { name: 'Mã QR chuyển khoản' });
    expect(qr).toHaveAttribute('src', UNPAID.qrUrl);
    expect(screen.getByText('SEVQR RH12345678')).toBeInTheDocument();
    expect(screen.getByText(/1\.500\.000/)).toBeInTheDocument();
    expect(screen.getByText('0000000001')).toBeInTheDocument();
  });

  test('BR-223 the page says cash is handled by the Club Manager and BR-226 the test mode is stated', async () => {
    renderAs('HORSE_OWNER', <InvoicePayment invoiceId={5} number="HD-2026-10-0001" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Thanh toán' }));
    expect(await screen.findByText(/liên hệ Quản lý CLB/)).toBeInTheDocument();
    expect(screen.getByText(/chế độ thử nghiệm/)).toBeInTheDocument();
  });

  test('AC-482 a paid invoice shows "Đã thanh toán" and no button or QR', async () => {
    vitest.mocked(api.paymentStatus).mockResolvedValue(PAID);
    renderAs('HORSE_OWNER', <InvoicePayment invoiceId={5} number="HD-2026-10-0001" />);
    expect(await screen.findByText('Đã thanh toán')).toBeInTheDocument();
    expect(screen.getByText('mô phỏng')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Thanh toán' })).not.toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  test('AC-470 the open dialog turns to paid when the money arrives', async () => {
    vitest.useFakeTimers({ shouldAdvanceTime: true });
    try {
      renderAs('HORSE_OWNER', <InvoicePayment invoiceId={5} number="HD-2026-10-0001" />);
      await userEvent.click(await screen.findByRole('button', { name: 'Thanh toán' }));
      expect(await screen.findByText('Đang chờ tiền về…')).toBeInTheDocument();
      vitest.mocked(api.paymentStatus).mockResolvedValue(PAID);
      await act(async () => {
        await vitest.advanceTimersByTimeAsync(5200);
      });
      expect(await screen.findByText(/Hóa đơn đã được thanh toán/)).toBeInTheDocument();
      expect(screen.queryByRole('img', { name: 'Mã QR chuyển khoản' })).not.toBeInTheDocument();
    } finally {
      vitest.useRealTimers();
    }
  });

  test('a refusal to open is shown', async () => {
    vitest.mocked(api.openPayment).mockRejectedValue(new Error('Bạn không có quyền thực hiện thao tác này.'));
    renderAs('HORSE_OWNER', <InvoicePayment invoiceId={5} number="HD-2026-10-0001" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Thanh toán' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
