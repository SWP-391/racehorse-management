package vn.racehorse.veterinary.dto;

import jakarta.validation.constraints.DecimalMax;
import vn.racehorse.common.validation.Bounds;
import vn.racehorse.common.validation.ClubDate;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code supplyItemId} and {@code quantity}: the medicine taken from stock; a hoof check has neither (BR-172). */
public record CompletionRequest(@ClubDate LocalDate dateDone, String performer, String product, String batch, String note,
                                Long supplyItemId,
                                @DecimalMax(value = Bounds.QUANTITY_MAX, message = Bounds.MAX_MESSAGE) BigDecimal quantity) {
}
