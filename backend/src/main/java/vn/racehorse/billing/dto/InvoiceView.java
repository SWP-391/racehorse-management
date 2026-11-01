package vn.racehorse.billing.dto;

import java.math.BigDecimal;

public record InvoiceView(Long id, Long ownerId, String ownerName, String month, String number, BigDecimal total,
                          java.time.Instant closedAt) {
}
