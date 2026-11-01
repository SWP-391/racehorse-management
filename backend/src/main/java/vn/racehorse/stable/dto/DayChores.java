package vn.racehorse.stable.dto;

import java.time.LocalDate;
import java.util.List;

public record DayChores(LocalDate date, List<ChoreView> chores) {
}
