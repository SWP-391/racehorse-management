package vn.racehorse.health.service;

import vn.racehorse.health.entity.ActivityStatus;

import java.util.Optional;

/**
 * Tells the health module whether a horse is training. Sessions provide the real implementation (@Primary);
 * without them every horse is idle.
 */
public interface ActivityStatusProvider {

    ActivityStatus activityOf(Long horseId);

    /** The session in progress for the horse, if any. */
    Optional<Long> sessionInProgress(Long horseId);
}
