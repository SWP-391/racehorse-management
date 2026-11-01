package vn.racehorse.training.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.common.paging.Search;
import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.horse.service.HorseTrainerService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.training.dto.AssignRequest;
import vn.racehorse.training.dto.AssignableDto;
import vn.racehorse.training.dto.AssignedSessionDto;
import vn.racehorse.training.dto.BoardRowDto;
import vn.racehorse.training.dto.DeclineRequest;
import vn.racehorse.training.dto.EndRequest;
import vn.racehorse.training.dto.GraceDto;
import vn.racehorse.training.dto.GraceRequest;
import vn.racehorse.training.dto.PersonRef;
import vn.racehorse.training.dto.PhaseDto;
import vn.racehorse.training.dto.PlanDto;
import vn.racehorse.training.dto.PlanRequest;
import vn.racehorse.training.dto.PlanShiftPreview;
import vn.racehorse.training.dto.ReasonRequest;
import vn.racehorse.training.dto.ScheduleItemDto;
import vn.racehorse.training.dto.SessionAssignable;
import vn.racehorse.training.dto.SessionDetailDto;
import vn.racehorse.training.dto.SessionDto;
import vn.racehorse.training.dto.SessionInput;
import vn.racehorse.training.dto.SessionRequest;
import vn.racehorse.training.dto.ShiftRequest;
import vn.racehorse.training.dto.StartRequest;
import vn.racehorse.training.dto.TrainingThresholdDto;
import vn.racehorse.training.dto.TrainingThresholdRequest;
import vn.racehorse.training.dto.TrainingVersionDto;
import vn.racehorse.training.dto.UnavailableDto;
import vn.racehorse.training.entity.PlanVersion;
import vn.racehorse.training.entity.TrainingPlan;
import vn.racehorse.training.entity.TrainingSession;
import vn.racehorse.training.service.ClubSettings;
import vn.racehorse.training.service.PlanService;
import vn.racehorse.training.service.ProgressService;
import vn.racehorse.training.service.SessionService;
import vn.racehorse.training.service.TrainingEnums.PlanState;
import vn.racehorse.training.service.TrainingEnums.SessionState;
import vn.racehorse.video.service.TrialVideoService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Huấn luyện", description = "Giáo án, buổi tập, phân công và tiến độ")
@RestController
@RequestMapping("/api")
public class TrainingController {

    private final PlanService plans;
    private final SessionService sessions;
    private final ProgressService progress;
    private final ClubSettings settings;
    private final TrialVideoService videos;
    private final HorseTrainerService trainers;
    private final AccessGuard access;

    public TrainingController(PlanService plans, SessionService sessions, ProgressService progress,
                              ClubSettings settings, TrialVideoService videos, HorseTrainerService trainers,
                              AccessGuard access) {
        this.plans = plans;
        this.sessions = sessions;
        this.progress = progress;
        this.settings = settings;
        this.videos = videos;
        this.trainers = trainers;
        this.access = access;
    }

    // ---- DTOs ---------------------------------------------------------------------------------------------

    /** Planned end = planned start + planned duration, club-local "HH:mm"; null while the session has no start time (BR-190). */

    private PlanDto toDto(TrainingPlan p) {
        UserAccount trainer = trainers.effectiveTrainerOf(p.getHorseId());
        return new PlanDto(p.getId(), p.getHorseId(), p.getTitle(), p.getState(), p.getCancelReason(),
                p.completionSuggested(plans.today()), p.getPhases().stream().map(PhaseDto::of).toList(),
                PersonRef.of(trainer), canWrite(trainer));
    }

    /** True when the caller is the effective trainer, so the client shows write controls from the server's view. */
    private boolean canWrite(UserAccount trainer) {
        AccountPrincipal user = access.current();
        return trainer != null && trainer.getId().equals(user.id());
    }

    private SessionDto sessionDto(TrainingSession s) {
        UserAccount trainer = trainers.effectiveTrainerOf(s.getHorseId());
        return SessionDto.of(s, trainer, canWrite(trainer));
    }

    // ---- plans --------------------------------------------------------------------------------------------

