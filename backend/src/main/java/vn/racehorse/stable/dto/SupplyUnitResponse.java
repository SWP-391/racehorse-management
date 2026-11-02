package vn.racehorse.stable.dto;

import vn.racehorse.stable.entity.SupplyUnit;

public record SupplyUnitResponse(Long id, String name, boolean active, long itemCount) {
    public static SupplyUnitResponse of(SupplyUnit u, long itemCount) {
        return new SupplyUnitResponse(u.getId(), u.getName(), u.isActive(), itemCount);
    }
}
