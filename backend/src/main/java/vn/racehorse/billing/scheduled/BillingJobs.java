package vn.racehorse.billing.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import vn.racehorse.billing.service.DailyFeeService;

/** Creates the daily fee of finished club days (TECH-167). Disabled in tests, which call the service directly. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class BillingJobs {

    private static final Logger log = LoggerFactory.getLogger(BillingJobs.class);

    private final DailyFeeService dailyFee;

    BillingJobs(DailyFeeService dailyFee) {
        this.dailyFee = dailyFee;
    }

    @Scheduled(fixedDelayString = "${app.jobs.interval-ms:60000}", initialDelayString = "${app.jobs.initial-delay-ms:10000}")
    void run() {
        try {
            int days = dailyFee.run();
            if (days > 0) {
                log.info("daily fee: {} club days processed", days);
            }
        } catch (RuntimeException e) {
            log.error("daily fee failed", e);
        }
    }
}
