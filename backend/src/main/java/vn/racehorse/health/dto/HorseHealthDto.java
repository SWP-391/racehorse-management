package vn.racehorse.health.dto;

import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.health.entity.ActivityStatus;
import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.health.entity.PendingLockRequest;
import vn.racehorse.health.entity.Readiness;
import vn.racehorse.health.entity.TrainingLock;

import java.util.List;

public record HorseHealthDto(Long horseId, String horseName, String registrationNumber, HealthStatus status,
                             LockDto lock, PendingLockDto pendingLock, ActivityStatus activity,
                             Readiness readiness, boolean newUnexamined, List<HistoryDto> history) {
    private static final String SYSTEM = "Hệ thống";

    private static String name(UserAccount account) {
        return account == null ? SYSTEM : account.getDisplayName();
    }

    public static HorseHealthDto of(HorseHealthView v) {
        TrainingLock l = v.activeLock();
        LockDto lock = l == null ? null : new LockDto(l.getId(), l.getKind(), l.getReason(), name(l.getPlacedBy()),
                l.getPlacedAt(), l.getReviewDate(), v.reviewNotes().stream()
                .map(n -> new ReviewNoteDto(name(n.getAuthor()), n.getNote(), n.getCreatedAt())).toList());
        PendingLockRequest p = v.pendingRequest();
        PendingLockDto pendingLock = p == null ? null
                : new PendingLockDto(p.getReason(), name(p.getRequestedBy()), p.getRequestedAt(), p.getSessionId());
        return new HorseHealthDto(v.horse().getId(), v.horse().getName(), v.horse().getRegistrationNumber(),
                v.health().getStatus(), lock, pendingLock, v.activity(), v.readiness(), v.health().isUnexamined(),
                v.history().stream().map(h -> new HistoryDto(h.getFromStatus(), h.getToStatus(), h.getReason(),
                        name(h.getChangedBy()), h.getChangedAt())).toList());
    }
}
