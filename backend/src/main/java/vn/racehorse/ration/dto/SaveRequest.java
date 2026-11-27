package vn.racehorse.ration.dto;

import java.util.List;

/**
 * Unknown fields (for example an effective date) are ignored: the date is always today (AC-251). {@code templateId}
 * starts the ration from a template (its meals and tag are used); {@code conditionTag} defaults to NORMAL.
 */
public record SaveRequest(List<RationMealInput> meals, String conditionTag, MismatchInput mismatch, Long templateId) {
}
