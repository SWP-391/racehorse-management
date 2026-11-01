package vn.racehorse.billing.dto;

import vn.racehorse.billing.entity.ExamFeeType;

public record FeeTypeDto(Long id, String name, boolean active) {
    public static FeeTypeDto of(ExamFeeType t) {
        return new FeeTypeDto(t.getId(), t.getName(), t.isActive());
    }
}
