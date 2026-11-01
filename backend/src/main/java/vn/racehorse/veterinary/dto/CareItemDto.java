package vn.racehorse.veterinary.dto;

import vn.racehorse.veterinary.entity.CareItem;
import vn.racehorse.veterinary.entity.CareType;
import vn.racehorse.veterinary.service.CareService;

import java.time.LocalDate;
import java.util.Map;

/** The compact care row (BR-187): state, next due, latest completion; notes and batch stay in the history. */
public record CareItemDto(Long id, Long horseId, String horseName, CareType type, String name, int intervalDays,
                          LocalDate nextDue, CareService.DueState state, CareLastCompletion lastCompletion,
                          boolean hasDetails, Long plannedSupplyItemId, String plannedSupplyItemName,
                          Long plannedQuantity) {
    public static CareItemDto of(CareDueView v, CareExtras extras, Map<Long, String> supplyNames) {
        CareItem i = v.item();
        return new CareItemDto(i.getId(), i.getHorseId(), v.horse().getName(), i.getType(), i.getName(),
                i.getIntervalDays(), i.getNextDue(), v.state(), extras == null ? null : extras.last(),
                extras != null && extras.hasDetails(), i.getPlannedSupplyItemId(),
                i.getPlannedSupplyItemId() == null ? null : supplyNames.get(i.getPlannedSupplyItemId()),
                i.getPlannedQuantity());
    }
}
