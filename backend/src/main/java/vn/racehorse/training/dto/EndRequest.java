package vn.racehorse.training.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import vn.racehorse.common.validation.Bounds;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.service.TrainingEnums.Intensity;

import java.math.BigDecimal;
import java.time.Instant;

public record EndRequest(@Max(value = Bounds.DISTANCE_MAX, message = Bounds.MAX_MESSAGE) Integer distanceM,
                         @Max(value = Bounds.DURATION_MAX, message = Bounds.MAX_MESSAGE) Integer durationMin,
                         String surface, Intensity intensity, Short form,
                         @Min(value = Bounds.HEART_RATE_MIN, message = Bounds.MIN_MESSAGE)
                         @Max(value = Bounds.HEART_RATE_MAX, message = Bounds.MAX_MESSAGE) Integer avgHeartRate,
                         @DecimalMax(value = Bounds.SPEED_MAX, message = Bounds.MAX_MESSAGE) BigDecimal maxSpeed,
                         @DecimalMin(value = Bounds.WEIGHT_MIN, message = Bounds.MIN_MESSAGE)
                         @DecimalMax(value = Bounds.WEIGHT_MAX, message = Bounds.MAX_MESSAGE) BigDecimal weightKg,
                         String comment, String earlyEndReason, Instant actualEndTime) {
    public TrainingSession.Indicators indicators() {
        return new TrainingSession.Indicators(distanceM, durationMin, surface, intensity, form, avgHeartRate,
                maxSpeed, weightKg, comment);
    }
}
