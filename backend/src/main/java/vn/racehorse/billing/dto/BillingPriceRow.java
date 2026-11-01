package vn.racehorse.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillingPriceRow(Long id, BigDecimal amount, LocalDate effectiveFrom) {
}
