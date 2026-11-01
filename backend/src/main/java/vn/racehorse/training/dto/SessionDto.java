package vn.racehorse.training.dto;

import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.service.TrainingEnums.AssignmentAck;
import vn.racehorse.training.service.TrainingEnums.DataSource;
import vn.racehorse.training.service.TrainingEnums.Intensity;
import vn.racehorse.training.service.TrainingEnums.SessionState;
import vn.racehorse.training.service.TrainingEnums.SessionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record SessionDto(Long id, Long planId, Long horseId, LocalDate date, LocalTime startTime, SessionType type,
                         int plannedDurationMin, Integer targetDistanceM, String targetSurface,
                         PersonRef responsiblePerson, SessionState state, Instant startedAt, Instant endedAt,
                         boolean endTimeAutomatic, boolean overtime, boolean lockDuringSession,
                         boolean missingIndicators, String earlyEndReason, String cancelReason, Integer distanceM,
                         Integer durationMin, String surface, Intensity intensity, Short form,
                         Integer avgHeartRate, DataSource avgHeartRateSource, BigDecimal maxSpeed,
                         DataSource maxSpeedSource, BigDecimal weightKg, String comment,
                         AssignmentAck assignmentAck, String plannedEnd, boolean plannedEndNextDay,
                         boolean needsResponsible, PersonRef declinedBy, String declineReason,
                         Instant declinedAt, PersonRef assignedTrainer, boolean canWrite) {
    public static SessionDto of(TrainingSession s, UserAccount trainer, boolean canWrite) {
        return new SessionDto(s.getId(), s.getPlanId(), s.getHorseId(), s.getScheduledDate(),
                s.getPlannedStartTime(), s.getType(), s.getPlannedDurationMin(), s.getTargetDistanceM(),
                s.getTargetSurface(), PersonRef.of(s.getResponsiblePerson()), s.getState(), s.getStartedAt(),
                s.getEndedAt(), s.isEndTimeAutomatic(), s.isOvertime(), s.isLockDuringSession(),
                s.isMissingIndicators(), s.getEarlyEndReason(), s.getCancelReason(), s.getDistanceM(),
                s.getDurationMin(), s.getSurface(), s.getIntensity(), s.getForm(), s.getAvgHeartRate(),
                s.getAvgHeartRateSource(), s.getMaxSpeed(), s.getMaxSpeedSource(), s.getWeightKg(),
                s.getComment(), s.getAssignmentAck(), endOf(s), endsNextDay(s), s.needsResponsible(),
                PersonRef.of(s.getDeclinedBy()), s.getDeclineReason(), s.getDeclinedAt(), PersonRef.of(trainer),
                canWrite);
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
