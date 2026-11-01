package vn.racehorse.stable.dto;

import java.util.List;

public record BarnDto(List<BarnAreaDto> areas, List<BarnHorseDto> unplaced, boolean canPlace) {
}
