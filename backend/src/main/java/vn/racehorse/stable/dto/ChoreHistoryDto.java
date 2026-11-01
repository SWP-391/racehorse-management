package vn.racehorse.stable.dto;

import vn.racehorse.stable.entity.Chore;
import vn.racehorse.stable.entity.ChoreEntry;

import java.time.Instant;
import java.time.LocalDate;

public record ChoreHistoryDto(Long entryId, LocalDate date, Chore chore, String groomName, Instant markedAt,
                         Instant cancelledAt, String cancelReason, String cancelledByName, Long groomId,
                         boolean onBehalf, boolean backfilled, String backfillReason) {
    public static ChoreHistoryDto of(ChoreEntry e) {
        return new ChoreHistoryDto(e.getId(), e.getChoreDate(), e.getChore(), e.getGroom().getDisplayName(),
                e.getMarkedAt(), e.getCancelledAt(), e.getCancelReason(),
                e.getCancelledBy() == null ? null : e.getCancelledBy().getDisplayName(), e.getGroom().getId(),
                e.isOnBehalf(), e.isBackfilled(), e.getBackfillReason());
    }
}
