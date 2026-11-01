package vn.racehorse.stock.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.ClubClock;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.stable.entity.SupplyCategory;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stable.repository.SupplyItemRepository;
import vn.racehorse.stock.dto.StockForecast;
import vn.racehorse.stock.dto.StockNoticeResult;
import vn.racehorse.stock.entity.StockNotice;
import vn.racehorse.stock.repository.StockNoticeRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The daily stock notice (BR-176, TECH-160): from 07:00 club time, once per club date, the Club Manager is told which
 * items are Cần nhập or Hết hàng and the Veterinarian which medicines are Hết hàng. Nothing qualifying writes no row
 * and no notice, so the check repeats until something does.
 */
@Service
public class StockNoticeService {

    /** A technical default, not a business rule. */
    static final LocalTime NOTICE_TIME = LocalTime.of(7, 0);

    private final StockForecastService forecasts;
    private final SupplyItemRepository items;
    private final StockNoticeRepository sent;
    private final NotificationService notifications;
    private final ClubClock clock;

    public StockNoticeService(StockForecastService forecasts, SupplyItemRepository items, StockNoticeRepository sent,
                              NotificationService notifications, ClubClock clock) {
        this.forecasts = forecasts;
        this.items = items;
        this.sent = sent;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public StockNoticeResult sendDailyNotice() {
        ZonedDateTime now = clock.toClubTime(clock.now());
        LocalDate day = now.toLocalDate();
        if (now.toLocalTime().isBefore(NOTICE_TIME) || sent.existsById(day)) {
            return StockNoticeResult.NONE;
        }
        Map<Long, StockForecast> all = forecasts.forecast();
        List<SupplyItem> flagged = items.findByActiveTrueOrderByNameAsc().stream()
                .filter(i -> all.get(i.getId()).state() != StockForecastService.State.OK).toList();
        if (flagged.isEmpty()) {
            return StockNoticeResult.NONE;
        }
        sent.saveAndFlush(new StockNotice(day, clock.now(), flagged.size()));
        String list = flagged.stream().map(i -> "\"" + i.getName() + "\" ("
                + (all.get(i.getId()).state() == StockForecastService.State.OUT ? "hết hàng"
                : "còn đủ " + all.get(i.getId()).daysOfCover() + " ngày") + ")").collect(Collectors.joining(", "));
        notifications.notifyRole(Role.CLUB_MANAGER, "Kho cần nhập: " + list + ".", "/supplies");
        List<SupplyItem> medicinesOut = flagged.stream().filter(i -> i.getCategory() == SupplyCategory.MEDICINE
                && all.get(i.getId()).state() == StockForecastService.State.OUT).toList();
        if (!medicinesOut.isEmpty()) {
            notifications.notifyRole(Role.VETERINARIAN, "Thuốc đã hết trong kho: "
                    + medicinesOut.stream().map(i -> "\"" + i.getName() + "\"").collect(Collectors.joining(", ")) + ".", "/care");
        }
        return new StockNoticeResult(true, flagged.size(), medicinesOut.size());
    }
}
