package vn.racehorse.training.dto;

import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.service.TrainingEnums.AssignmentAck;
import vn.racehorse.training.service.TrainingEnums.SessionState;
import vn.racehorse.training.service.TrainingEnums.SessionType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** What a Groom sees of a session assigned to them, nothing more (BR-114), plus what to confirm (BR-118). */
public record AssignedSessionDto(Long id, Long horseId, String horseName, LocalDate date, LocalTime startTime,
                                 SessionType type, int plannedDurationMin, SessionState state,
                                 AssignmentAck assignmentAck, String plannedEnd, boolean plannedEndNextDay,
                                 Instant endedAt) {
    public static AssignedSessionDto of(AssignedSession a) {
        TrainingSession s = a.session();
        return new AssignedSessionDto(s.getId(), s.getHorseId(), a.horseName(), s.getScheduledDate(),
                s.getPlannedStartTime(), s.getType(), s.getPlannedDurationMin(), s.getState(),
                s.getAssignmentAck(), endOf(s), endsNextDay(s), s.getEndedAt());
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