    @Operation(summary = "Giáo án của một con ngựa")
    @GetMapping("/horses/{horseId}/plans")
    public PageResponse<PlanDto> plansOf(@PathVariable Long horseId,
                                         @RequestParam(required = false) PlanState state,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(plans.plansOf(horseId).stream()
                .filter(p -> state == null || p.getState() == state)
                .filter(p -> Search.matches(params.q(), p.getTitle()))
                .map(this::toDto).toList(), params);
    }

    @Operation(summary = "Tạo giáo án")
    @PostMapping("/horses/{horseId}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto create(@PathVariable Long horseId, @Valid @RequestBody PlanRequest request) {
        return toDto(plans.create(horseId, request.title(), request.phases()));
    }

    @Operation(summary = "Chi tiết giáo án")
    @GetMapping("/plans/{id}")
    public PlanDto plan(@PathVariable Long id) {
        return toDto(plans.get(id));
    }

    @Operation(summary = "Cập nhật giáo án")
    @PutMapping("/plans/{id}")
    public PlanDto update(@PathVariable Long id, @Valid @RequestBody PlanRequest request) {
        return toDto(plans.update(id, request.title(), request.phases()));
    }

    @Operation(summary = "Kích hoạt giáo án")
    @PostMapping("/plans/{id}/activate")
    public PlanDto activate(@PathVariable Long id) {
        return toDto(plans.activate(id));
    }

    @Operation(summary = "Hoàn thành giáo án")
    @PostMapping("/plans/{id}/complete")
    public PlanDto complete(@PathVariable Long id) {
        return toDto(plans.complete(id));
    }

    @Operation(summary = "Hủy giáo án")
    @PostMapping("/plans/{id}/cancel")
    public PlanDto cancel(@PathVariable Long id, @RequestBody ReasonRequest request) {
        return toDto(plans.cancel(id, request.reason()));
    }

    @Operation(summary = "Giữ nguyên giáo án sau khi xem xét")
    @PostMapping("/plans/{id}/review/keep")
    public List<SessionDto> keep(@PathVariable Long id) {
        return plans.keepAfterLock(id).stream().map(this::sessionDto).toList();
    }

    @Operation(summary = "Dời giáo án")
    @PostMapping("/plans/{id}/shift")
    public List<PlanShiftPreview> shift(@PathVariable Long id, @RequestBody ShiftRequest request) {
        return plans.shift(id, request.days(), Boolean.TRUE.equals(request.dryRun()));
    }

    @Operation(summary = "Các phiên bản giáo án")
    @GetMapping("/plans/{id}/versions")
    public PageResponse<TrainingVersionDto> versions(@PathVariable Long id,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(plans.versionsOf(id).stream().map(v -> new TrainingVersionDto(v.getVersionNo(), v.getReason(),
                v.getCreatedBy() == null ? "Hệ thống" : v.getCreatedBy().getDisplayName(), v.getCreatedAt()))
                .filter(v -> Search.matches(params.q(), v.reason(), v.createdBy(), "v" + v.versionNo()))
                .toList(), params);
    }

    @Operation(summary = "Khôi phục một phiên bản giáo án")
    @PostMapping("/plans/{id}/versions/{versionNo}/restore")
    public TrainingVersionDto restore(@PathVariable Long id, @PathVariable int versionNo) {
        PlanVersion v = plans.restore(id, versionNo);
        return new TrainingVersionDto(v.getVersionNo(), v.getReason(),
                v.getCreatedBy() == null ? "Hệ thống" : v.getCreatedBy().getDisplayName(), v.getCreatedAt());
    }

    // ---- sessions -----------------------------------------------------------------------------------------

    @Operation(summary = "Buổi tập của giáo án")
    @GetMapping("/plans/{id}/sessions")
    public PageResponse<SessionDto> sessionsOf(@PathVariable Long id,
                                               @RequestParam(required = false) SessionState state,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        PageParams params = PageParams.of(page, size, null);
        return Paging.page(sessions.ofPlan(id).stream()
                .filter(s -> state == null || s.getState() == state)
                .filter(s -> inRange(s.getScheduledDate(), from, to))
                .map(this::sessionDto).toList(), params);
    }

