package vn.racehorse.training.dto;

import jakarta.validation.constraints.DecimalMax;
import vn.racehorse.common.validation.Bounds;

import java.math.BigDecimal;

public record TrainingThresholdRequest(@DecimalMax(value = Bounds.SETTING_HOURS_MAX, message = Bounds.MAX_MESSAGE) BigDecimal hours) {
}
