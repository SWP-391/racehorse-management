package vn.racehorse.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** The payment of one invoice: unpaid with a QR, or paid with no QR (BR-218, BR-219, BR-226). */
public record PaymentView(Long invoiceId, String number, String month, BigDecimal total, String status,
                          String paymentCode, Instant paidAt, boolean simulated, TransferInfo transfer, String qrUrl) {
}
