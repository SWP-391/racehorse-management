package vn.racehorse.training.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.health.dto.HealthEligibility;
import vn.racehorse.health.service.HealthService;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.training.dto.PlanPhaseInput;
import vn.racehorse.training.dto.PlanShiftPreview;
import vn.racehorse.training.dto.SessionTimesSlot;
import vn.racehorse.training.entity.PlanPhase;
import vn.racehorse.training.entity.PlanVersion;
import vn.racehorse.training.entity.TrainingPlan;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.repository.PlanVersionRepository;
import vn.racehorse.training.repository.TrainingPlanRepository;
import vn.racehorse.training.repository.TrainingSessionRepository;
import vn.racehorse.training.service.TrainingEnums.PlanState;
import vn.racehorse.training.service.TrainingEnums.SessionState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Training plans, phases, post-lock review and plan versions (REQ-002, BR-030, BR-031, BR-022, BR-023). */
@Service
public class PlanService {

    private static final Set<SessionState> SHIFTABLE =
            EnumSet.of(SessionState.DRAFT, SessionState.SCHEDULED, SessionState.AWAITING_REVIEW);

    private final TrainingPlanRepository plans;
    private final TrainingSessionRepository sessions;
    private final PlanVersionRepository versions;
    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final HealthService health;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final ObjectMapper json;

    public PlanService(TrainingPlanRepository plans, TrainingSessionRepository sessions, PlanVersionRepository versions,
                       HorseRepository horses, UserAccountRepository accounts, HealthService health,
                       AccessGuard access, AuditService audit, ClubClock clock, ObjectMapper json) {
        this.plans = plans;
        this.sessions = sessions;
        this.versions = versions;
        this.horses = horses;
        this.accounts = accounts;
        this.health = health;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.json = json;
    }

