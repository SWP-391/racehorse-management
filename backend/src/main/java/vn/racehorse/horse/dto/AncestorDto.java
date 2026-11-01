package vn.racehorse.horse.dto;

import vn.racehorse.horse.entity.PedigreePosition;

public record AncestorDto(PedigreePosition position, AncestorRef horse, String name) {
}
