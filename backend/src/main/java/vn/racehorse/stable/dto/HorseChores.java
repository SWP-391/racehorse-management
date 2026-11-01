package vn.racehorse.stable.dto;

import vn.racehorse.horse.entity.Horse;

import java.util.List;

public record HorseChores(Horse horse, List<ChoreView> chores) {
}