    // ---- reads --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TrainingPlan> plansOf(Long horseId) {
        staffViewer();
        horse(horseId);
        return plans.findByHorseIdOrderByCreatedAtDesc(horseId);
    }

    @Transactional(readOnly = true)
    public TrainingPlan get(Long planId) {
        staffViewer();
        return plan(planId);
    }

    @Transactional(readOnly = true)
    public List<PlanVersion> versionsOf(Long planId) {
        staffViewer();
        plan(planId);
        return versions.findByPlanIdOrderByVersionNoAsc(planId);
    }

    public LocalDate today() {
        return clock.toClubTime(clock.now()).toLocalDate();
    }

    // ---- plan lifecycle -----------------------------------------------------------------------------------

    @Transactional
    public TrainingPlan create(Long horseId, String title, List<PlanPhaseInput> phases) {
        AccountPrincipal actor = access.requireTrainerOf(Permission.MANAGE_PLANS, horseId);
        Horse horse = managedHorse(horseId);
        TrainingPlan plan = new TrainingPlan(horse.getId(), blankToNull(title), account(actor), clock.now());
        plan.replacePhases(toPhases(phases), clock.now());
        plan = plans.save(plan);
        audit.record(actor, "PLAN_CREATED", "PLAN", plan.getId(),
                Map.of("horseId", horseId, "phases", plan.getPhases().size()));
        return plan;
    }

    @Transactional
    public TrainingPlan update(Long planId, String title, List<PlanPhaseInput> phases) {
        access.require(Permission.MANAGE_PLANS);
        TrainingPlan plan = plan(planId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.MANAGE_PLANS, plan.getHorseId());
        managedHorse(plan.getHorseId());
        requireState(plan, EnumSet.of(PlanState.DRAFT, PlanState.ACTIVE), "Chỉ sửa được giáo án Nháp hoặc Đang thực hiện.");
        plan.setTitle(blankToNull(title), clock.now());
        plan.replacePhases(toPhases(phases), clock.now());
        audit.record(actor, "PLAN_UPDATED", "PLAN", planId, Map.of("phases", plan.getPhases().size()));
        return plan;
    }

    @Transactional
    public TrainingPlan activate(Long planId) {
        access.require(Permission.MANAGE_PLANS);
        TrainingPlan plan = plan(planId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.MANAGE_PLANS, plan.getHorseId());
        managedHorse(plan.getHorseId());
        requireState(plan, EnumSet.of(PlanState.DRAFT), "Chỉ kích hoạt được giáo án Nháp.");
        if (plan.getPhases().isEmpty()) {
            throw ApiException.invalid("PLAN_WITHOUT_PHASES", "phases", "Giáo án cần ít nhất một giai đoạn.");
        }
        // Serialised with scheduling on the horse row; the unique index is the last line against a race (BR-154).
        health.lockForUpdate(plan.getHorseId());
        plans.findFirstByHorseIdAndState(plan.getHorseId(), PlanState.ACTIVE).ifPresent(running -> {
            throw activePlanExists(running);
        });
        plan.moveTo(PlanState.ACTIVE, clock.now());
        try {
            plans.saveAndFlush(plan);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw plans.findFirstByHorseIdAndState(plan.getHorseId(), PlanState.ACTIVE)
                    .map(PlanService::activePlanExists)
                    .orElse(new ApiException(HttpStatus.CONFLICT, "ACTIVE_PLAN_EXISTS",
                            "Ngựa đã có giáo án đang thực hiện."));
        }
        audit.record(actor, "PLAN_ACTIVATED", "PLAN", planId, Map.of());
        return plan;
    }

    /** Completion is always the Head Trainer's decision (BR-031, DEC-022). */
    @Transactional
    public TrainingPlan complete(Long planId) {
        access.require(Permission.MANAGE_PLANS);
        TrainingPlan plan = plan(planId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.MANAGE_PLANS, plan.getHorseId());
        requireState(plan, EnumSet.of(PlanState.ACTIVE), "Chỉ hoàn thành được giáo án Đang thực hiện.");
        plan.moveTo(PlanState.COMPLETED, clock.now());
        audit.record(actor, "PLAN_COMPLETED", "PLAN", planId, Map.of());
        return plan;
    }

    /** Cancelling a plan cancels its upcoming sessions with the same reason; nothing is deleted. */
    @Transactional
    public TrainingPlan cancel(Long planId, String reason) {
        access.require(Permission.MANAGE_PLANS);
        TrainingPlan plan = plan(planId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.MANAGE_PLANS, plan.getHorseId());
        requireState(plan, EnumSet.of(PlanState.DRAFT, PlanState.ACTIVE), "Giáo án đã kết thúc.");
        String why = requireText(reason, "reason", "Bắt buộc nhập lý do.");
        plan.cancel(why, clock.now());
        for (TrainingSession s : sessions.findByPlanIdOrderByScheduledDateAscIdAsc(planId)) {
            if (s.getState().isUpcoming()) {
                s.cancel("Giáo án bị hủy: " + why, clock.now());
            }
        }
        audit.record(actor, "PLAN_CANCELLED", "PLAN", planId, Map.of("reason", why));
        return plan;
    }

    // ---- post-lock review and versions (BR-022, BR-023) ---------------------------------------------------

    /** Keeps the sessions awaiting review on their dates (one confirmation). */
    @Transactional
    public List<TrainingSession> keepAfterLock(Long planId) {
        access.require(Permission.REVIEW_BLOCKED_SESSIONS);
        TrainingPlan plan = activePlan(planId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.REVIEW_BLOCKED_SESSIONS, plan.getHorseId());
        requireNoLock(plan.getHorseId());
        List<TrainingSession> awaiting = sessions.findByPlanIdOrderByScheduledDateAscIdAsc(planId).stream()
                .filter(s -> s.getState() == SessionState.AWAITING_REVIEW).toList();
        awaiting.forEach(s -> s.moveTo(SessionState.SCHEDULED, clock.now()));
        audit.record(actor, "PLAN_REVIEW_KEPT", "PLAN", planId, Map.of("sessions", awaiting.size()));
        return awaiting;
    }

    /**
     * Shifts the remaining plan by a number of days. With dryRun only the preview is returned; otherwise the plan is
     * snapshotted before and after, so the previous version can be restored (TECH-027).
     */
    @Transactional
    public List<PlanShiftPreview> shift(Long planId, Integer days, boolean dryRun) {
        access.require(Permission.REVIEW_BLOCKED_SESSIONS);
        TrainingPlan plan = activePlan(planId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.REVIEW_BLOCKED_SESSIONS, plan.getHorseId());
        if (days == null || days == 0) {
            throw ApiException.invalid("SHIFT_DAYS_REQUIRED", "days", "Nhập số ngày cần dịch (khác 0).");
        }
        LocalDate today = today();
        List<TrainingSession> movable = sessions.findByPlanIdOrderByScheduledDateAscIdAsc(planId).stream()
                .filter(s -> SHIFTABLE.contains(s.getState()) && !s.getScheduledDate().isBefore(today)).toList();
        List<PlanShiftPreview> preview = movable.stream()
                .map(s -> new PlanShiftPreview(s.getId(), s.getScheduledDate(), s.getScheduledDate().plusDays(days)))
                .toList();
        if (preview.stream().anyMatch(p -> p.to().isBefore(today))) {
            throw ApiException.invalid("SHIFT_INTO_PAST", "days", "Không thể dịch buổi tập về quá khứ.");
        }
        if (!dryRun) {
            health.lockForUpdate(plan.getHorseId());
        }
        Map<Long, LocalDate> moved = new HashMap<>();
        movable.forEach(s -> moved.put(s.getId(), s.getScheduledDate().plusDays(days)));
        requireNoOverlapAfterMove(plan.getHorseId(), movable, moved);
        if (dryRun) {
            return preview;
        }
        requireNoLock(plan.getHorseId());
        ensureBaseline(plan, actor, "Trước khi dịch lịch " + days + " ngày");
        for (TrainingSession s : movable) {
            s.reschedule(s.getScheduledDate().plusDays(days), clock.now());
            if (s.getState() == SessionState.AWAITING_REVIEW) {
                s.moveTo(SessionState.SCHEDULED, clock.now());
            }
        }
        for (PlanPhase p : plan.getPhases()) {
            if (!p.getEndDate().isBefore(today)) {
                p.shift(days);
            }
        }
        PlanVersion v = snapshot(plan, actor, "Dịch lịch " + days + " ngày");
        audit.record(actor, "PLAN_SHIFTED", "PLAN", planId,
                Map.of("days", days, "sessions", movable.size(), "version", v.getVersionNo()));
        return preview;
    }

    /** Re-applies a version's dates to sessions not yet run, and records the result as a new version. */
    @Transactional
    public PlanVersion restore(Long planId, int versionNo) {
        access.require(Permission.MANAGE_PLANS);
        TrainingPlan plan = activePlan(planId);
        AccountPrincipal actor = access.requireTrainerOf(Permission.MANAGE_PLANS, plan.getHorseId());
        PlanVersion target = versions.findByPlanIdAndVersionNo(planId, versionNo)
                .orElseThrow(() -> ApiException.notFound("phiên bản giáo án"));
        Snapshot snap = read(target.getSnapshot());
        Map<Long, TrainingSession> byId = new HashMap<>();
        sessions.findByPlanIdOrderByScheduledDateAscIdAsc(planId).forEach(s -> byId.put(s.getId(), s));
        health.lockForUpdate(plan.getHorseId());
        Map<Long, LocalDate> moved = new HashMap<>();
        List<TrainingSession> restored = new ArrayList<>();
        for (Snapshot.SessionDate sd : snap.sessions()) {
            TrainingSession s = byId.get(sd.id());
            if (s != null && SHIFTABLE.contains(s.getState())) {
                moved.put(s.getId(), sd.date());
                restored.add(s);
            }
        }
        requireNoOverlapAfterMove(plan.getHorseId(), restored, moved);
        for (Snapshot.SessionDate sd : snap.sessions()) {
            TrainingSession s = byId.get(sd.id());
            if (s != null && SHIFTABLE.contains(s.getState())) {
                s.reschedule(sd.date(), clock.now());
            }
        }
        Map<Long, Snapshot.PhaseDates> phaseDates = new HashMap<>();
        snap.phases().forEach(p -> phaseDates.put(p.id(), p));
        for (PlanPhase p : plan.getPhases()) {
            Snapshot.PhaseDates d = phaseDates.get(p.getId());
            if (d != null) {
                p.setDates(d.start(), d.end());
            }
        }
        PlanVersion v = snapshot(plan, actor, "Khôi phục phiên bản " + versionNo);
        audit.record(actor, "PLAN_VERSION_RESTORED", "PLAN", planId,
                Map.of("restored", versionNo, "newVersion", v.getVersionNo()));
        return v;
    }

    /** Each moved session, at its new date and unchanged start time, must not overlap any other (BR-121, TECH-088). */
    private void requireNoOverlapAfterMove(Long horseId, List<TrainingSession> movedSessions, Map<Long, LocalDate> moved) {
        List<TrainingSession> all = sessions.findByHorseIdOrderByScheduledDateDescIdDesc(horseId);
        for (TrainingSession s : movedSessions) {
            SessionTimes.requireNoOverlap(new SessionTimesSlot(s.getId(), moved.get(s.getId()), s.getPlannedStartTime(),
                    s.getPlannedDurationMin()), all, moved);
        }
    }

    // ---- snapshots ----------------------------------------------------------------------------------------

    record Snapshot(List<PhaseDates> phases, List<SessionDate> sessions) {
        record PhaseDates(Long id, LocalDate start, LocalDate end) {
        }

        record SessionDate(Long id, LocalDate date, SessionState state) {
        }
    }

    private void ensureBaseline(TrainingPlan plan, AccountPrincipal actor, String reason) {
        if (versions.findFirstByPlanIdOrderByVersionNoDesc(plan.getId()).isEmpty()) {
            snapshot(plan, actor, reason);
        }
    }

    private PlanVersion snapshot(TrainingPlan plan, AccountPrincipal actor, String reason) {
        List<Snapshot.PhaseDates> phases = new ArrayList<>();
        plan.getPhases().forEach(p -> phases.add(new Snapshot.PhaseDates(p.getId(), p.getStartDate(), p.getEndDate())));
        List<Snapshot.SessionDate> sessionDates = new ArrayList<>();
        sessions.findByPlanIdOrderByScheduledDateAscIdAsc(plan.getId()).stream()
                .filter(s -> s.getState().isUpcoming())
                .forEach(s -> sessionDates.add(new Snapshot.SessionDate(s.getId(), s.getScheduledDate(), s.getState())));
        int next = versions.findFirstByPlanIdOrderByVersionNoDesc(plan.getId()).map(v -> v.getVersionNo() + 1).orElse(1);
        String body;
        try {
            body = json.writeValueAsString(new Snapshot(phases, sessionDates));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return versions.save(new PlanVersion(plan.getId(), next, body, reason, account(actor), clock.now()));
    }

    private Snapshot read(String body) {
        try {
            return json.readValue(body, Snapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private List<PlanPhase> toPhases(List<PlanPhaseInput> inputs) {
        List<PlanPhase> result = new ArrayList<>();
        if (inputs == null) {
            return result;
        }
        int ordinal = 1;
        for (PlanPhaseInput in : inputs) {
            String prefix = "phases[" + (ordinal - 1) + "].";
            Map<String, String> errors = new LinkedHashMap<>();
            if (in.name() == null || in.name().isBlank()) errors.put(prefix + "name", "Bắt buộc nhập.");
            if (in.startDate() == null) errors.put(prefix + "startDate", "Bắt buộc nhập.");
            if (in.endDate() == null) errors.put(prefix + "endDate", "Bắt buộc nhập.");
            if (in.goal() == null || in.goal().isBlank()) errors.put(prefix + "goal", "Bắt buộc nhập.");
            if (in.distanceM() == null || in.distanceM() <= 0) errors.put(prefix + "distanceM", "Cự ly phải lớn hơn 0.");
            if (in.weeklyVolumeKm() == null || in.weeklyVolumeKm().signum() <= 0)
                errors.put(prefix + "weeklyVolumeKm", "Khối lượng phải lớn hơn 0.");
            if (in.surface() == null || in.surface().isBlank()) errors.put(prefix + "surface", "Bắt buộc nhập.");
            if (in.startDate() != null && in.endDate() != null && in.endDate().isBefore(in.startDate()))
                errors.put(prefix + "endDate", "Ngày kết thúc phải sau ngày bắt đầu.");
            if (!errors.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "Thông tin giai đoạn chưa hợp lệ.", errors);
            }
            result.add(new PlanPhase(ordinal++, in.name().trim(), in.startDate(), in.endDate(), in.goal().trim(),
                    in.distanceM(), in.weeklyVolumeKm(), in.surface().trim()));
        }
        return result;
    }

    private void staffViewer() {
        AccountPrincipal user = access.require(Permission.VIEW_PLANS);
        if (user.role() == Role.HORSE_OWNER) {
            // Owners see the schedule and comments of their own horses only (PERM-012), not plan internals.
            throw ApiException.forbidden();
        }
    }

    private void requireNoLock(Long horseId) {
        HealthEligibility e = health.eligibility(horseId);
        if (e.locked()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_LOCKED",
                    "Ngựa đang bị khóa huấn luyện: " + e.activeLock().getReason());
        }
    }

    private TrainingPlan activePlan(Long planId) {
        TrainingPlan plan = plan(planId);
        managedHorse(plan.getHorseId());
        requireState(plan, EnumSet.of(PlanState.ACTIVE), "Giáo án chưa ở trạng thái Đang thực hiện.");
        return plan;
    }

    private static ApiException activePlanExists(TrainingPlan running) {
        String title = running.getTitle() == null ? "#" + running.getId() : "\"" + running.getTitle() + "\"";
        return new ApiException(HttpStatus.CONFLICT, "ACTIVE_PLAN_EXISTS",
                "Ngựa đã có giáo án đang thực hiện " + title + ". Hãy hoàn thành hoặc hủy giáo án đó trước.");
    }

    private static void requireState(TrainingPlan plan, Set<PlanState> allowed, String message) {
        if (!allowed.contains(plan.getState())) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_STATE", message);
        }
    }

    private TrainingPlan plan(Long planId) {
        return plans.findById(planId).orElseThrow(() -> ApiException.notFound("giáo án"));
    }

    private Horse horse(Long horseId) {
        return horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
    }

    private Horse managedHorse(Long horseId) {
        Horse horse = horse(horseId);
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED",
                    "Ngựa đã ngừng quản lý: không thể tạo hoặc sửa giáo án.");
        }
        return horse;
    }

    private UserAccount account(AccountPrincipal user) {
        return user == null ? null : accounts.findById(user.id()).orElse(null);
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.invalid("REASON_REQUIRED", field, message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
