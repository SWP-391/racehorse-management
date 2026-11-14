package vn.racehorse.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** An invoice in a list; {@code paymentStatus} is UNPAID or PAID and {@code paidAt} is set once paid (BR-224). */
public record InvoiceView(Long id, Long ownerId, String ownerName, String month, String number, BigDecimal total,
                          Instant closedAt, String paymentStatus, Instant paidAt) {
}
