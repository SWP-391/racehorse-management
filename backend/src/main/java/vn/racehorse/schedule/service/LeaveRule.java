package vn.racehorse.schedule.service;

import org.springframework.stereotype.Component;
import vn.racehorse.training.service.GroomAvailabilityRule;
import vn.racehorse.training.service.Unavailable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/** A Groom on recorded leave is unavailable the whole day (BR-162, BR-167, TECH-146). */
@Component
class LeaveRule implements GroomAvailabilityRule {

    private final GroomScheduleService schedules;

    LeaveRule(GroomScheduleService schedules) {
        this.schedules = schedules;
    }

    @Override
    public Optional<Unavailable> check(Long groomId, LocalDate date, LocalTime start, LocalTime end) {
        return schedules.onLeave(groomId, date)
                ? Optional.of(Unavailable.of(Unavailable.Reason.LEAVE, date.toString()))
                : Optional.empty();
    }
}
