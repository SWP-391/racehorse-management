package vn.racehorse.health.service;

import org.springframework.stereotype.Component;
import vn.racehorse.health.entity.ActivityStatus;

import java.util.Optional;

/** Default while no session module provides activity: every horse is idle. Sessions supply a @Primary provider. */
@Component
class IdleActivityStatusProvider implements ActivityStatusProvider {

    @Override
    public ActivityStatus activityOf(Long horseId) {
        return ActivityStatus.IDLE;
    }

    @Override
    public Optional<Long> sessionInProgress(Long horseId) {
        return Optional.empty();
    }
}
