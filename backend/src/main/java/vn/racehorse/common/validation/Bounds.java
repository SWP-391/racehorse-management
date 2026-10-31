package vn.racehorse.common.validation;

/**
 * Plausibility bounds where the business rules give no limit (input-validation, design D6). They only stop an obvious
 * slip such as a heart rate of 1500; none of them is a business rule and the Veterinarian or Head Trainer confirms them.
 * The same values sit in the client's {@code BOUNDS} (frontend/src/lib/validate.ts) and the wording is the client's.
 */
public final class Bounds {

    public static final String MIN_MESSAGE = "Giá trị tối thiểu là {value}.";
    public static final String MAX_MESSAGE = "Giá trị tối đa là {value}.";

    public static final int HEART_RATE_MIN = 20;
    public static final int HEART_RATE_MAX = 300;
    public static final String SPEED_MAX = "100";
    public static final String WEIGHT_MIN = "100";
    public static final String WEIGHT_MAX = "1500";
    public static final int DISTANCE_MAX = 50_000;
    public static final int DURATION_MAX = 600;
    public static final String VOLUME_MAX = "1000";
    public static final int INTERVAL_MAX = 3650;
    public static final String QUANTITY_MAX = "1000000";
    public static final String PRICE_MAX = "1000000000";
    public static final String SETTING_DAYS_MAX = "365";
    public static final String SETTING_HOURS_MAX = "168";
    public static final int SETTING_MINUTES_MAX = 1440;

    /** A typo in the year (0202, 20255) is never meant; the dates of the club lie inside this window. */
    public static final String DATE_MIN = "1990-01-01";
    public static final String DATE_MAX = "2100-12-31";

    private Bounds() {
    }
}
