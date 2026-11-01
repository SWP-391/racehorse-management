package vn.racehorse.veterinary.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import vn.racehorse.common.validation.Bounds;
import vn.racehorse.common.validation.ClubDate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExaminationRequest(String diagnosis, String bodyRegion, String bodyDetail, String bodyArea,
                                 @DecimalMin(value = Bounds.WEIGHT_MIN, message = Bounds.MIN_MESSAGE)
                                 @DecimalMax(value = Bounds.WEIGHT_MAX, message = Bounds.MAX_MESSAGE) BigDecimal weightKg,
                                 String treatment, String prescription, @ClubDate LocalDate examDate,
                                 Long feeTypeId) {
}
