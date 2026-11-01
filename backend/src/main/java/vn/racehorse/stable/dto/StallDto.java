package vn.racehorse.stable.dto;

public record StallDto(Long areaId, String area, int slot) {
    public static StallDto of(vn.racehorse.horse.entity.Horse h) {
        return h.getArea() == null ? null : new StallDto(h.getArea().getId(), h.getArea().getName(), h.getStallSlot());
    }
}
