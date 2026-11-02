package vn.racehorse.schedule.dto;

import java.time.LocalDate;
import java.util.List;

public record TimetableResponse(LocalDate from, LocalDate to, List<TimetableDayResponse> days) {
}
