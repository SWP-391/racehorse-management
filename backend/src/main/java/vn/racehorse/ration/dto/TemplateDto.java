package vn.racehorse.ration.dto;

import vn.racehorse.ration.entity.RationTemplate;

import java.util.List;

public record TemplateDto(Long id, String name, String tag, boolean active, boolean needsConfirmation,
                          List<MealDto> meals) {
    public static TemplateDto of(RationTemplate t) {
        return new TemplateDto(t.getId(), t.getName(), t.getTag().name(), t.isActive(), t.isNeedsConfirmation(), t.getMeals().stream()
                .map(m -> new MealDto(m.getName(), m.getTime().toString(), m.getItems().stream().map(i -> new ItemDto(
                        i.getType().name(), i.getSupplyItem().getId(), i.getSupplyItem().getName(),
                        i.getSupplyItem().getUnit(), i.getSupplyItem().isActive(), i.getQuantity())).toList()))
                .toList());
    }
}
