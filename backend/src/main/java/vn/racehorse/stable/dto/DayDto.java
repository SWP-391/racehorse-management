package vn.racehorse.stable.dto;

import java.time.LocalDate;
import java.util.List;

public record DayDto(LocalDate date, LocalDate today, List<HorseChoresDto> horses, int page, int size, long total) {
}
