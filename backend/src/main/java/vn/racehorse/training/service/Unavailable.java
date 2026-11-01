package vn.racehorse.training.service;

/** Why a Groom cannot be responsible for a session at a given time (BR-161, BR-162). */
public record Unavailable(Reason reason, String detail, Long sessionId) {

    public enum Reason {
        /** During their fixed barn hours (added by the Groom schedule change). */
        BARN_HOURS,
        /** On recorded leave that day (added by the Groom schedule change). */
        LEAVE,
        /** Another session of theirs overlaps. */
        OVERLAP
    }

    public static Unavailable of(Reason reason, String detail) {
        return new Unavailable(reason, detail, null);
    }
}
