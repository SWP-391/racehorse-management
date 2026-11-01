package vn.racehorse.veterinary.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import vn.racehorse.common.validation.Bounds;
import vn.racehorse.common.validation.ClubDate;
import vn.racehorse.veterinary.entity.CareType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CareItemRequest(CareType type, String name,
                              @Max(value = Bounds.INTERVAL_MAX, message = Bounds.MAX_MESSAGE) Integer intervalDays,
                              @ClubDate LocalDate firstDue, Long plannedSupplyItemId,
                              @DecimalMax(value = Bounds.QUANTITY_MAX, message = Bounds.MAX_MESSAGE) BigDecimal plannedQuantity) {
}
