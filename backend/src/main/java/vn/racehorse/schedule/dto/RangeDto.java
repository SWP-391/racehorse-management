package vn.racehorse.schedule.dto;

public record RangeDto(int weekday, String start, String end) {
    public static RangeDto of(GroomScheduleRange r) {
        return new RangeDto(r.weekday(), r.start(), r.end());
    }
}
