package vn.racehorse.training.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import vn.racehorse.common.validation.Bounds;
import vn.racehorse.common.validation.ClubDate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PlanPhaseInput(String name, @ClubDate LocalDate startDate, @ClubDate LocalDate endDate, String goal,
                         @Max(value = Bounds.DISTANCE_MAX, message = Bounds.MAX_MESSAGE) Integer distanceM,
                         @DecimalMax(value = Bounds.VOLUME_MAX, message = Bounds.MAX_MESSAGE) BigDecimal weeklyVolumeKm,
                         String surface) {
}
