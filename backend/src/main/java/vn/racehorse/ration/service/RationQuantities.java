package vn.racehorse.ration.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.ration.entity.RationItem;
import vn.racehorse.ration.entity.RationMeal;
import vn.racehorse.ration.entity.RationVersion;
import vn.racehorse.ration.repository.RationVersionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a ration takes out of stock in one day, in whole units of each supply item (BR-173, TECH-157). Rations may hold
 * two decimals but stock is whole numbers, so the daily total of each item is rounded up once for the whole day
 * (6.5 kg becomes 7 kg). No permission check: the chore and forecast services decide who may ask.
 */
@Service
public class RationQuantities {

    private final RationVersionRepository versions;

    public RationQuantities(RationVersionRepository versions) {
        this.versions = versions;
    }

    /** The version in force on a date: the latest one whose effective date is not after it (BR-140). */
    @Transactional(readOnly = true)
    public Map<Long, Long> dailyUse(Long horseId, LocalDate day) {
        return versions.findFirstByHorseIdAndEffectiveDateLessThanEqualOrderByVersionNoDesc(horseId, day)
                .map(RationQuantities::dailyTotals).orElse(Map.of());
    }

    /** Supply item id to the whole units the version takes in one day, summed over all its meals. */
    public static Map<Long, Long> dailyTotals(RationVersion version) {
        Map<Long, BigDecimal> exact = new LinkedHashMap<>();
        for (RationMeal meal : version.getMeals()) {
            for (RationItem item : meal.getItems()) {
                exact.merge(item.getSupplyItem().getId(), item.getQuantity(), BigDecimal::add);
            }
        }
        Map<Long, Long> whole = new LinkedHashMap<>();
        exact.forEach((id, qty) -> whole.put(id, qty.setScale(0, RoundingMode.CEILING).longValueExact()));
        return whole;
    }
}
