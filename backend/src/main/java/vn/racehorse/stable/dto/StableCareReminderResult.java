package vn.racehorse.stable.dto;

/** {@code evaluated} is false when it is before 18:00 or the day was already evaluated. */
public record StableCareReminderResult(boolean evaluated, int pendingHorses, int pendingChores, int notifiedGrooms) {
    public static final StableCareReminderResult NOT_EVALUATED = new StableCareReminderResult(false, 0, 0, 0);
}
