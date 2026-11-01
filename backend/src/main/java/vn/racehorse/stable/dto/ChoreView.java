package vn.racehorse.stable.dto;

import vn.racehorse.stable.entity.Chore;

/** What one viewer may see of one chore; the Owner's copy carries no entry, Groom or time (TECH-094). */
public record ChoreView(Chore chore, boolean done, Long entryId, String groomName, java.time.Instant markedAt,
                        Long groomId, Boolean onBehalf, Boolean backfilled) {
    public ChoreView(Chore chore, boolean done, Long entryId, String groomName, java.time.Instant markedAt,
                     Long groomId) {
        this(chore, done, entryId, groomName, markedAt, groomId, null, null);
    }

    public ChoreView(Chore chore, boolean done, Long entryId, String groomName, java.time.Instant markedAt,
                     Long groomId, Boolean onBehalf) {
        this(chore, done, entryId, groomName, markedAt, groomId, onBehalf, null);
    }
}
