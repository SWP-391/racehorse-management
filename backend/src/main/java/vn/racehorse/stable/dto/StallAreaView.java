package vn.racehorse.stable.dto;

import vn.racehorse.horse.entity.Area;

import java.util.List;

public record StallAreaView(Area area, List<StallSlotView> slots) {
}
