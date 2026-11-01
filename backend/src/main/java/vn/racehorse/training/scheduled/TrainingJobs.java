package vn.racehorse.training.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import vn.racehorse.horse.service.HorseTrainerService;
import vn.racehorse.training.service.SessionService;

/**
 * Runs the time-based session rules every minute (TECH-025). Each step catches up on everything overdue, so state
 * converges after a restart. Disabled in tests, which call {@link SessionService} directly with a shifted clock.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class TrainingJobs {

    private static final Logger log = LoggerFactory.getLogger(TrainingJobs.class);

    private final SessionService sessions;
    private final HorseTrainerService trainers;

    TrainingJobs(SessionService sessions, HorseTrainerService trainers) {
        this.sessions = sessions;
        this.trainers = trainers;
    }

    @Scheduled(fixedDelayString = "${app.jobs.interval-ms:60000}", initialDelayString = "${app.jobs.initial-delay-ms:10000}")
    void run() {
        try {
            int overtime = sessions.flagOvertime();
            int closed = sessions.autoClose();
            int missed = sessions.markMissed();
            int reminders = sessions.sendAssignmentReminders();
            int returned = trainers.endExpiredLoans();
            if (overtime + closed + missed + reminders + returned > 0) {
                log.info("training jobs: {} overtime, {} auto-closed, {} missed, {} reminders, {} loans ended",
                        overtime, closed, missed, reminders, returned);
            }
        } catch (RuntimeException e) {
            log.error("training jobs failed", e);
        }
    }
}
