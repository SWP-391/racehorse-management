package vn.racehorse.ration.dto;

import java.util.List;

/** Unknown fields (for example an effective date) are ignored: the date is always today (AC-251). */
public record SaveRequest(List<RationMealInput> meals) {
}
