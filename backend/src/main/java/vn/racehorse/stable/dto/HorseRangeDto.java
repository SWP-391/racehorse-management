package vn.racehorse.stable.dto;

import java.util.List;

public record HorseRangeDto(Long horseId, String horseName, String registrationNumber, List<DayChoresDto> days) {
}
