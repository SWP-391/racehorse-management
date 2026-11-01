package vn.racehorse.stable.dto;

import vn.racehorse.stable.entity.Chore;

import java.time.Instant;

/** {@code onBehalf} is absent for a not-done chore and for the Horse Owner's copy (AC-216). */
public record ChoreDto(Chore chore, boolean done, Long entryId, String groomName, Instant markedAt, Long groomId,
                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                       Boolean onBehalf,
                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                       Boolean backfilled) {
    public static ChoreDto of(ChoreView v) {
        return new ChoreDto(v.chore(), v.done(), v.entryId(), v.groomName(), v.markedAt(), v.groomId(),
                v.onBehalf(), v.backfilled());
    }
}
