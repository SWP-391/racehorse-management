package vn.racehorse.training.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.events.DomainEvents.GroomUnavailable;
import vn.racehorse.events.DomainEvents.HorseArchived;
import vn.racehorse.events.DomainEvents.LockActivated;
import vn.racehorse.events.DomainEvents.LockLifted;
import vn.racehorse.events.DomainEvents.PendingLockRequested;
import vn.racehorse.events.DomainEvents.SessionEnded;
import vn.racehorse.health.dto.HealthEligibility;
import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.health.service.HealthService;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.horse.service.HorseTrainerService;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.training.dto.AssignedSession;
import vn.racehorse.training.dto.SessionAssignable;
import vn.racehorse.training.dto.SessionBlocked;
import vn.racehorse.training.dto.SessionInput;
import vn.racehorse.training.dto.SessionTimesSlot;
import vn.racehorse.training.entity.TrainingPlan;
import vn.racehorse.training.entity.TrainingSession.Indicators;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.repository.TrainingPlanRepository;
import vn.racehorse.training.repository.TrainingSessionRepository;
import vn.racehorse.training.service.TrainingEnums.AssignmentAck;
import vn.racehorse.training.service.TrainingEnums.PlanState;
import vn.racehorse.training.service.TrainingEnums.SessionState;
import vn.racehorse.training.service.TrainingEnums.SessionType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Scheduling and running sessions, and how locks and archiving affect them (REQ-003, REQ-004, REQ-006 to REQ-008). */
@Service
public class SessionService {

    private final TrainingSessionRepository sessions;
    private final TrainingPlanRepository plans;
    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final HealthService health;
    private final ClubSettings settings;
    private final AccessGuard access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClubClock clock;
    private final ApplicationEventPublisher events;
    private final HorseTrainerService trainers;
    private final GroomAvailability availability;

    public SessionService(TrainingSessionRepository sessions, TrainingPlanRepository plans, HorseRepository horses,
                          UserAccountRepository accounts, HealthService health, ClubSettings settings,
                          AccessGuard access, AuditService audit, NotificationService notifications, ClubClock clock,
                          ApplicationEventPublisher events, HorseTrainerService trainers,
                          GroomAvailability availability) {
        this.sessions = sessions;
        this.plans = plans;
        this.horses = horses;
        this.accounts = accounts;
        this.health = health;
        this.settings = settings;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.events = events;
        this.trainers = trainers;
        this.availability = availability;
    }

