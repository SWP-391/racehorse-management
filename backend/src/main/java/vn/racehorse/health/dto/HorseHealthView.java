package vn.racehorse.health.dto;

import vn.racehorse.health.entity.ActivityStatus;
import vn.racehorse.health.entity.HealthStatusChange;
import vn.racehorse.health.entity.HorseHealth;
import vn.racehorse.health.entity.LockReviewNote;
import vn.racehorse.health.entity.PendingLockRequest;
import vn.racehorse.health.entity.Readiness;
import vn.racehorse.health.entity.TrainingLock;
import vn.racehorse.horse.entity.Horse;

import java.util.List;

public record HorseHealthView(Horse horse, HorseHealth health, TrainingLock activeLock, ActivityStatus activity,
                              Readiness readiness, List<LockReviewNote> reviewNotes,
                              List<HealthStatusChange> history, PendingLockRequest pendingRequest) {
}
