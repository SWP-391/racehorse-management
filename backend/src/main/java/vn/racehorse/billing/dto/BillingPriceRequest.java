package vn.racehorse.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import vn.racehorse.common.validation.Bounds;
import vn.racehorse.common.validation.ClubDate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillingPriceRequest(String kind, Long feeTypeId,
                           @DecimalMax(value = Bounds.PRICE_MAX, message = Bounds.MAX_MESSAGE) BigDecimal amount,
                           @ClubDate LocalDate effectiveFrom) {
}
