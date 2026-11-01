package vn.racehorse.billing.dto;

import vn.racehorse.common.validation.ClubDate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillingAdjustmentRequest(Long ownerId, Long horseId, @ClubDate LocalDate date, BigDecimal amount, String reason) {
}
