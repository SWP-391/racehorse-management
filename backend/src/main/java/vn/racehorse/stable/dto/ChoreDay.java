package vn.racehorse.stable.dto;

import java.time.LocalDate;
import java.util.List;

public record ChoreDay(LocalDate date, LocalDate today, List<HorseChores> horses) {
}
