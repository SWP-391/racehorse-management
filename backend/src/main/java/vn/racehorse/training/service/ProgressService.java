package vn.racehorse.training.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.health.dto.HealthEligibility;
import vn.racehorse.health.service.HealthService;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.training.entity.TrainingPlan;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.repository.TrainingPlanRepository;
import vn.racehorse.training.repository.TrainingSessionRepository;
import vn.racehorse.training.service.TrainingEnums.PlanState;
import vn.racehorse.training.service.TrainingEnums.SessionState;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Head Trainer's progress board (BR-042) and the Owner's schedule view (BR-082). */
@Service
public class ProgressService {

    static final Set<SessionState> DONE = EnumSet.of(SessionState.COMPLETED, SessionState.AUTO_CLOSED);
    private static final int RECENT = 5;

    private final HorseRepository horses;
    private final TrainingPlanRepository plans;
    private final TrainingSessionRepository sessions;
    private final HealthService health;
    private final AccessGuard access;

    public ProgressService(HorseRepository horses, TrainingPlanRepository plans, TrainingSessionRepository sessions,
                           HealthService health, AccessGuard access) {
        this.horses = horses;
        this.plans = plans;
        this.sessions = sessions;
        this.health = health;
        this.access = access;
    }

    public record BoardRow(Horse horse, HealthEligibility eligibility, TrainingPlan currentPlan,
                           List<TrainingSession> recentSessions, List<TrainingSession> trend) {
    }

    @Transactional(readOnly = true)
    public List<BoardRow> board() {
        access.require(Permission.VIEW_PROGRESS_BOARD);
        return horses.findAllByOrderByNameAsc().stream().filter(h -> !h.isArchived()).map(h -> {
            List<TrainingPlan> horsePlans = plans.findByHorseIdOrderByCreatedAtDesc(h.getId());
            TrainingPlan current = horsePlans.stream().filter(p -> p.getState() == PlanState.ACTIVE).findFirst()
                    .or(() -> horsePlans.stream().filter(p -> p.getState() == PlanState.DRAFT).findFirst())
                    .orElse(null);
            List<TrainingSession> done = sessions.findByHorseIdOrderByScheduledDateDescIdDesc(h.getId()).stream()
                    .filter(s -> DONE.contains(s.getState())).toList();
            List<TrainingSession> trend = done.stream()
                    .sorted(Comparator.comparing(TrainingSession::getScheduledDate).thenComparing(TrainingSession::getId))
                    .toList();
            return new BoardRow(h, health.eligibility(h.getId()), current, done.stream().limit(RECENT).toList(), trend);
        }).toList();
    }

    /** Sessions and comments of one horse; an Owner only for horses they own (PERM-012, BR-006). */
    @Transactional(readOnly = true)
    public List<TrainingSession> schedule(Long horseId) {
        AccountPrincipal user = access.require(Permission.VIEW_PLANS);
        Horse horse = horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (user.role() == Role.HORSE_OWNER && !Objects.equals(horse.getOwner().getId(), user.id())) {
            throw ApiException.forbidden();
        }
        return sessions.findByHorseIdOrderByScheduledDateDescIdDesc(horseId).stream()
                .filter(s -> s.getState() != SessionState.DRAFT)
                .sorted(SessionTimes.byDateThenTime(false)).toList();
    }

    Optional<TrainingPlan> plan(Long id) {
        return plans.findById(id);
    }
}