    @Operation(summary = "Tạo buổi tập")
    @PostMapping("/plans/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDto createSession(@PathVariable Long id, @Valid @RequestBody SessionRequest r) {
        return sessionDto(sessions.create(id, new SessionInput(r.date(), r.startTime(), r.type(),
                r.plannedDurationMin(), r.targetDistanceM(), r.targetSurface(), r.responsiblePersonId(),
                Boolean.TRUE.equals(r.draft()), Boolean.TRUE.equals(r.acknowledgeWarning()))));
    }

    @Operation(summary = "Chi tiết buổi tập")
    @GetMapping("/sessions/{id}")
    public SessionDetailDto session(@PathVariable Long id) {
        TrainingSession s = sessions.get(id);
        return new SessionDetailDto(sessionDto(s),
                s.getState() == SessionState.IN_PROGRESS && sessions.pendingLockAtEnd(s.getHorseId()));
    }

    @Operation(summary = "Xếp lịch buổi tập")
    @PostMapping("/sessions/{id}/schedule")
    public SessionDto schedule(@PathVariable Long id) {
        return sessionDto(sessions.schedule(id));
    }

    @Operation(summary = "Phân công người phụ trách buổi tập")
    @PutMapping("/sessions/{id}/responsible")
    public SessionDto assign(@PathVariable Long id, @RequestBody AssignRequest request) {
        return sessionDto(sessions.assign(id, request.responsiblePersonId(),
                Boolean.TRUE.equals(request.acknowledgeWarning())));
    }

    /** The responsible Groom refuses the assignment with a reason (BR-159). */
    @Operation(summary = "Từ chối phân công buổi tập (kèm lý do)")
    @PostMapping("/sessions/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable Long id, @RequestBody DeclineRequest request) {
        sessions.decline(id, request.reason());
    }

    /** The Grooms the assigned trainer may choose for a time range, and why each other one cannot be (BR-162). */
    @Operation(summary = "Người chăm ngựa có thể được phân công")
    @GetMapping("/sessions/assignable-grooms")
    public AssignableDto assignableGrooms(
            @RequestParam Long horseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam Integer durationMin,
            @RequestParam(required = false) Long sessionId) {
        SessionAssignable a = sessions.assignable(horseId, date, startTime, durationMin, sessionId);
        return new AssignableDto(a.available().stream().map(PersonRef::of).toList(),
                a.unavailable().stream().map(b -> new UnavailableDto(b.groom().getId(), b.groom().getDisplayName(),
                        b.why().reason(), b.why().detail(), b.why().sessionId())).toList(),
                a.nextFree() == null ? null : a.nextFree().toString());
    }

    @Operation(summary = "Xác nhận đã nhận buổi tập")
    @PostMapping("/sessions/{id}/acknowledge")
    public AssignedSessionDto acknowledge(@PathVariable Long id) {
        return AssignedSessionDto.of(sessions.acknowledge(id));
    }

    @Operation(summary = "Buổi tập của tôi")
    @GetMapping("/my-sessions")
    public PageResponse<AssignedSessionDto> mySessions(
            @RequestParam(required = false) SessionState state,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(sessions.assignedToMe().stream()
                .filter(a -> state == null || a.session().getState() == state)
                .filter(a -> inRange(a.session().getScheduledDate(), from, to))
                .filter(a -> Search.matches(params.q(), a.horseName()))
                .map(AssignedSessionDto::of).toList(), params);
    }

    @Operation(summary = "Hủy buổi tập")
    @PostMapping("/sessions/{id}/cancel")
    public SessionDto cancelSession(@PathVariable Long id, @RequestBody ReasonRequest request) {
        return sessionDto(sessions.cancel(id, request.reason()));
    }

    @Operation(summary = "Bắt đầu buổi tập")
    @PostMapping("/sessions/{id}/start")
    public SessionDto start(@PathVariable Long id, @RequestBody(required = false) StartRequest request) {
        return sessionDto(sessions.start(id, request != null && Boolean.TRUE.equals(request.acknowledgeWarning())));
    }

