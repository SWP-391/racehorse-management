package vn.racehorse.ration.entity;

/** Condition a ration is meant for (BR-248). */
public enum RationTag {
    NORMAL, WATCH, RECOVERY, ISOLATION;

    public static RationTag parse(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String label() {
        return switch (this) {
            case NORMAL -> "Thường";
            case WATCH -> "Theo dõi";
            case RECOVERY -> "Phục hồi";
            case ISOLATION -> "Cách ly";
        };
    }
}
