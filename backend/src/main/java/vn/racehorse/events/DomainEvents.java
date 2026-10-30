package vn.racehorse.events;

/**
 * Events between modules (TECH-023). Listeners run synchronously in the publisher's transaction, so a change and
 * its consequences commit or roll back together.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    /** A training lock became active on the horse (manual, automatic, intake or applied pending request). */
    public record LockActivated(Long horseId) {
    }

    /** The horse's lock was lifted with the Veterinarian's return level (RESUME_AS_IS or RESUME_WITH_ADJUSTMENT). */
    public record LockLifted(Long horseId, String returnLevel) {
    }

    /** A session in progress ended (by the Head Trainer or by auto-close); the horse is idle again. */
    public record SessionEnded(Long horseId, Long sessionId) {
    }

    /** The Veterinarian registered a pending lock request for the session in progress. */
    public record PendingLockRequested(Long horseId, Long sessionId, String reason) {
    }

    /**
     * A Groom was deactivated, given another role or put on leave (BR-136, BR-168, XDEP-017, XDEP-021); {@code reason}
     * is a Vietnamese phrase and {@code onDate} the one day concerned, or null for every session not yet started.
     */
    public record GroomUnavailable(Long accountId, String displayName, String reason, java.time.LocalDate onDate) {
        public GroomUnavailable(Long accountId, String displayName, String reason) {
            this(accountId, displayName, reason, null);
        }
    }

    /** The Club Manager archived the horse. */
    public record HorseArchived(Long horseId) {
    }
}
