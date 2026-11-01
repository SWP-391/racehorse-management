package vn.racehorse.billing.dto;

import vn.racehorse.billing.entity.Charge;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A charge to create. {@code amount} overrides quantity times unit price (adjustments). */
public record ChargeSpec(Long horseId, Long ownerId, Charge.Category category, LocalDate workDate, String description,
                   BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, String key, String sourceRef,
                   String reason, Long createdBy) {
}
