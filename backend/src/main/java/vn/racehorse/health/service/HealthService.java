package vn.racehorse.health.service;

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
import vn.racehorse.events.DomainEvents.LockActivated;
import vn.racehorse.events.DomainEvents.LockLifted;
import vn.racehorse.events.DomainEvents.PendingLockRequested;
import vn.racehorse.events.DomainEvents.SessionEnded;
import vn.racehorse.health.dto.HealthEligibility;
import vn.racehorse.health.dto.HorseHealthView;
import vn.racehorse.health.entity.ActivityStatus;
import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.health.entity.HealthStatusChange;
import vn.racehorse.health.entity.HorseHealth;
import vn.racehorse.health.entity.LockKind;
import vn.racehorse.health.entity.LockReviewNote;
import vn.racehorse.health.entity.PendingLockRequest;
import vn.racehorse.health.entity.Readiness;
import vn.racehorse.health.entity.ReturnLevel;
import vn.racehorse.health.entity.TrainingLock;
import vn.racehorse.health.repository.HealthStatusChangeRepository;
import vn.racehorse.health.repository.HorseHealthRepository;
import vn.racehorse.health.repository.LockReviewNoteRepository;
import vn.racehorse.health.repository.PendingLockRequestRepository;
import vn.racehorse.health.repository.TrainingLockRepository;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.event.HorseRegistered;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.horse.service.HorseTrainerService;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Health status, training locks, pending lock requests, review notes and readiness (REQ-011, REQ-015, REQ-025).
 * Every write first takes the horse's health row lock, so lock and session-start decisions are serialised (TECH-026).
 */
@Service
public class HealthService {

    static final String INTAKE_REASON = "Chờ khám nhập đàn";

    private final HorseHealthRepository health;
    private final HealthStatusChangeRepository history;
    private final TrainingLockRepository locks;
    private final LockReviewNoteRepository notes;
    private final PendingLockRequestRepository pending;
    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final ActivityStatusProvider activity;
    private final AccessGuard access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClubClock clock;
    private final ApplicationEventPublisher events;
    private final HorseTrainerService trainers;

    public HealthService(HorseHealthRepository health, HealthStatusChangeRepository history,
                         TrainingLockRepository locks, LockReviewNoteRepository notes,
                         PendingLockRequestRepository pending, HorseRepository horses,
                         UserAccountRepository accounts, ActivityStatusProvider activity, AccessGuard access,
                         AuditService audit, NotificationService notifications, ClubClock clock,
                         ApplicationEventPublisher events, HorseTrainerService trainers) {
        this.health = health;
        this.history = history;
        this.locks = locks;
        this.notes = notes;
        this.pending = pending;
        this.horses = horses;
        this.accounts = accounts;
        this.activity = activity;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.events = events;
        this.trainers = trainers;
    }

    // ---- intake (BR-008) ----------------------------------------------------------------------------------

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onHorseRegistered(HorseRegistered event) {
        health.save(new HorseHealth(event.horseId(), HealthStatus.UNDER_OBSERVATION, clock.now()));
        history.save(new HealthStatusChange(event.horseId(), null, HealthStatus.UNDER_OBSERVATION,
                "Ngựa mới nhập đàn", null, clock.now()));
        TrainingLock lock = locks.save(new TrainingLock(event.horseId(), LockKind.INTAKE, INTAKE_REASON, null,
                clock.now(), null));
        audit.recordSystem("LOCK_PLACED", "HORSE", event.horseId(),
                Map.of("kind", LockKind.INTAKE.name(), "reason", INTAKE_REASON, "lockId", lock.getId()));
        trainers.notifyTrainer(event.horseId(),
                "Ngựa \"" + event.horseName() + "\" bị khóa huấn luyện: " + INTAKE_REASON + ".",
                "/horses/" + event.horseId());
        events.publishEvent(new LockActivated(event.horseId()));
    }

