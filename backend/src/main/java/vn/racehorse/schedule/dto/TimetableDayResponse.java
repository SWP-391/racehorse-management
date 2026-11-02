package vn.racehorse.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/** One day: barn hours and leave for a Groom (empty or null for a Head Trainer) and the sessions in time order. */
public record TimetableDayResponse(LocalDate date, List<RangeDto> barnHours, TimetableLeaveResponse leave,
                                   List<TimetableItemResponse> items) {
}
