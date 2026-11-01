package vn.racehorse.stock.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.common.ClubClock;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.ration.entity.RationVersion;
import vn.racehorse.ration.repository.RationVersionRepository;
import vn.racehorse.ration.service.RationQuantities;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stable.repository.SupplyItemRepository;
import vn.racehorse.stock.dto.StockForecast;
import vn.racehorse.stock.entity.ExpectedReceipt;
import vn.racehorse.stock.repository.ExpectedReceiptRepository;
import vn.racehorse.training.service.ClubSettings;
import vn.racehorse.veterinary.entity.CareItem;
import vn.racehorse.veterinary.repository.CareItemRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Còn đủ N ngày" for every supply item (BR-175, BR-177, TECH-159): stock plus the deliveries still expected, minus the
 * medicine planned for care items falling due within the warning window, over the daily use of all current rations.
 * Two states only, because the club has one threshold: Cần nhập and Hết hàng. Computed per request, no cache.
 */
@Service
public class StockForecastService {

    public enum State { OK, RESTOCK, OUT }

    private final SupplyItemRepository items;
    private final HorseRepository horses;
    private final RationVersionRepository versions;
    private final CareItemRepository careItems;
    private final ExpectedReceiptRepository expectedReceipts;
    private final ClubSettings settings;
    private final ClubClock clock;

    public StockForecastService(SupplyItemRepository items, HorseRepository horses, RationVersionRepository versions,
                                CareItemRepository careItems, ExpectedReceiptRepository expectedReceipts,
                                ClubSettings settings, ClubClock clock) {
        this.items = items;
        this.horses = horses;
        this.versions = versions;
        this.careItems = careItems;
        this.expectedReceipts = expectedReceipts;
        this.settings = settings;
        this.clock = clock;
    }

    /** One forecast per item id; no permission check, the callers decide who sees what. */
    @Transactional(readOnly = true)
    public Map<Long, StockForecast> forecast() {
        int threshold = settings.stockWarningDays();
        LocalDate today = clock.today();
        List<Long> horseIds = horses.findAllByOrderByNameAsc().stream().filter(h -> !h.isArchived()).map(Horse::getId).toList();

        Map<Long, Long> dailyUse = new HashMap<>();
        if (!horseIds.isEmpty()) {
            Map<Long, RationVersion> latest = new LinkedHashMap<>();
            for (RationVersion v : versions.findByHorseIdInOrderByVersionNoDesc(horseIds)) {
                latest.putIfAbsent(v.getHorseId(), v);
            }
            latest.values().forEach(v -> RationQuantities.dailyTotals(v).forEach((id, qty) -> dailyUse.merge(id, qty, Long::sum)));
        }
        Map<Long, Long> expected = new HashMap<>();
        expectedReceipts.findByStateAndExpectedDateGreaterThanEqual(ExpectedReceipt.State.EXPECTED, today)
                .forEach(e -> expected.merge(e.getSupplyItemId(), e.getQuantity(), Long::sum));
        Map<Long, Long> careDemand = new HashMap<>();
        for (CareItem c : careItems.findByPlannedSupplyItemIdIsNotNullAndNextDueLessThanEqual(today.plusDays(threshold))) {
            if (horseIds.contains(c.getHorseId())) {
                careDemand.merge(c.getPlannedSupplyItemId(), c.getPlannedQuantity(), Long::sum);
            }
        }

        Map<Long, StockForecast> out = new LinkedHashMap<>();
        for (SupplyItem item : items.findAllByOrderByNameAsc()) {
            long use = dailyUse.getOrDefault(item.getId(), 0L);
            long exp = expected.getOrDefault(item.getId(), 0L);
            long care = careDemand.getOrDefault(item.getId(), 0L);
            long effective = item.getQuantity() + exp - care;
            Long days = use > 0 ? (effective <= 0 ? 0L : effective / use) : null;
            State state;
            if (item.getQuantity() == 0) {
                state = State.OUT;
            } else if ((days != null && days <= threshold) || (care > 0 && effective <= 0)) {
                state = State.RESTOCK;
            } else {
                state = State.OK;
            }
            out.put(item.getId(), new StockForecast(item.getId(), use, exp, care, days, state));
        }
        return out;
    }
}
