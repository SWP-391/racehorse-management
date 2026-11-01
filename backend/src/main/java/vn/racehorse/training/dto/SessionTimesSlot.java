package vn.racehorse.training.dto;

import vn.racehorse.training.entity.TrainingSession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** A half-open planned range [start, end) in club time. */
public record SessionTimesSlot(Long sessionId, LocalDate date, LocalTime start, int durationMin) {
    public static SessionTimesSlot of(TrainingSession s) {
        return new SessionTimesSlot(s.getId(), s.getScheduledDate(), s.getPlannedStartTime(), s.getPlannedDurationMin());
    }

    public boolean timed() {
        return start != null;
    }

    LocalDateTime from() {
        return date.atTime(start);
    }

    LocalDateTime to() {
        return from().plusMinutes(durationMin);
    }

    public boolean overlaps(SessionTimesSlot other) {
        return timed() && other.timed() && from().isBefore(other.to()) && other.from().isBefore(to());
    }
}
