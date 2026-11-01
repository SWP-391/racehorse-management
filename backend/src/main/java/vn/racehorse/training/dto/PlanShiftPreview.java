package vn.racehorse.training.dto;

import java.time.LocalDate;

public record PlanShiftPreview(Long sessionId, LocalDate from, LocalDate to) {
}