    @Operation(summary = "Kết thúc buổi tập")
    @PostMapping("/sessions/{id}/end")
    public SessionDto end(@PathVariable Long id, @Valid @RequestBody EndRequest request) {
        return sessionDto(sessions.end(id, request.indicators(), request.earlyEndReason()));
    }

    @Operation(summary = "Ghi chỉ số sau buổi tập")
    @PutMapping("/sessions/{id}/indicators")
    public SessionDto completeIndicators(@PathVariable Long id, @Valid @RequestBody EndRequest request) {
        return sessionDto(sessions.completeAfterAutoClose(id, request.indicators(), request.actualEndTime()));
    }

    @Operation(summary = "Danh sách nhân sự huấn luyện")
    @GetMapping("/staff")
    public List<PersonRef> staff() {
        return sessions.staff().stream().map(PersonRef::of).toList();
    }

    // ---- board, schedule, settings ------------------------------------------------------------------------

    @Operation(summary = "Bảng tiến độ")
    @GetMapping("/progress")
    public PageResponse<BoardRowDto> board(@RequestParam(required = false) HealthStatus status,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) Long trainerId) {
        PageParams params = PageParams.of(page, size, q);
        List<BoardRowDto> rows = progress.board().stream()
                .filter(r -> status == null || r.eligibility().status() == status)
                .filter(r -> trainerId == null || java.util.Optional.ofNullable(trainers.effectiveTrainer(r.horse()))
                        .map(t -> t.getId().equals(trainerId)).orElse(false))
                .filter(r -> Search.matches(params.q(), r.horse().getName()))
                .map(r -> new BoardRowDto(r.horse().getId(), r.horse().getName(),
                r.eligibility().status(), r.eligibility().readiness(), r.eligibility().locked(),
                r.eligibility().unexamined(), r.currentPlan() == null ? null : toDto(r.currentPlan()),
                r.recentSessions().stream().map(this::sessionDto).toList(),
                r.trend().stream().map(this::sessionDto).toList(),
                PersonRef.of(trainers.effectiveTrainer(r.horse())), trainers.canWrite(r.horse(), access.current())))
                .toList();
        return Paging.page(rows, params);
    }

    @Operation(summary = "Lịch tập của một con ngựa")
    @GetMapping("/horses/{horseId}/schedule")
    public PageResponse<ScheduleItemDto> horseSchedule(
            @PathVariable Long horseId,
            @RequestParam(required = false) SessionState state,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageParams params = PageParams.of(page, size, null);
        List<TrainingSession> items = progress.schedule(horseId).stream()
                .filter(s -> state == null || s.getState() == state)
                .filter(s -> inRange(s.getScheduledDate(), from, to))
                .toList();
        Map<Long, Long> counts = videos.counts(items.stream().map(TrainingSession::getId).toList());
        return Paging.page(items.stream().map(s -> ScheduleItemDto.of(s, counts.getOrDefault(s.getId(), 0L))).toList(),
                params);
    }

    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    @Operation(summary = "Xem thời gian ân hạn")
    @GetMapping("/settings/grace-period")
    public GraceDto gracePeriod() {
        return new GraceDto(settings.gracePeriodMinutesForManager());
    }

    @Operation(summary = "Đặt thời gian ân hạn")
    @PutMapping("/settings/grace-period")
    public GraceDto setGracePeriod(@Valid @RequestBody GraceRequest request) {
        return new GraceDto(settings.setGracePeriodMinutes(request.minutes()));
    }

    @Operation(summary = "Xem ngưỡng đổi muộn")
    @GetMapping("/settings/late-change-threshold")
    public TrainingThresholdDto lateChangeThreshold() {
        return new TrainingThresholdDto(settings.lateChangeThresholdHoursForManager());
    }

    @Operation(summary = "Đặt ngưỡng đổi muộn")
    @PutMapping("/settings/late-change-threshold")
    public TrainingThresholdDto setLateChangeThreshold(@Valid @RequestBody TrainingThresholdRequest request) {
        return new TrainingThresholdDto(settings.setLateChangeThresholdHours(request.hours()));
    }
}
