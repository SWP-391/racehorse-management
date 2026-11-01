package vn.racehorse.stable.dto;

import java.time.LocalDate;
import java.util.List;

public record DayChoresDto(LocalDate date, List<ChoreDto> chores) {
}
