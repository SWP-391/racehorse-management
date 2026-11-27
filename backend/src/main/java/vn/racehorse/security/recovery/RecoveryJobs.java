package vn.racehorse.security.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Removes old recovery rows and dead login_session rows once an hour (TECH-224). Disabled in tests, which call the service directly. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class RecoveryJobs {

    private static final Logger log = LoggerFactory.getLogger(RecoveryJobs.class);

    private final PasswordRecoveryService service;
    private final vn.racehorse.security.LoginSessionService loginSessions;

    RecoveryJobs(PasswordRecoveryService service, vn.racehorse.security.LoginSessionService loginSessions) {
        this.service = service;
        this.loginSessions = loginSessions;
    }

    @Scheduled(fixedDelayString = "${app.recovery.cleanup-interval-ms:3600000}", initialDelayString = "${app.jobs.initial-delay-ms:10000}")
    void run() {
        try {
            service.cleanup();
            loginSessions.purge();
        } catch (RuntimeException e) {
            log.error("recovery clean-up failed", e);
        }
    }
}
