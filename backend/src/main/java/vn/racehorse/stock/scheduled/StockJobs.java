package vn.racehorse.stock.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import vn.racehorse.stock.dto.StockNoticeResult;
import vn.racehorse.stock.service.StockNoticeService;

/**
 * Checks the daily stock notice every minute (TECH-160). The date is written first, so a restart sends nothing twice.
 * Disabled in tests, which call {@link StockNoticeService} directly with a shifted clock.
 */
@Configuration
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class StockJobs {

    private static final Logger log = LoggerFactory.getLogger(StockJobs.class);

    private final StockNoticeService notices;

    StockJobs(StockNoticeService notices) {
        this.notices = notices;
    }

    @Scheduled(fixedDelayString = "${app.jobs.interval-ms:60000}", initialDelayString = "${app.jobs.initial-delay-ms:10000}")
    void run() {
        try {
            StockNoticeResult result = notices.sendDailyNotice();
            if (result.sent()) {
                log.info("stock notice: {} items flagged, {} medicines out", result.items(), result.medicines());
            }
        } catch (RuntimeException e) {
            log.error("stock notice failed", e);
        }
    }
}
