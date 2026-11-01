package vn.racehorse.schedule.dto;

import jakarta.validation.constraints.DecimalMax;
import vn.racehorse.common.validation.Bounds;

import java.math.BigDecimal;

public record LeaveNoticeRequest(@DecimalMax(value = Bounds.SETTING_DAYS_MAX, message = Bounds.MAX_MESSAGE) BigDecimal days) {
}
