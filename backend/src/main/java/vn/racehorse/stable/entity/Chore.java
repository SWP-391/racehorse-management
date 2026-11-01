package vn.racehorse.stable.entity;

/** The four fixed daily chores (BR-124, TECH-091). Vietnamese labels live in the client only. */
public enum Chore {
    FEEDING,
    STALL_CLEANING,
    WASHING,
    ICE_SOAK;

    /** Parses a code from a path or body; null when it is not one of the four. */
    public static Chore parse(String code) {
        if (code == null) {
            return null;
        }
        for (Chore c : values()) {
            if (c.name().equalsIgnoreCase(code.trim())) {
                return c;
            }
        }
        return null;
    }
}
