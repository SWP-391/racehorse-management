package vn.racehorse.schedule.service;

import org.springframework.stereotype.Component;
import vn.racehorse.training.service.GroomAvailabilityRule;
import vn.racehorse.training.service.Unavailable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * A Groom is busy while inside their fixed barn hours (BR-162, TECH-146). The check is half-open: a session that starts
 * exactly when a range ends is free. A Groom with no hours that weekday, or no schedule at all, is free.
 */
@Component
class BarnHoursRule implements GroomAvailabilityRule {

    private final GroomScheduleService schedules;

    BarnHoursRule(GroomScheduleService schedules) {
        this.schedules = schedules;
    }

    @Override
    public Optional<Unavailable> check(Long groomId, LocalDate date, LocalTime start, LocalTime end) {
        int from = start.getHour() * 60 + start.getMinute();
        int to = end.equals(LocalTime.MAX) ? 1440 : end.getHour() * 60 + end.getMinute();
        return schedules.rangesOn(groomId, date).stream()
                .filter(r -> from < r.endMin() && to > r.startMin())
                .findFirst()
                .map(r -> Unavailable.of(Unavailable.Reason.BARN_HOURS, r.start() + "-" + r.end()));
    }
}
