package vn.racehorse.stable.dto;

import java.util.List;

public record BarnAreaDto(Long id, String name, int capacity, List<SlotDto> slots) {
}
