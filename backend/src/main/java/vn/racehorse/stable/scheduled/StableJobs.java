package vn.racehorse.stable.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import vn.racehorse.stable.dto.StableCareReminderResult;
import vn.racehorse.stable.service.StableCareService;

/**
 * Checks the 18:00 chore reminder every minute (TECH-096). The day is evaluated once, so a restart or a late check
 * sends nothing twice. Disabled in tests, which call {@link StableCareService} directly with a shifted clock.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class StableJobs {

    private static final Logger log = LoggerFactory.getLogger(StableJobs.class);

    private final StableCareService stableCare;

    StableJobs(StableCareService stableCare) {
        this.stableCare = stableCare;
    }

    @Scheduled(fixedDelayString = "${app.jobs.interval-ms:60000}", initialDelayString = "${app.jobs.initial-delay-ms:10000}")
    void run() {
        try {
            StableCareReminderResult result = stableCare.sendEveningReminder();
            if (result.evaluated()) {
                log.info("chore reminder: {} horses, {} chores pending, {} grooms notified",
                        result.pendingHorses(), result.pendingChores(), result.notifiedGrooms());
            }
        } catch (RuntimeException e) {
            log.error("chore reminder failed", e);
        }
    }
}
