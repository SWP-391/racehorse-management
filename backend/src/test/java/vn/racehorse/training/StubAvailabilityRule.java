package vn.racehorse.training;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import vn.racehorse.training.service.GroomAvailabilityRule;
import vn.racehorse.training.service.Unavailable;

/**
 * Stands in for the barn-hours and leave rules of the Groom schedule change (TECH-136): inert unless a test fills the
 * static state, and always cleared by {@link GroomAvailabilityTest}.
 */
@Component
class StubAvailabilityRule implements GroomAvailabilityRule {

    /** Groom id to [from, to) hours during which the Groom counts as busy in the barn, on every day. */
    static final Map<Long, LocalTime[]> BARN = new ConcurrentHashMap<>();
    static final Set<Long> LEAVE = ConcurrentHashMap.newKeySet();

    static void reset() {
        BARN.clear();
        LEAVE.clear();
    }

    @Override
    public Optional<Unavailable> check(Long groomId, LocalDate date, LocalTime start, LocalTime end) {
        if (LEAVE.contains(groomId)) {
            return Optional.of(Unavailable.of(Unavailable.Reason.LEAVE, "đang nghỉ phép ngày " + date));
        }
        LocalTime[] busy = BARN.get(groomId);
        if (busy != null && start.isBefore(busy[1]) && busy[0].isBefore(end)) {
            return Optional.of(Unavailable.of(Unavailable.Reason.BARN_HOURS,
                    "đang trong giờ làm chuồng " + busy[0] + " đến " + busy[1]));
        }
        return Optional.empty();
    }
}
