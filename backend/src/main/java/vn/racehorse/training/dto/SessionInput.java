package vn.racehorse.training.dto;

import vn.racehorse.training.service.TrainingEnums.SessionType;

import java.time.LocalDate;
import java.time.LocalTime;

public record SessionInput(LocalDate date, LocalTime startTime, SessionType type, Integer plannedDurationMin,
                           Integer targetDistanceM, String targetSurface, Long responsiblePersonId, boolean draft,
                           boolean acknowledgeWarning) {
}
