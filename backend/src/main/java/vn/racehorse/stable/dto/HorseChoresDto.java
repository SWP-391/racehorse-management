package vn.racehorse.stable.dto;

import java.util.List;

public record HorseChoresDto(Long horseId, String horseName, String registrationNumber, StallDto stall,
                             List<ChoreDto> chores) {
}
