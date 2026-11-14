package vn.racehorse.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One received transfer, applied or kept unmatched, for the Club Manager (BR-221, BR-222, BR-226). */
public record TransferRow(Long id, BigDecimal amount, String content, Instant transferredAt, Instant receivedAt,
                          boolean simulated, String status, String reason, String reasonText, Long invoiceId,
                          String invoiceNumber, String ownerName) {
}
