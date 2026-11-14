package vn.racehorse.payment.dto;

import java.math.BigDecimal;

/** An invoice without a payment, offered when the Club Manager assigns a transfer (BR-222). */
public record UnpaidInvoice(Long id, String number, String month, BigDecimal total, Long ownerId, String ownerName) {
}
