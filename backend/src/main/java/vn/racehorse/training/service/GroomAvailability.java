package vn.racehorse.training.service;

import org.springframework.stereotype.Component;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.training.dto.SessionTimesSlot;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.repository.TrainingSessionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Whether a Groom can take a session: no other session of theirs overlaps (BR-161) and none of the registered rules
 * objects (barn hours, leave; BR-162). A session without a start time is not checked, and a Groom without any
 * schedule is free (BR-162).
 */
@Component
public class GroomAvailability {

    private final TrainingSessionRepository sessions;
    private final HorseRepository horses;
    private final List<GroomAvailabilityRule> rules;

    public GroomAvailability(TrainingSessionRepository sessions, HorseRepository horses,
                             List<GroomAvailabilityRule> rules) {
        this.sessions = sessions;
        this.horses = horses;
        this.rules = rules;
    }

    /**
     * @param excludeSessionId the session being changed, which must not collide with itself
     * @param horseId          the horse of the session; its own sessions never count, BR-121 handles them
     */
    public Optional<Unavailable> check(Long groomId, LocalDate date, LocalTime start, int durationMin,
                                       Long excludeSessionId, Long horseId) {
        if (start == null) {
            return Optional.empty();
        }
        Optional<Unavailable> overlap = overlap(groomId, date, start, durationMin, excludeSessionId, horseId);
        if (overlap.isPresent()) {
            return overlap;
        }
        LocalDateTime from = date.atTime(start);
        LocalDateTime to = from.plusMinutes(durationMin);
        for (LocalDate day = from.toLocalDate(); !day.isAfter(to.toLocalDate()); day = day.plusDays(1)) {
            LocalTime pieceStart = day.equals(from.toLocalDate()) ? from.toLocalTime() : LocalTime.MIDNIGHT;
            LocalTime pieceEnd = day.equals(to.toLocalDate()) ? to.toLocalTime() : LocalTime.MAX;
            if (day.equals(to.toLocalDate()) && pieceEnd.equals(LocalTime.MIDNIGHT)) {
                continue; // the range ends exactly at midnight: nothing falls on the next day
            }
            for (GroomAvailabilityRule rule : rules) {
                Optional<Unavailable> hit = rule.check(groomId, day, pieceStart, pieceEnd);
                if (hit.isPresent()) {
                    return hit;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Unavailable> overlap(Long groomId, LocalDate date, LocalTime start, int durationMin,
                                          Long excludeSessionId, Long horseId) {
        List<TrainingSession> theirs = sessions.findByResponsiblePersonIdOrderByScheduledDateDescIdDesc(groomId)
                .stream().filter(o -> !o.getHorseId().equals(horseId)).toList();
        SessionTimesSlot slot = new SessionTimesSlot(excludeSessionId, date, start, durationMin);
        return SessionTimes.firstOverlap(slot, theirs, Map.of()).map(other -> new Unavailable(
                Unavailable.Reason.OVERLAP,
                "Đã có buổi tập trùng giờ: "
                        + SessionTimes.describe(other, SessionService.TYPE_NAMES.get(other.getType())) + " của ngựa \""
                        + horses.findById(other.getHorseId()).map(Horse::getName).orElse("?") + "\".",
                other.getId()));
    }
}
