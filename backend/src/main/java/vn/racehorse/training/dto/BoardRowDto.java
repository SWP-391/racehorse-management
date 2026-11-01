package vn.racehorse.training.dto;

import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.health.entity.Readiness;

import java.util.List;

public record BoardRowDto(Long horseId, String horseName, HealthStatus status, Readiness readiness, boolean locked,
                          boolean newUnexamined, PlanDto currentPlan, List<SessionDto> recentSessions,
                          List<SessionDto> trend, PersonRef assignedTrainer, boolean canWrite) {
}
