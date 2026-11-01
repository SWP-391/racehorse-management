package vn.racehorse.training.service;

import org.springframework.http.HttpStatus;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.training.dto.SessionTimesSlot;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.service.TrainingEnums.SessionState;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Planned time ranges, overlap and ordering of sessions (BR-120, BR-121, BR-122; TECH-082, TECH-083, TECH-087). */
final class SessionTimes {

    private SessionTimes() {
    }

    /** States that never take up the horse's or the person's time (BR-121). */
    static final Set<SessionState> NOT_COUNTED =
            EnumSet.of(SessionState.CANCELLED, SessionState.MISSED, SessionState.MISSED_DUE_TO_LOCK);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * The first counted session among {@code others} whose slot overlaps {@code candidate}, using {@code moved} for
     * sessions whose date is about to change. The candidate itself is skipped by id.
     */
    static Optional<TrainingSession> firstOverlap(SessionTimesSlot candidate, Collection<TrainingSession> others,
                                                  Map<Long, LocalDate> moved) {
        if (!candidate.timed()) {
            return Optional.empty();
        }
        return others.stream()
                .filter(o -> !Objects.equals(o.getId(), candidate.sessionId()) && !NOT_COUNTED.contains(o.getState()))
                .filter(o -> candidate.overlaps(new SessionTimesSlot(o.getId(), moved.getOrDefault(o.getId(), o.getScheduledDate()),
                        o.getPlannedStartTime(), o.getPlannedDurationMin())))
                .min(Comparator.comparing(TrainingSession::getScheduledDate)
                        .thenComparing(TrainingSession::getPlannedStartTime));
    }

    /** Refuses with 409 SESSION_OVERLAP naming the other session of the same horse (BR-121, TECH-083). */
    static void requireNoOverlap(SessionTimesSlot candidate, Collection<TrainingSession> horseSessions, Map<Long, LocalDate> moved) {
        firstOverlap(candidate, horseSessions, moved).ifPresent(other -> {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_OVERLAP", "Trùng giờ với buổi tập "
                    + describe(other, SessionService.TYPE_NAMES.get(other.getType())) + " của ngựa này.");
        });
    }

    /** "Phi ngày 21/10/2026 lúc 07:00" - how a refusal or warning names the other session. */
    static String describe(TrainingSession s, String typeName) {
        return typeName + " ngày " + s.getScheduledDate().format(DATE)
                + (s.getPlannedStartTime() == null ? "" : " lúc " + s.getPlannedStartTime().format(TIME));
    }

    /**
     * Date in the given direction, then on one date the sessions without a start time first, then by time, then id
     * (BR-120, TECH-087).
     */
    static Comparator<TrainingSession> byDateThenTime(boolean dateAscending) {
        Comparator<TrainingSession> date = Comparator.comparing(TrainingSession::getScheduledDate);
        return (dateAscending ? date : date.reversed())
                .thenComparing(TrainingSession::getPlannedStartTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(TrainingSession::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
