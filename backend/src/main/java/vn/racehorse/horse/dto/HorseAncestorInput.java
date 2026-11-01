package vn.racehorse.horse.dto;

import vn.racehorse.horse.entity.PedigreePosition;

public record HorseAncestorInput(PedigreePosition position, Long horseId, String name) {
}
