package vn.racehorse.schedule.dto;

public record GroomScheduleRange(int weekday, int startMin, int endMin) {
    public String start() {
        return time(startMin);
    }

    public String end() {
        return time(endMin);
    }

    private static String time(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
