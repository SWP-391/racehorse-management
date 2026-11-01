package vn.racehorse.ration.dto;

import vn.racehorse.ration.entity.RationMeal;

import java.util.List;

public record MealDto(String name, String time, List<ItemDto> items) {
    public static MealDto of(RationMeal m) {
        return new MealDto(m.getName(), m.getTime().toString(), m.getItems().stream().map(i -> new ItemDto(
                i.getType().name(), i.getSupplyItem().getId(), i.getSupplyItem().getName(),
                i.getSupplyItem().getUnit(), i.getSupplyItem().isActive(), i.getQuantity())).toList());
    }
}