    // ---- reads --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TrainingSession> ofPlan(Long planId) {
        AccountPrincipal user = access.require(Permission.VIEW_PLANS);
        if (user.role() == Role.HORSE_OWNER) {
            throw ApiException.forbidden();
        }
        return sessions.findByPlanIdOrderByScheduledDateAscIdAsc(planId).stream()
                .sorted(SessionTimes.byDateThenTime(true)).toList();
    }

    @Transactional(readOnly = true)
    public TrainingSession get(Long sessionId) {
        AccountPrincipal user = access.require(Permission.VIEW_PLANS);
        if (user.role() == Role.HORSE_OWNER) {
            throw ApiException.forbidden();
        }
        return session(sessionId);
    }

    /** The care team the Head Trainer can name as responsible person: active Grooms only (BR-134, DEC-053). */
    @Transactional(readOnly = true)
    public List<UserAccount> staff() {
        access.require(Permission.SCHEDULE_SESSIONS);
        return accounts.findByRoleOrderByDisplayNameAsc(Role.GROOM).stream().filter(UserAccount::isActive).toList();
    }

    /**
     * The Grooms the Head Trainer may name for a time range, with the reason for each one who cannot be chosen, and
     * the earliest start (15 minute steps, same day) at which someone is free when nobody is (BR-162, TECH-136).
     */
    @Transactional(readOnly = true)
    public SessionAssignable assignable(Long horseId, LocalDate date, LocalTime start, Integer durationMin,
                                 Long excludeSessionId) {
        access.requireTrainerOf(Permission.SCHEDULE_SESSIONS, horseId);
        if (date == null || start == null || durationMin == null || durationMin <= 0) {
            throw ApiException.invalid("VALIDATION_FAILED", "startTime", "Cần ngày, giờ bắt đầu và thời lượng.");
        }
        List<UserAccount> grooms = accounts.findByRoleOrderByDisplayNameAsc(Role.GROOM).stream()
                .filter(UserAccount::isActive).toList();
        List<UserAccount> free = new ArrayList<>();
        List<SessionBlocked> blocked = new ArrayList<>();
        for (UserAccount g : grooms) {
            Optional<Unavailable> why = availability.check(g.getId(), date, start, durationMin, excludeSessionId, horseId);
            if (why.isPresent()) {
                blocked.add(new SessionBlocked(g, why.get()));
            } else {
                free.add(g);
            }
        }
        LocalTime nextFree = null;
        if (free.isEmpty() && !grooms.isEmpty()) {
            for (LocalTime t = start.plusMinutes(15); t.isAfter(start) && t.isBefore(LocalTime.of(23, 59));
                 t = t.plusMinutes(15)) {
                LocalTime candidate = t;
                boolean someone = grooms.stream().anyMatch(g -> availability
                        .check(g.getId(), date, candidate, durationMin, excludeSessionId, horseId).isEmpty());
                if (someone) {
                    nextFree = candidate;
                    break;
                }
            }
        }
        return new SessionAssignable(free, blocked, nextFree);
    }

    /** One eligibility rule for scheduling and for changing the responsible person (BR-134, TECH-101). */
    private UserAccount requireGroom(Long accountId) {
        return Optional.ofNullable(accountId).flatMap(accounts::findById)
                .filter(a -> a.isActive() && a.getRole() == Role.GROOM)
                .orElseThrow(() -> ApiException.invalid("INVALID_RESPONSIBLE", "responsiblePersonId",
                        "Người phụ trách phải là Groom đang hoạt động."));
    }

    @Transactional(readOnly = true)
    public boolean pendingLockAtEnd(Long horseId) {
        return health.hasPendingLock(horseId);
    }

    // ---- scheduling ---------------------------------------------------------------------------------------

    @Transactional
    public TrainingSession create(Long planId, SessionInput in) {
        access.require(Permission.SCHEDULE_SESSIONS);
        TrainingPlan plan = plan(planId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.SCHEDULE_SESSIONS, plan.getHorseId());
        managedHorse(plan.getHorseId());
        if (plan.getState() != PlanState.DRAFT && plan.getState() != PlanState.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_STATE", "Giáo án đã kết thúc.");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (in.date() == null) errors.put("date", "Bắt buộc nhập.");
        if (in.startTime() == null) errors.put("startTime", "Bắt buộc nhập giờ bắt đầu.");
        if (in.type() == null) errors.put("type", "Bắt buộc chọn.");
        if (in.plannedDurationMin() == null || in.plannedDurationMin() <= 0)
            errors.put("plannedDurationMin", "Thời lượng phải lớn hơn 0.");
        if (in.date() != null && in.date().isBefore(today())) errors.put("date", "Ngày tập không được ở quá khứ.");
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thông tin buổi tập chưa hợp lệ.", errors);
        }
        boolean draft = in.draft() || plan.getState() == PlanState.DRAFT;
        // The responsible person may stay empty only while the session is a draft (BR-158).
        if (!draft && in.responsiblePersonId() == null) {
            throw ApiException.invalid("RESPONSIBLE_REQUIRED", "responsiblePersonId",
                    "Bắt buộc chọn người phụ trách trước khi xếp lịch.");
        }
        UserAccount responsible = in.responsiblePersonId() == null ? null : requireGroom(in.responsiblePersonId());
        if (!in.draft() && plan.getState() == PlanState.DRAFT) {
            throw planNotActive();
        }
        // Serialises scheduling for one horse so two overlapping sessions cannot both pass (TECH-083).
        health.lockForUpdate(plan.getHorseId());
        if (!draft) {
            requireNotLocked(plan.getHorseId());
        }
        SessionTimesSlot slot = new SessionTimesSlot(null, in.date(), in.startTime(), in.plannedDurationMin());
        requireNoHorseOverlap(slot, plan.getHorseId(), Map.of());
        if (responsible != null) {
            requireFree(responsible, slot, plan.getHorseId());
        }
        TrainingSession s = sessions.save(new TrainingSession(planId, plan.getHorseId(), in.date(), in.startTime(),
                in.type(), in.plannedDurationMin(), in.targetDistanceM(), blankToNull(in.targetSurface()), responsible,
                draft ? SessionState.DRAFT : SessionState.SCHEDULED, clock.now()));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("planId", planId);
        detail.put("date", in.date().toString());
        detail.put("startTime", in.startTime().toString());
        detail.put("type", in.type().name());
        audit.record(actor, draft ? "SESSION_DRAFTED" : "SESSION_SCHEDULED", "SESSION", s.getId(), detail);
        announceAssignment(s, null);
        return s;
    }

    /** Refuses a slot that overlaps another counted session of the horse (BR-121, TECH-083). */
    private void requireNoHorseOverlap(SessionTimesSlot slot, Long horseId, Map<Long, LocalDate> moved) {
        SessionTimes.requireNoOverlap(slot, sessions.findByHorseIdOrderByScheduledDateDescIdDesc(horseId), moved);
    }

    /**
     * Refuses a Groom who is not free for the slot: another session of theirs overlaps (BR-161), or one of the rules
     * of {@link GroomAvailabilityRule} says no (BR-162). The Groom's account row is locked first, so two simultaneous
     * assignments cannot both pass (TECH-136).
     */
    private void requireFree(UserAccount groom, SessionTimesSlot slot, Long horseId) {
        accounts.findByIdForUpdate(groom.getId());
        availability.check(groom.getId(), slot.date(), slot.start(), slot.durationMin(), slot.sessionId(), horseId)
                .ifPresent(u -> {
                    String message = "Không thể chọn " + groom.getDisplayName() + " làm người phụ trách: " + u.detail();
                    throw new ApiException(HttpStatus.CONFLICT, "GROOM_NOT_AVAILABLE", message,
                            Map.of("responsiblePersonId", message));
                });
    }

    private static ApiException warning(String message) {
        return new ApiException(HttpStatus.CONFLICT, "WARNING_CONFIRMATION_REQUIRED", message);
    }

    /**
     * Late when less than the threshold before the planned start (BR-117, TECH-085); a session without a start time
     * keeps the date rule of DEC-041 (changed on its own date).
     */
    private boolean lateChange(TrainingSession s) {
        if (s.plannedStart() == null) {
            return s.getScheduledDate().equals(today());
        }
        LocalDateTime now = clock.toClubTime(clock.now()).toLocalDateTime();
        return !now.isBefore(s.plannedStart().minus(settings.lateChangeThreshold()));
    }

    /** Moves a drafted session to Đã lên lịch; only in an active plan and never while locked (BR-032, BR-015). */
    @Transactional
    public TrainingSession schedule(Long sessionId) {
        access.require(Permission.SCHEDULE_SESSIONS);
        TrainingSession s = session(sessionId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.SCHEDULE_SESSIONS, s.getHorseId());
        managedHorse(s.getHorseId());
        if (s.getState() != SessionState.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_STATE", "Chỉ xếp lịch được buổi tập Nháp.");
        }
        if (plan(s.getPlanId()).getState() != PlanState.ACTIVE) {
            throw planNotActive();
        }
        if (s.getScheduledDate().isBefore(today())) {
            throw ApiException.invalid("VALIDATION_FAILED", "date", "Ngày tập không được ở quá khứ.");
        }
        requireNotLocked(s.getHorseId());
        if (s.getResponsiblePerson() == null) {
            throw ApiException.invalid("RESPONSIBLE_REQUIRED", "responsiblePersonId",
                    "Bắt buộc chọn người phụ trách trước khi xếp lịch.");
        }
        // Times or barn hours may have changed since the draft was written (TECH-134).
        requireFree(s.getResponsiblePerson(), SessionTimesSlot.of(s), s.getHorseId());
        s.moveTo(SessionState.SCHEDULED, clock.now());
        audit.record(actor, "SESSION_SCHEDULED", "SESSION", s.getId(), Map.of("planId", s.getPlanId()));
        return s;
    }

    /**
     * Changes the responsible person of a session not yet started (PERM-006, BR-115). A late change needs the warning
     * confirmed (BR-117); a Groom who is not free is refused (BR-161, BR-162); the new and previous Groom are told
     * (BR-116, TECH-062, TECH-064, TECH-084).
     */
    @Transactional
    public TrainingSession assign(Long sessionId, Long responsiblePersonId, boolean acknowledgeWarning) {
        access.require(Permission.SCHEDULE_SESSIONS);
        TrainingSession s = session(sessionId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.SCHEDULE_SESSIONS, s.getHorseId());
        managedHorse(s.getHorseId());
        if (!s.getState().isUpcoming()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_STATE", "Chỉ đổi người phụ trách cho buổi tập chưa diễn ra.");
        }
        UserAccount previous = s.getResponsiblePerson();
        UserAccount responsible = requireGroom(responsiblePersonId);
        if (previous != null && previous.getId().equals(responsible.getId())) {
            return s;
        }
        requireFree(responsible, SessionTimesSlot.of(s), s.getHorseId());
        boolean late = lateChange(s);
        if (late && !acknowledgeWarning) {
            throw warning("Đổi người phụ trách sát giờ tập: người mới có thể không kịp chuẩn bị.");
        }
        s.assign(responsible, clock.now());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", previous == null ? null : previous.getId());
        detail.put("to", responsible.getId());
        detail.put("lateWarningConfirmed", late);
        audit.record(actor, "SESSION_RESPONSIBLE_CHANGED", "SESSION", s.getId(), detail);
        announceAssignment(s, previous);
        return s;
    }

    /** The responsible Groom confirms "Đã nhận", once, while the session is upcoming (PERM-033, BR-118, TECH-066). */
    @Transactional
    public AssignedSession acknowledge(Long sessionId) {
        AccountPrincipal actor = access.require(Permission.ACKNOWLEDGE_ASSIGNMENT);
        TrainingSession s = session(sessionId);
        if (s.getResponsiblePerson() == null || !s.getResponsiblePerson().getId().equals(actor.id())) {
            throw ApiException.forbidden();
        }
        if (s.getAssignmentAck() == AssignmentAck.ACKNOWLEDGED) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ACKNOWLEDGED", "Bạn đã xác nhận buổi tập này.");
        }
        if (!s.getState().isUpcoming()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_STATE", "Buổi tập đã diễn ra.");
        }
        s.acknowledge(clock.now());
        audit.record(actor, "SESSION_ASSIGNMENT_ACKNOWLEDGED", "SESSION", s.getId(), Map.of("horseId", s.getHorseId()));
        return new AssignedSession(s, horseName(s.getHorseId()));
    }

    /**
     * The responsible Groom refuses with a mandatory reason: nobody is responsible and the session is flagged
     * "Cần người phụ trách"; the assigned Head Trainer is told at once (BR-159, TECH-134).
     */
    @Transactional
    public TrainingSession decline(Long sessionId, String reason) {
        AccountPrincipal actor = access.require(Permission.ACKNOWLEDGE_ASSIGNMENT);
        TrainingSession s = session(sessionId);
        if (s.getResponsiblePerson() == null || !s.getResponsiblePerson().getId().equals(actor.id())) {
            throw ApiException.forbidden();
        }
        if (!s.getState().isUpcoming()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_STATE", "Buổi tập đã diễn ra.");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalid("REASON_REQUIRED", "reason", "Bắt buộc nhập lý do.");
        }
        UserAccount groom = s.getResponsiblePerson();
        s.decline(groom, reason.trim(), clock.now());
        audit.record(actor, "SESSION_ASSIGNMENT_DECLINED", "SESSION", s.getId(),
                Map.of("groomId", groom.getId(), "reason", reason.trim()));
        trainers.notifyTrainer(s.getHorseId(), "Groom " + groom.getDisplayName() + " không nhận buổi tập "
                + TYPE_NAMES.get(s.getType()) + " của ngựa \"" + horseName(s.getHorseId()) + "\" ngày "
                + s.getScheduledDate().format(DATE) + " (" + reason.trim() + "). Cần người phụ trách khác.",
                "/sessions/" + s.getId());
        return s;
    }

    /** In-app notices to the newly responsible Groom and to the previous one (BR-116, Q-046: Grooms only). */
    private void announceAssignment(TrainingSession s, UserAccount previous) {
        String what = TYPE_NAMES.get(s.getType()) + " của ngựa \"" + horseName(s.getHorseId()) + "\" ngày "
                + s.getScheduledDate().format(DATE);
        UserAccount current = s.getResponsiblePerson();
        if (current != null && current.getRole() == Role.GROOM) {
            notifications.notifyAccount(current.getId(), "Bạn được giao phụ trách buổi tập " + what + ".", "/my-sessions");
        }
        if (previous != null && previous.getRole() == Role.GROOM) {
            notifications.notifyAccount(previous.getId(), "Bạn không còn phụ trách buổi tập " + what + ".", "/my-sessions");
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** The names the UI uses for session types (vi.ts training.types). */
    static final Map<SessionType, String> TYPE_NAMES = Map.of(SessionType.TROT, "Chạy nước kiệu",
            SessionType.GALLOP, "Phi", SessionType.TRIAL_RUN, "Chạy thử", SessionType.OTHER, "Khác");

    /** The sessions where the caller is the responsible person, read-only (BR-114, PERM-032). */
    @Transactional(readOnly = true)
    public List<AssignedSession> assignedToMe() {
        AccountPrincipal user = access.require(Permission.VIEW_ASSIGNED_SESSIONS);
        return sessions.findByResponsiblePersonIdOrderByScheduledDateDescIdDesc(user.id()).stream()
                .sorted(SessionTimes.byDateThenTime(false))
                .map(s -> new AssignedSession(s, horseName(s.getHorseId()))).toList();
    }

    @Transactional
    public TrainingSession cancel(Long sessionId, String reason) {
        access.require(Permission.SCHEDULE_SESSIONS);
        TrainingSession s = session(sessionId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.SCHEDULE_SESSIONS, s.getHorseId());
        if (!s.getState().isUpcoming()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_STATE", "Buổi tập này không thể hủy.");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalid("REASON_REQUIRED", "reason", "Bắt buộc nhập lý do.");
        }
        s.cancel(reason.trim(), clock.now());
        audit.record(actor, "SESSION_CANCELLED", "SESSION", s.getId(), Map.of("reason", reason.trim()));
        return s;
    }

    // ---- running ------------------------------------------------------------------------------------------

    /**
     * Starts a session if, at this moment, the horse is idle, unlocked and Đủ điều kiện or Cần theo dõi (the latter
     * after confirming a warning). The horse's health row lock serialises this with lock placement (BR-034, TECH-026).
     */
    @Transactional
    public TrainingSession start(Long sessionId, boolean acknowledgeWarning) {
        access.require(Permission.RUN_SESSIONS);
        TrainingSession s = session(sessionId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.RUN_SESSIONS, s.getHorseId());
        managedHorse(s.getHorseId());
        health.lockForUpdate(s.getHorseId());
        HealthEligibility e = health.eligibility(s.getHorseId());
        if (e.locked()) {
            throw lockedError(e);
        }
        if (s.getState() != SessionState.SCHEDULED) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_STATE", "Chỉ bắt đầu được buổi tập Đã lên lịch.");
        }
        if (plan(s.getPlanId()).getState() != PlanState.ACTIVE) {
            throw planNotActive();
        }
        if (s.getResponsiblePerson() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_RESPONSIBLE_PERSON",
                    "Buổi tập chưa có người phụ trách: hãy chọn người phụ trách trước khi bắt đầu.");
        }
        if (e.status() == HealthStatus.INJURED || e.status() == HealthStatus.QUARANTINED) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_NOT_ELIGIBLE",
                    "Ngựa đang " + e.status().label() + ": không được tập.");
        }
        if (sessions.findFirstByHorseIdAndState(s.getHorseId(), SessionState.IN_PROGRESS).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ALREADY_TRAINING", "Ngựa đang có buổi tập khác.");
        }
        if (e.status() == HealthStatus.UNDER_OBSERVATION && !acknowledgeWarning) {
            throw new ApiException(HttpStatus.CONFLICT, "WARNING_CONFIRMATION_REQUIRED",
                    "Ngựa đang Cần theo dõi. Xác nhận để bắt đầu buổi tập.");
        }
        s.start(clock.now());
        sessions.saveAndFlush(s);
        // An unconfirmed Groom only reminds the Head Trainer, it never blocks the start (BR-119, TECH-067).
        audit.record(actor, "SESSION_STARTED", "SESSION", s.getId(),
                Map.of("horseId", s.getHorseId(), "warningConfirmed", e.status() == HealthStatus.UNDER_OBSERVATION,
                        "responsibleUnconfirmed", s.getAssignmentAck() == AssignmentAck.UNCONFIRMED));
        return s;
    }

    @Transactional
    public TrainingSession end(Long sessionId, Indicators indicators, String earlyEndReason) {
        access.require(Permission.RUN_SESSIONS);
        TrainingSession s = session(sessionId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.RUN_SESSIONS, s.getHorseId());
        if (s.getState() != SessionState.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_STATE", "Buổi tập không ở trạng thái Đang tập.");
        }
        requireIndicators(indicators);
        String early = earlyEndReason == null || earlyEndReason.isBlank() ? null : earlyEndReason.trim();
        s.complete(indicators, early, clock.now());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("form", indicators.form());
        if (early != null) {
            details.put("earlyEndReason", early);
        }
        audit.record(actor, "SESSION_COMPLETED", "SESSION", s.getId(), details);
        events.publishEvent(new SessionEnded(s.getHorseId(), s.getId()));
        return s;
    }

    /** After auto-close the Head Trainer completes the indicators and may correct the end time (BR-038, AC-049). */
    @Transactional
    public TrainingSession completeAfterAutoClose(Long sessionId, Indicators indicators, Instant actualEnd) {
        access.require(Permission.RUN_SESSIONS);
        TrainingSession s = session(sessionId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.RUN_SESSIONS, s.getHorseId());
        if (s.getState() != SessionState.AUTO_CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_STATE", "Buổi tập không ở trạng thái tự động đóng.");
        }
        requireIndicators(indicators);
        if (actualEnd != null && (actualEnd.isBefore(s.getStartedAt()) || actualEnd.isAfter(s.getEndedAt()))) {
            throw ApiException.invalid("INVALID_END_TIME", "actualEndTime",
                    "Giờ kết thúc phải nằm giữa giờ bắt đầu và giờ tự động đóng.");
        }
        s.completeAfterAutoClose(indicators, actualEnd, clock.now());
        audit.record(actor, "SESSION_INDICATORS_COMPLETED", "SESSION", s.getId(),
                Map.of("endTimeCorrected", actualEnd != null));
        return s;
    }

    // ---- reactions to locks and archiving (BR-021, BR-022, BR-007) ----------------------------------------

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onLockActivated(LockActivated event) {
        for (TrainingSession s : sessions.findByHorseIdAndStateIn(event.horseId(),
                EnumSet.of(SessionState.SCHEDULED, SessionState.AWAITING_REVIEW))) {
            s.moveTo(SessionState.BLOCKED_BY_LOCK, clock.now());
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onLockLifted(LockLifted event) {
        LocalDate today = today();
        SessionState returnTo = "RESUME_AS_IS".equals(event.returnLevel())
                ? SessionState.SCHEDULED : SessionState.AWAITING_REVIEW;
        for (TrainingSession s : sessions.findByHorseIdAndStateIn(event.horseId(),
                EnumSet.of(SessionState.BLOCKED_BY_LOCK))) {
            s.moveTo(s.getScheduledDate().isBefore(today) ? SessionState.MISSED_DUE_TO_LOCK : returnTo, clock.now());
        }
    }

    /**
     * A Groom stopped being a Groom: the Head Trainer is asked to reassign the sessions that have not started
     * (BR-136, XDEP-017, TECH-104). The assignments stay as they are; with no such session nothing is sent.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onGroomUnavailable(GroomUnavailable event) {
        List<TrainingSession> upcoming = sessions
                .findByResponsiblePersonIdOrderByScheduledDateDescIdDesc(event.accountId()).stream()
                .filter(s -> s.getState().isUpcoming())
                .filter(s -> event.onDate() == null || s.getScheduledDate().equals(event.onDate())).toList();
        if (upcoming.isEmpty()) {
            return;
        }
        // One notice per assigned trainer, listing only the sessions of their own horses (BR-136, BR-157).
        Map<Long, List<TrainingSession>> byHorse = new LinkedHashMap<>();
        upcoming.stream()
                .sorted(java.util.Comparator.comparing(TrainingSession::getScheduledDate)
                        .thenComparing(TrainingSession::getId))
                .forEach(s -> byHorse.computeIfAbsent(s.getHorseId(), k -> new ArrayList<>()).add(s));
        Map<Long, List<TrainingSession>> byTrainer = new LinkedHashMap<>();
        Map<Long, List<TrainingSession>> unassigned = new LinkedHashMap<>();
        byHorse.forEach((horseId, list) -> {
            Long trainerId = trainers.effectiveTrainerId(horseId);
            if (trainerId == null) {
                unassigned.put(horseId, list);
            } else {
                byTrainer.computeIfAbsent(trainerId, k -> new ArrayList<>()).addAll(list);
            }
        });
        byTrainer.forEach((trainerId, list) -> notifications.notifyAccount(trainerId,
                groomNotice(event, list), "/horses"));
        unassigned.forEach((horseId, list) -> trainers.notifyTrainer(horseId, groomNotice(event, list), "/horses"));
    }

    private String groomNotice(GroomUnavailable event, List<TrainingSession> list) {
        String text = list.stream()
                .map(s -> TYPE_NAMES.get(s.getType()) + " của ngựa \"" + horseName(s.getHorseId()) + "\" ngày "
                        + s.getScheduledDate().format(DATE)
                        + (s.getPlannedStartTime() == null ? "" : " lúc " + s.getPlannedStartTime()))
                .collect(java.util.stream.Collectors.joining("; "));
        return "Groom " + event.displayName() + " " + event.reason() + ". Cần giao lại " + list.size()
                + " buổi tập: " + text + ".";
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onHorseArchived(HorseArchived event) {
        LocalDate today = today();
        for (TrainingSession s : sessions.findByHorseIdAndStateIn(event.horseId(), EnumSet.of(SessionState.DRAFT,
                SessionState.SCHEDULED, SessionState.BLOCKED_BY_LOCK, SessionState.AWAITING_REVIEW))) {
            if (!s.getScheduledDate().isBefore(today)) {
                s.cancel("ngựa ngừng quản lý", clock.now());
            }
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onPendingLockRequested(PendingLockRequested event) {
        if (event.sessionId() != null) {
            sessions.findById(event.sessionId()).ifPresent(s -> s.markLockDuringSession(clock.now()));
        }
    }

    /** Called by the simulation when a session ends; fills only what the Head Trainer left empty (TECH-040). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fillSimulated(Long sessionId, Integer avgHeartRate, java.math.BigDecimal maxSpeed) {
        TrainingSession s = session(sessionId);
        s.fillSimulated(avgHeartRate, maxSpeed);
    }

    // ---- time-based rules (BR-037, BR-038, BR-039; TECH-025) ----------------------------------------------

    /** Flags sessions past their planned duration and notifies Head Trainers once per session. */
    @Transactional
    public int flagOvertime() {
        Instant now = clock.now();
        int flagged = 0;
        for (TrainingSession s : sessions.findByState(SessionState.IN_PROGRESS)) {
            if (!s.isOvertime() && !now.isBefore(s.getStartedAt().plus(Duration.ofMinutes(s.getPlannedDurationMin())))) {
                s.markOvertime(now);
                trainers.notifyTrainer(s.getHorseId(),
                        "Buổi tập của ngựa \"" + horseName(s.getHorseId()) + "\" đã quá giờ.", "/sessions/" + s.getId());
                flagged++;
            }
        }
        return flagged;
    }

    /** Closes sessions still running after planned duration plus the grace period; applies pending locks. */
    @Transactional
    public int autoClose() {
        Instant now = clock.now();
        Duration grace = settings.gracePeriod();
        int closed = 0;
        for (TrainingSession s : sessions.findByState(SessionState.IN_PROGRESS)) {
            Instant deadline = s.getStartedAt().plus(Duration.ofMinutes(s.getPlannedDurationMin())).plus(grace);
            if (!now.isBefore(deadline)) {
                s.autoClose(now);
                audit.recordSystem("SESSION_AUTO_CLOSED", "SESSION", s.getId(), Map.of("horseId", s.getHorseId()));
                trainers.notifyTrainer(s.getHorseId(),
                        "Buổi tập của ngựa \"" + horseName(s.getHorseId())
                                + "\" đã được tự động đóng. Hãy bổ sung chỉ số và giờ kết thúc thực tế.",
                        "/sessions/" + s.getId());
                events.publishEvent(new SessionEnded(s.getHorseId(), s.getId()));
                closed++;
            }
        }
        return closed;
    }

    /**
     * Reminds a Groom who has not answered 24 hours before the start, and tells the assigned trainer once the
     * late-change threshold is reached (BR-160, TECH-135). The timestamps make each notice once per assignment, also
     * after a restart.
     */
    @Transactional
    public int sendAssignmentReminders() {
        Instant now = clock.now();
        LocalDateTime nowClub = clock.toClubTime(now).toLocalDateTime();
        Duration threshold = settings.lateChangeThreshold();
        int sent = 0;
        for (TrainingSession s : sessions.findByState(SessionState.SCHEDULED)) {
            if (s.getResponsiblePerson() == null || s.getAssignmentAck() != AssignmentAck.UNCONFIRMED
                    || s.plannedStart() == null || !nowClub.isBefore(s.plannedStart())) {
                continue;
            }
            LocalDateTime start = s.plannedStart();
            String what = TYPE_NAMES.get(s.getType()) + " của ngựa \"" + horseName(s.getHorseId()) + "\" ngày "
                    + s.getScheduledDate().format(DATE) + " lúc " + s.getPlannedStartTime();
            if (s.getGroomRemindedAt() == null && !nowClub.isBefore(start.minusHours(24))) {
                notifications.notifyAccount(s.getResponsiblePerson().getId(),
                        "Nhắc: bạn chưa xác nhận buổi tập " + what + ".", "/my-sessions");
                s.markGroomReminded(now);
                sent++;
            }
            if (s.getTrainerWarnedAt() == null && !nowClub.isBefore(start.minus(threshold))) {
                trainers.notifyTrainer(s.getHorseId(), "Groom " + s.getResponsiblePerson().getDisplayName()
                        + " vẫn chưa xác nhận buổi tập " + what + ".", "/sessions/" + s.getId());
                s.markTrainerWarned(now);
                sent++;
            }
        }
        return sent;
    }

    /** After a session's club-local day has passed: Bỏ lỡ, or Bỏ lỡ do khóa if it was blocked (BR-039, BR-022). */
    @Transactional
    public int markMissed() {
        LocalDate today = today();
        int missed = 0;
        for (TrainingSession s : sessions.findByStateInAndScheduledDateBefore(
                EnumSet.of(SessionState.SCHEDULED, SessionState.BLOCKED_BY_LOCK, SessionState.AWAITING_REVIEW), today)) {
            s.moveTo(s.getState() == SessionState.SCHEDULED ? SessionState.MISSED : SessionState.MISSED_DUE_TO_LOCK,
                    clock.now());
            missed++;
        }
        return missed;
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    public LocalDate today() {
        return clock.toClubTime(clock.now()).toLocalDate();
    }

    private void requireIndicators(Indicators i) {
        if (i == null) {
            throw ApiException.invalid("VALIDATION_FAILED", "indicators", "Bắt buộc nhập chỉ số buổi tập.");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (i.distanceM() == null) errors.put("distanceM", "Bắt buộc nhập.");
        else if (i.distanceM() <= 0) errors.put("distanceM", "Cự ly phải lớn hơn 0.");
        if (i.durationMin() == null) errors.put("durationMin", "Bắt buộc nhập.");
        else if (i.durationMin() <= 0) errors.put("durationMin", "Thời lượng phải lớn hơn 0.");
        if (i.surface() == null || i.surface().isBlank()) errors.put("surface", "Bắt buộc nhập.");
        if (i.intensity() == null) errors.put("intensity", "Bắt buộc chọn.");
        if (i.form() == null) errors.put("form", "Bắt buộc chấm phong độ.");
        else if (i.form() < 1 || i.form() > 5) errors.put("form", "Phong độ chấm từ 1 đến 5.");
        if (i.avgHeartRate() != null && i.avgHeartRate() <= 0) errors.put("avgHeartRate", "Phải lớn hơn 0.");
        if (i.maxSpeed() != null && i.maxSpeed().signum() <= 0) errors.put("maxSpeed", "Phải lớn hơn 0.");
        if (i.weightKg() != null && i.weightKg().signum() <= 0) errors.put("weightKg", "Phải lớn hơn 0.");
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INDICATORS_INCOMPLETE",
                    "Thiếu hoặc sai chỉ số: " + String.join(", ", errors.keySet()) + ".", errors);
        }
    }

    private void requireNotLocked(Long horseId) {
        HealthEligibility e = health.eligibility(horseId);
        if (e.locked()) {
            throw lockedError(e);
        }
    }

    private static ApiException lockedError(HealthEligibility e) {
        return new ApiException(HttpStatus.CONFLICT, "HORSE_LOCKED",
                "Ngựa đang bị khóa huấn luyện: " + e.activeLock().getReason());
    }

    private static ApiException planNotActive() {
        return new ApiException(HttpStatus.CONFLICT, "PLAN_NOT_ACTIVE",
                "Giáo án chưa kích hoạt: chỉ được soạn nháp buổi tập, chưa thể xếp lịch hoặc bắt đầu.");
    }

    private String horseName(Long horseId) {
        return horses.findById(horseId).map(Horse::getName).orElse("?");
    }

    private Horse managedHorse(Long horseId) {
        Horse horse = horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý.");
        }
        return horse;
    }

    private TrainingPlan plan(Long planId) {
        return plans.findById(planId).orElseThrow(() -> ApiException.notFound("giáo án"));
    }

    private TrainingSession session(Long sessionId) {
        return sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("buổi tập"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
