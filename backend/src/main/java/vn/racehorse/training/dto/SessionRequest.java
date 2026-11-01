package vn.racehorse.training.dto;

import jakarta.validation.constraints.Max;
import vn.racehorse.common.validation.Bounds;
import vn.racehorse.common.validation.ClubDate;
import vn.racehorse.training.service.TrainingEnums.SessionType;

import java.time.LocalDate;
import java.time.LocalTime;

public record SessionRequest(@ClubDate LocalDate date, LocalTime startTime, SessionType type,
                             @Max(value = Bounds.DURATION_MAX, message = Bounds.MAX_MESSAGE) Integer plannedDurationMin,
                             @Max(value = Bounds.DISTANCE_MAX, message = Bounds.MAX_MESSAGE) Integer targetDistanceM,
                             String targetSurface, Long responsiblePersonId, Boolean draft,
                             Boolean acknowledgeWarning) {
}
