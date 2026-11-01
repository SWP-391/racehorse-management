package vn.racehorse.stable.dto;

import vn.racehorse.horse.entity.Horse;

import java.util.List;

public record ChoreHorseRange(Horse horse, List<DayChores> days) {
}
