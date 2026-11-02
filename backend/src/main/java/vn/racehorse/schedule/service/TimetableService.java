package vn.racehorse.schedule.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.horse.service.HorseTrainerService;
import vn.racehorse.schedule.dto.RangeDto;
import vn.racehorse.schedule.dto.TimetableDayResponse;
import vn.racehorse.schedule.dto.TimetableItemResponse;
import vn.racehorse.schedule.dto.TimetableLeaveResponse;
import vn.racehorse.schedule.dto.TimetableResponse;
import vn.racehorse.schedule.entity.GroomLeave;
import vn.racehorse.schedule.repository.GroomLeaveRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.repository.TrainingSessionRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The read-only timetable of the signed-in Groom (barn hours, leave, own sessions) or Head Trainer (sessions of the
 * horses assigned that day), built from existing data (BR-200 to BR-202). It takes no user id: it can only be asked
 * for one's own.
 */
@Service
public class TimetableService {

    static final int MAX_DAYS = 31;

    private final AccessGuard access;
    private final TrainingSessionRepository sessions;
    private final HorseRepository horses;
    private final HorseTrainerService trainers;
    private final GroomScheduleService schedule;
    private final GroomLeaveRepository leaves;

    public TimetableService(AccessGuard access, TrainingSessionRepository sessions, HorseRepository horses,
                            HorseTrainerService trainers, GroomScheduleService schedule, GroomLeaveRepository leaves) {
        this.access = access;
        this.sessions = sessions;
        this.horses = horses;
        this.trainers = trainers;
        this.schedule = schedule;
        this.leaves = leaves;
    }

    @Transactional(readOnly = true)
    public TimetableResponse mine(LocalDate from, LocalDate to) {
        AccountPrincipal user = access.require(Permission.VIEW_OWN_TIMETABLE);
        if (from == null || to == null) {
            throw ApiException.invalid("RANGE_REQUIRED", "from", "Chọn khoảng ngày cần xem.");
        }
        if (from.isAfter(to)) {
            throw ApiException.invalid("RANGE_INVERTED", "to", "Ngày kết thúc không được trước ngày bắt đầu.");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to.plusDays(1))) {
            throw ApiException.invalid("RANGE_TOO_LONG", "to", "Chỉ xem được tối đa " + MAX_DAYS + " ngày một lần.");
        }
        boolean groom = user.role() == Role.GROOM;
        List<TrainingSession> found = groom
                ? sessions.findByResponsiblePersonIdAndScheduledDateBetween(user.id(), from, to)
                : trainerSessions(user.id(), from, to);

        Set<Long> horseIds = found.stream().map(TrainingSession::getHorseId).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        horses.findAllById(horseIds).forEach(h -> names.put(h.getId(), h.getName()));

        Map<LocalDate, GroomLeave> leaveByDay = new HashMap<>();
        if (groom) {
            leaves.findByAccountIdAndLeaveDateBetweenOrderByLeaveDateAscIdAsc(user.id(), from, to).stream()
                    .filter(l -> !l.isCancelled()).forEach(l -> leaveByDay.putIfAbsent(l.getLeaveDate(), l));
        }

        List<TimetableDayResponse> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            LocalDate d = day;
            List<TimetableItemResponse> items = found.stream().filter(s -> s.getScheduledDate().equals(d))
                    .sorted(Comparator
                            .comparing(TrainingSession::getPlannedStartTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
                            .thenComparing(TrainingSession::getId))
                    .map(s -> TimetableItemResponse.of(s, names.get(s.getHorseId()), !groom)).toList();
            List<RangeDto> hours = groom ? schedule.rangesOn(user.id(), d).stream().map(RangeDto::of).toList() : List.of();
            GroomLeave leave = leaveByDay.get(d);
            days.add(new TimetableDayResponse(d, hours,
                    leave == null ? null : new TimetableLeaveResponse(leave.isLate(), leave.getReason()), items));
        }
        return new TimetableResponse(from, to, days);
    }

    /** Sessions of the horses assigned to the Head Trainer on the session's own date (the shared rule, D2). */
    private List<TrainingSession> trainerSessions(Long trainerId, LocalDate from, LocalDate to) {
        List<Horse> assigned = horses.assignedTo(trainerId);
        if (assigned.isEmpty()) {
            return List.of();
        }
        Map<Long, Horse> byId = assigned.stream().collect(Collectors.toMap(Horse::getId, h -> h));
        return sessions.findByHorseIdInAndScheduledDateBetween(byId.keySet(), from, to).stream()
                .filter(s -> trainers.isAssignedOn(byId.get(s.getHorseId()), trainerId, s.getScheduledDate())).toList();
    }
}
