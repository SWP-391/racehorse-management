package vn.racehorse.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import vn.racehorse.common.validation.Bounds;
import vn.racehorse.common.validation.ClubDate;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A new fee type needs its first price (BR-207, AC-453); a rename sends the name only. */
public record FeeTypeRequest(String name,
                             @DecimalMax(value = Bounds.PRICE_MAX, message = Bounds.MAX_MESSAGE) BigDecimal amount,
                             @ClubDate LocalDate effectiveFrom) {
}
