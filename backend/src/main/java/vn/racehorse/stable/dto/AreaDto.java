package vn.racehorse.stable.dto;

public record AreaDto(Long id, String name, int capacity, long occupied) {
    public static AreaDto of(AreaRow r) {
        return new AreaDto(r.area().getId(), r.area().getName(), r.area().getCapacity(), r.occupied());
    }
}
