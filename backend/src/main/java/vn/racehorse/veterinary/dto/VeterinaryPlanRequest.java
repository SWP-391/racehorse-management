package vn.racehorse.veterinary.dto;

import jakarta.validation.constraints.DecimalMax;
import vn.racehorse.common.validation.Bounds;

import java.math.BigDecimal;

public record VeterinaryPlanRequest(Long supplyItemId,
                          @DecimalMax(value = Bounds.QUANTITY_MAX, message = Bounds.MAX_MESSAGE) BigDecimal quantity) {
}
