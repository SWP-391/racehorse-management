package vn.racehorse.health.dto;

import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.health.entity.Readiness;
import vn.racehorse.health.entity.TrainingLock;

/** What training needs to decide whether a horse may train now (XDEP-001, XDEP-002). */
public record HealthEligibility(Long horseId, HealthStatus status, TrainingLock activeLock, Readiness readiness,
                          boolean unexamined) {
    public boolean locked() {
        return activeLock != null;
    }
}
