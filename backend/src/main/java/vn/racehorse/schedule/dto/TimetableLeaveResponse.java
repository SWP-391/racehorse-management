package vn.racehorse.schedule.dto;

/** The leave of a Groom on a day; `late` is the "đột xuất" label (BR-168). */
public record TimetableLeaveResponse(boolean late, String reason) {
}