    // ---- reads --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HealthEligibility eligibility(Long horseId) {
        HorseHealth h = healthOf(horseId);
        TrainingLock lock = locks.findByHorseIdAndLiftedAtIsNull(horseId).orElse(null);
        return new HealthEligibility(horseId, h.getStatus(), lock, Readiness.of(h.getStatus(), lock != null),
                h.isUnexamined());
    }

    /** Marks the horse's first examination; the new-horse label goes, the lock stays (BR-009, TECH-032). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markExamined(Long horseId) {
        healthOf(horseId).markExamined(clock.now());
    }

    @Transactional(readOnly = true)
    public boolean hasPendingLock(Long horseId) {
        return openRequest(horseId) != null;
    }

    /** Takes the horse's health row lock for the caller's transaction (TECH-026). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForUpdate(Long horseId) {
        health.findForUpdate(horseId).orElseThrow(() -> ApiException.notFound("hồ sơ sức khỏe"));
    }

    @Transactional(readOnly = true)
    public List<HorseHealthView> herd() {
        access.require(Permission.VIEW_HEALTH);
        List<Horse> managed = horses.findAllByOrderByNameAsc().stream().filter(h -> !h.isArchived()).toList();
        List<Long> ids = managed.stream().map(Horse::getId).toList();
        Map<Long, HorseHealth> byHorse = health.findAllById(ids).stream()
                .collect(Collectors.toMap(HorseHealth::getHorseId, Function.identity()));
        Map<Long, TrainingLock> lockByHorse = locks.findByHorseIdInAndLiftedAtIsNull(ids).stream()
                .collect(Collectors.toMap(TrainingLock::getHorseId, Function.identity()));
        return managed.stream().filter(h -> byHorse.containsKey(h.getId())).map(h -> {
            HorseHealth hh = byHorse.get(h.getId());
            TrainingLock lock = lockByHorse.get(h.getId());
            return new HorseHealthView(h, hh, lock, activity.activityOf(h.getId()),
                    Readiness.of(hh.getStatus(), lock != null), List.of(), List.of(),
                    openRequest(h.getId()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public HorseHealthView detail(Long horseId) {
        access.require(Permission.VIEW_HEALTH);
        return view(horseId, true);
    }

    @Transactional(readOnly = true)
    public Readiness readiness(Long horseId) {
        AccountPrincipal user = access.require(Permission.VIEW_READINESS);
        requireVisible(user, horse(horseId));
        return eligibility(horseId).readiness();
    }

    @Transactional(readOnly = true)
    public Map<Long, Readiness> readinessOfVisibleHorses() {
        AccountPrincipal user = access.require(Permission.VIEW_READINESS);
        List<Horse> visible = user.role() == Role.HORSE_OWNER
                ? horses.findByOwnerIdOrderByNameAsc(user.id())
                : horses.findAllByOrderByNameAsc();
        Map<Long, Readiness> result = new LinkedHashMap<>();
        for (Horse h : visible) {
            if (health.existsById(h.getId())) {
                result.put(h.getId(), eligibility(h.getId()).readiness());
            }
        }
        return result;
    }

    // ---- status and locks ---------------------------------------------------------------------------------

    @Transactional
    public HorseHealthView changeStatus(Long horseId, HealthStatus to, String reason) {
        AccountPrincipal actor = access.require(Permission.CHANGE_HEALTH_STATUS);
        Horse horse = managedHorse(horseId);
        lockForUpdate(horseId);
        requireIdle(horseId);
        String why = requireText(reason, "reason", "REASON_REQUIRED", "Bắt buộc nhập lý do.");
        if (to == null) {
            throw ApiException.invalid("STATUS_REQUIRED", "status", "Bắt buộc chọn trạng thái.");
        }
        applyStatus(actor, horse, to, why);
        return view(horseId, true);
    }

    @Transactional
    public HorseHealthView lock(Long horseId, String reason, LocalDate reviewDate) {
        AccountPrincipal actor = access.require(Permission.MANAGE_LOCKS);
        Horse horse = managedHorse(horseId);
        lockForUpdate(horseId);
        requireIdle(horseId);
        String why = requireText(reason, "reason", "REASON_REQUIRED", "Bắt buộc nhập lý do.");
        if (locks.findByHorseIdAndLiftedAtIsNull(horseId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_LOCKED", "Ngựa đang bị khóa huấn luyện.");
        }
        if (healthOf(horseId).getStatus() == HealthStatus.ELIGIBLE) {
            throw new ApiException(HttpStatus.CONFLICT, "LOCK_REQUIRES_OBSERVATION",
                    "Không thể khóa ngựa Đủ điều kiện. Hãy chuyển ngựa sang Cần theo dõi trước.");
        }
        placeLock(actor, horse, LockKind.MANUAL, why, reviewDate);
        return view(horseId, true);
    }

    @Transactional
    public HorseHealthView lift(Long horseId, ReturnLevel returnLevel) {
        AccountPrincipal actor = access.require(Permission.MANAGE_LOCKS);
        Horse horse = managedHorse(horseId);
        lockForUpdate(horseId);
        requireIdle(horseId);
        TrainingLock lock = locks.findByHorseIdAndLiftedAtIsNull(horseId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NOT_LOCKED", "Ngựa không bị khóa."));
        HealthStatus status = healthOf(horseId).getStatus();
        if (status.forbidsTraining()) {
            throw new ApiException(HttpStatus.CONFLICT, "LIFT_NOT_ALLOWED",
                    "Không thể gỡ khóa khi ngựa đang " + status.label() + ". Hãy đổi trạng thái trước.");
        }
        if (returnLevel == null) {
            throw ApiException.invalid("RETURN_LEVEL_REQUIRED", "returnLevel", "Bắt buộc chọn mức độ quay lại.");
        }
        lock.lift(account(actor), clock.now(), returnLevel);
        audit.record(actor, "LOCK_LIFTED", "HORSE", horseId,
                Map.of("lockId", lock.getId(), "returnLevel", returnLevel.name()));
        trainers.notifyTrainer(horseId,
                "Ngựa \"" + horse.getName() + "\" đã được gỡ khóa huấn luyện ("
                        + (returnLevel == ReturnLevel.RESUME_AS_IS ? "tiếp tục nguyên lịch" : "tập có điều chỉnh") + ").",
                "/horses/" + horseId);
        events.publishEvent(new LockLifted(horseId, returnLevel.name()));
        return view(horseId, true);
    }

    @Transactional
    public HorseHealthView requestReview(Long horseId, String note) {
        AccountPrincipal actor = access.requireTrainerOf(Permission.REQUEST_LOCK_REVIEW, horseId);
        Horse horse = managedHorse(horseId);
        String text = requireText(note, "note", "NOTE_REQUIRED", "Bắt buộc nhập ghi chú.");
        TrainingLock lock = locks.findByHorseIdAndLiftedAtIsNull(horseId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NOT_LOCKED", "Ngựa không bị khóa."));
        notes.save(new LockReviewNote(lock.getId(), account(actor), text, clock.now()));
        audit.record(actor, "LOCK_REVIEW_REQUESTED", "HORSE", horseId, Map.of("lockId", lock.getId(), "note", text));
        notifications.notifyRole(Role.VETERINARIAN,
                "HLV đề nghị xem xét khóa huấn luyện của ngựa \"" + horse.getName() + "\": " + text,
                "/horses/" + horseId);
        return view(horseId, false);
    }

    // ---- pending lock requests (BR-020) -------------------------------------------------------------------

    @Transactional
    public HorseHealthView requestPendingLock(Long horseId, String reason) {
        AccountPrincipal actor = access.require(Permission.REQUEST_PENDING_LOCK);
        Horse horse = managedHorse(horseId);
        lockForUpdate(horseId);
        String why = requireText(reason, "reason", "REASON_REQUIRED", "Bắt buộc nhập lý do.");
        Long sessionId = activity.sessionInProgress(horseId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                "HORSE_NOT_TRAINING", "Ngựa đang nghỉ: hãy khóa trực tiếp thay vì yêu cầu khóa chờ."));
        if (openRequest(horseId) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "PENDING_EXISTS", "Đã có yêu cầu khóa chờ cho buổi tập này.");
        }
        PendingLockRequest request = pending.save(
                new PendingLockRequest(horseId, sessionId, why, account(actor), clock.now()));
        audit.record(actor, "PENDING_LOCK_REQUESTED", "HORSE", horseId,
                Map.of("requestId", request.getId(), "sessionId", sessionId, "reason", why));
        trainers.notifyTrainer(horseId,
                "Ngựa \"" + horse.getName() + "\" sẽ bị khóa khi buổi tập này kết thúc: " + why + ".",
                "/horses/" + horseId);
        events.publishEvent(new PendingLockRequested(horseId, sessionId, why));
        return view(horseId, true);
    }

    @Transactional
    public HorseHealthView withdrawPendingLock(Long horseId) {
        AccountPrincipal actor = access.require(Permission.REQUEST_PENDING_LOCK);
        managedHorse(horseId);
        lockForUpdate(horseId);
        PendingLockRequest request = pending.findByHorseIdAndWithdrawnAtIsNullAndAppliedAtIsNull(horseId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_PENDING", "Không có yêu cầu khóa chờ."));
        request.withdraw(clock.now());
        audit.record(actor, "PENDING_LOCK_WITHDRAWN", "HORSE", horseId, Map.of("requestId", request.getId()));
        return view(horseId, true);
    }

    /**
     * Applies an open pending request when the session ends, before any other session can start (BR-020).
     * An Đủ điều kiện horse is first set to Cần theo dõi so it is never locked while eligible (DEC-024; Q-025 default).
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onSessionEnded(SessionEnded event) {
        PendingLockRequest request = pending.findByHorseIdAndWithdrawnAtIsNullAndAppliedAtIsNull(event.horseId())
                .orElse(null);
        if (request == null) {
            return;
        }
        lockForUpdate(event.horseId());
        Horse horse = horse(event.horseId());
        AccountPrincipal requester = AccountPrincipal.of(request.getRequestedBy());
        if (healthOf(horse.getId()).getStatus() == HealthStatus.ELIGIBLE) {
            applyStatus(requester, horse, HealthStatus.UNDER_OBSERVATION,
                    "Áp dụng yêu cầu khóa chờ: " + request.getReason());
        }
        TrainingLock lock = locks.findByHorseIdAndLiftedAtIsNull(horse.getId())
                .orElseGet(() -> placeLock(requester, horse, LockKind.MANUAL, request.getReason(), null));
        request.applied(lock.getId(), clock.now());
        notifications.notifyRole(Role.VETERINARIAN,
                "Yêu cầu khóa chờ cho ngựa \"" + horse.getName() + "\" đã có hiệu lực.", "/horses/" + horse.getId());
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private void applyStatus(AccountPrincipal actor, Horse horse, HealthStatus to, String why) {
        HorseHealth hh = healthOf(horse.getId());
        HealthStatus from = hh.getStatus();
        if (from == to) {
            throw ApiException.invalid("STATUS_UNCHANGED", "status", "Trạng thái không thay đổi.");
        }
        if (!from.allowedNext().contains(to)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION",
                    "Không thể chuyển từ " + from.label() + " sang " + to.label()
                            + ". Ngựa phải qua Cần theo dõi trước khi về Đủ điều kiện.",
                    Map.of("status", "Chuyển trạng thái không hợp lệ."));
        }
        hh.changeStatus(to, clock.now());
        history.save(new HealthStatusChange(horse.getId(), from, to, why, account(actor), clock.now()));
        audit.record(actor, "HEALTH_STATUS_CHANGED", "HORSE", horse.getId(),
                Map.of("from", from.name(), "to", to.name(), "reason", why));
        if (to.forbidsTraining() && locks.findByHorseIdAndLiftedAtIsNull(horse.getId()).isEmpty()) {
            placeLock(actor, horse, LockKind.AUTOMATIC, to.label() + ": " + why, null);
        }
    }

    private TrainingLock placeLock(AccountPrincipal actor, Horse horse, LockKind kind, String reason,
                                   LocalDate reviewDate) {
        TrainingLock lock = locks.save(new TrainingLock(horse.getId(), kind, reason, account(actor), clock.now(),
                reviewDate));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("lockId", lock.getId());
        details.put("kind", kind.name());
        details.put("reason", reason);
        if (reviewDate != null) {
            details.put("reviewDate", reviewDate.toString());
        }
        audit.record(actor, "LOCK_PLACED", "HORSE", horse.getId(), details);
        trainers.notifyTrainer(horse.getId(),
                "Ngựa \"" + horse.getName() + "\" bị khóa huấn luyện: " + reason + ".", "/horses/" + horse.getId());
        events.publishEvent(new LockActivated(horse.getId()));
        return lock;
    }

    private HorseHealthView view(Long horseId, boolean withHistory) {
        Horse horse = horse(horseId);
        HorseHealth hh = healthOf(horseId);
        TrainingLock lock = locks.findByHorseIdAndLiftedAtIsNull(horseId).orElse(null);
        List<LockReviewNote> lockNotes = lock == null ? List.of() : notes.findByLockIdOrderByCreatedAtAsc(lock.getId());
        return new HorseHealthView(horse, hh, lock, activity.activityOf(horseId),
                Readiness.of(hh.getStatus(), lock != null), lockNotes,
                withHistory ? history.findByHorseIdOrderByChangedAtDescIdDesc(horseId) : List.of(),
                openRequest(horseId));
    }

    private PendingLockRequest openRequest(Long horseId) {
        return pending.findByHorseIdAndWithdrawnAtIsNullAndAppliedAtIsNull(horseId).orElse(null);
    }

    private void requireIdle(Long horseId) {
        if (activity.activityOf(horseId) == ActivityStatus.TRAINING) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_TRAINING",
                    "Ngựa đang tập. Hãy đăng ký yêu cầu khóa chờ: khóa sẽ có hiệu lực khi buổi tập kết thúc.");
        }
    }

    private static void requireVisible(AccountPrincipal user, Horse horse) {
        if (user.role() == Role.HORSE_OWNER && !Objects.equals(horse.getOwner().getId(), user.id())) {
            throw ApiException.forbidden();
        }
    }

    private Horse horse(Long horseId) {
        return horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
    }

    private Horse managedHorse(Long horseId) {
        Horse horse = horse(horseId);
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: hồ sơ chỉ xem.");
        }
        return horse;
    }

    private HorseHealth healthOf(Long horseId) {
        return health.findById(horseId).orElseThrow(() -> ApiException.notFound("hồ sơ sức khỏe"));
    }

    private UserAccount account(AccountPrincipal user) {
        return user == null ? null : accounts.findById(user.id()).orElse(null);
    }

    private static String requireText(String value, String field, String code, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.invalid(code, field, message);
        }
        return value.trim();
    }
}
