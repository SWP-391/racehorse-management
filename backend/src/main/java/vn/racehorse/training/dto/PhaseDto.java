package vn.racehorse.training.dto;

import vn.racehorse.training.entity.PlanPhase;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PhaseDto(Long id, int ordinal, String name, LocalDate startDate, LocalDate endDate, String goal,
                       int distanceM, BigDecimal weeklyVolumeKm, String surface) {
    public static PhaseDto of(PlanPhase p) {
        return new PhaseDto(p.getId(), p.getOrdinal(), p.getName(), p.getStartDate(), p.getEndDate(), p.getGoal(),
                p.getDistanceM(), p.getWeeklyVolumeKm(), p.getSurface());
    }
}
