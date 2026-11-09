package vn.racehorse.simulation.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import vn.racehorse.simulation.service.SimulationService;

/** Raises alerts even when nobody is watching the live view; disabled in tests like the training jobs. */
@Configuration
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class SimulationJobs {

    private static final Logger log = LoggerFactory.getLogger(SimulationJobs.class);

    private final SimulationService simulation;

    SimulationJobs(SimulationService simulation) {
        this.simulation = simulation;
    }

    @Scheduled(fixedDelayString = "${app.jobs.simulation-interval-ms:5000}", initialDelayString = "${app.jobs.initial-delay-ms:10000}")
    void run() {
        try {
            int raised = simulation.evaluate();
            if (raised > 0) {
                log.info("simulation: {} alert(s) raised", raised);
            }
        } catch (RuntimeException e) {
            log.error("simulation job failed", e);
        }
    }
}
