package vn.racehorse.schedule.dto;

/** A range as sent by the client: ISO weekday and "HH:mm" strings ("24:00" is a valid end). */
public record GroomScheduleRangeInput(Integer weekday, String start, String end) {
}
