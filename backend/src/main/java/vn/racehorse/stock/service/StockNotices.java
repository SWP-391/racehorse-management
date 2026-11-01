package vn.racehorse.stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.ClubClock;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.stock.exception.InsufficientStockException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The urgent notice to the Club Manager when a feeding mark is refused for short stock (BR-176). It runs in its own
 * transaction so it survives the rollback of the refused mark; a failure here is logged and never hides the refusal.
 * The same item is not announced twice within ten minutes (a technical default that stops a flood when a Groom retries).
 */
@Component
public class StockNotices {

    private static final Logger log = LoggerFactory.getLogger(StockNotices.class);
    static final Duration QUIET = Duration.ofMinutes(10);

    private final NotificationService notifications;
    private final ClubClock clock;
    private final Map<Long, Instant> lastSent = new ConcurrentHashMap<>();

    public StockNotices(NotificationService notifications, ClubClock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void shortage(InsufficientStockException.Shortage s, String horseName) {
        try {
            Instant now = clock.now();
            Instant last = lastSent.get(s.itemId());
            if (last != null && Duration.between(last, now).compareTo(QUIET) < 0) {
                return;
            }
            lastSent.put(s.itemId(), now);
            notifications.notifyRole(Role.CLUB_MANAGER, "Khẩn: không đủ \"" + s.name() + "\" để cho ăn"
                    + (horseName == null ? "" : " (" + horseName + ")") + ", thiếu " + s.missing() + " " + s.unit()
                    + ". Cần nhập kho ngay.", "/supplies");
        } catch (RuntimeException e) {
            log.warn("could not write the urgent stock notice", e);
        }
    }
}
