package vn.racehorse.schedule.dto;

import java.util.List;

public record ScheduleRequest(List<GroomScheduleRangeInput> ranges) {
}
