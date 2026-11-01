package vn.racehorse.ration.dto;

import java.util.List;

public record TodayDto(Long horseId, List<MealDto> meals) {
}
