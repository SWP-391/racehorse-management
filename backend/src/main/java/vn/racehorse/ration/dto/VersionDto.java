package vn.racehorse.ration.dto;

import vn.racehorse.ration.entity.RationVersion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record VersionDto(int version, LocalDate effectiveDate, String savedByName, Instant savedAt,
                         List<MealDto> meals) {
    public static VersionDto of(RationVersion v) {
        return new VersionDto(v.getVersionNo(), v.getEffectiveDate(), v.getSavedBy().getDisplayName(),
                v.getSavedAt(), v.getMeals().stream().map(MealDto::of).toList());
    }
}
