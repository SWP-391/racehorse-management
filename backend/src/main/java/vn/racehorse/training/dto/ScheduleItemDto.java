package vn.racehorse.training.dto;

import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.service.TrainingEnums.SessionState;
import vn.racehorse.training.service.TrainingEnums.SessionType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** What an Owner sees of a session: schedule, the Head Trainer's comment (PERM-012) and how many videos. */
public record ScheduleItemDto(Long id, LocalDate date, LocalTime startTime, SessionType type, SessionState state,
                              Short form, String comment, long videoCount, String plannedEnd,
                              boolean plannedEndNextDay, Instant endedAt) {
    public static ScheduleItemDto of(TrainingSession s, long videoCount) {
        return new ScheduleItemDto(s.getId(), s.getScheduledDate(), s.getPlannedStartTime(), s.getType(),
                s.getState(), s.getForm(), s.getComment(), videoCount, endOf(s), endsNextDay(s),
                s.getEndedAt());
    }

    private static String endOf(TrainingSession s) {
        return s.getPlannedStartTime() == null ? null
                : s.getPlannedStartTime().plusMinutes(s.getPlannedDurationMin()).toString();
    }

    private static boolean endsNextDay(TrainingSession s) {
        return s.getPlannedStartTime() != null
                && s.getPlannedStartTime().toSecondOfDay() / 60 + s.getPlannedDurationMin() >= 24 * 60;
    }
}
