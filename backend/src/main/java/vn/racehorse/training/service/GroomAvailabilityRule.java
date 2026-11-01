package vn.racehorse.training.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * One reason a Groom may be unavailable for a time range on one club day (BR-162, TECH-136). Every bean of this type
 * is consulted by {@link GroomAvailability}; the Groom schedule change adds the barn-hours and leave rules here.
 * {@code end} is exclusive; a range that crosses midnight arrives split into one call per day, with
 * {@link LocalTime#MAX} as the end of the first day and midnight as the start of the next.
 */
public interface GroomAvailabilityRule {

    Optional<Unavailable> check(Long groomId, LocalDate date, LocalTime start, LocalTime end);
}
