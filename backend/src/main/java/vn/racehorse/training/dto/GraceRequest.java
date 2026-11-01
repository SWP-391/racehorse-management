package vn.racehorse.training.dto;

import jakarta.validation.constraints.Max;
import vn.racehorse.common.validation.Bounds;

public record GraceRequest(@Max(value = Bounds.SETTING_MINUTES_MAX, message = Bounds.MAX_MESSAGE) Integer minutes) {
}
