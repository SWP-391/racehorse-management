package vn.racehorse.schedule.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.events.DomainEvents.GroomUnavailable;
import vn.racehorse.schedule.dto.GroomScheduleRange;
import vn.racehorse.schedule.dto.GroomScheduleRangeInput;
import vn.racehorse.schedule.entity.GroomLeave;
import vn.racehorse.schedule.entity.GroomWorkSchedule;
import vn.racehorse.schedule.repository.GroomLeaveRepository;
import vn.racehorse.schedule.repository.GroomWorkScheduleRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.training.service.ClubSettings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The fixed weekly barn hours of a Groom and their leave (BR-166, BR-167, BR-168). The same hours apply every week;
 * leave and sudden absence are separate records. Also answers the questions the availability rules, the "làm thay"
 * flag and the evening reminder ask (TECH-144 to TECH-148).
 */
@Service
public class GroomScheduleService {

    private static final String[] WEEKDAYS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final GroomWorkScheduleRepository schedules;
    private final GroomLeaveRepository leaves;
    private final UserAccountRepository accounts;
    private final ClubSettings settings;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final ApplicationEventPublisher events;

    public GroomScheduleService(GroomWorkScheduleRepository schedules, GroomLeaveRepository leaves,
                                UserAccountRepository accounts, ClubSettings settings, AccessGuard access,
                                AuditService audit, ClubClock clock, ApplicationEventPublisher events) {
        this.schedules = schedules;
        this.leaves = leaves;
        this.accounts = accounts;
        this.settings = settings;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.events = events;
    }

    // ---- schedule ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<GroomScheduleRange> view(Long accountId) {
        AccountPrincipal user = access.require(Permission.VIEW_SCHEDULES);
        if (user.role() == Role.GROOM && !user.id().equals(accountId)) {
            throw ApiException.forbidden();
        }
        accounts.findById(accountId).orElseThrow(() -> ApiException.notFound("tài khoản"));
        return ranges(accountId);
    }

    /** Replaces the whole schedule of an active Groom in one transaction (BR-166). */
    @Transactional
    public List<GroomScheduleRange> replace(Long accountId, List<GroomScheduleRangeInput> input) {
        AccountPrincipal actor = access.require(Permission.MANAGE_SCHEDULES);
        UserAccount account = accounts.findByIdForUpdate(accountId).orElseThrow(() -> ApiException.notFound("tài khoản"));
        if (account.getRole() != Role.GROOM || !account.isActive()) {
            throw ApiException.invalid("VALIDATION_FAILED", "schedule", "Chỉ đặt lịch làm cho Groom đang hoạt động.");
        }
        return write(actor, account, input);
    }

    /** Called by the account creation, in its transaction; the caller has checked the permission (BR-166). */
    @Transactional
    public void setOnCreate(AccountPrincipal actor, UserAccount groom, List<GroomScheduleRangeInput> input) {
        write(actor, groom, input == null ? List.of() : input);
    }

    /** A Groom who gets another role no longer has barn hours; leave history stays (TECH-144). */
    @Transactional
    public void clearOnRoleChange(AccountPrincipal actor, UserAccount account) {
        List<GroomScheduleRange> before = ranges(account.getId());
        if (before.isEmpty()) {
            return;
        }
        schedules.deleteByAccountId(account.getId());
        audit.record(actor, "SCHEDULE_CLEARED", "ACCOUNT", account.getId(),
                Map.of("username", account.getUsername(), "before", summary(before)));
    }

    private List<GroomScheduleRange> write(AccountPrincipal actor, UserAccount account, List<GroomScheduleRangeInput> input) {
        List<GroomScheduleRange> parsed = parse(input);
        List<GroomScheduleRange> before = ranges(account.getId());
        schedules.deleteByAccountId(account.getId());
        schedules.flush();
        for (GroomScheduleRange r : parsed) {
            schedules.save(new GroomWorkSchedule(account.getId(), r.weekday(), r.startMin(), r.endMin()));
        }
        if (!before.equals(parsed)) {
            audit.record(actor, "SCHEDULE_CHANGED", "ACCOUNT", account.getId(), Map.of(
                    "username", account.getUsername(), "before", summary(before), "after", summary(parsed)));
        }
        return parsed;
    }

    /** Start before end, no overlap inside a weekday, whole input or nothing (AC-320). */
    public static List<GroomScheduleRange> parse(List<GroomScheduleRangeInput> input) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<GroomScheduleRange> ranges = new ArrayList<>();
        List<GroomScheduleRangeInput> list = input == null ? List.of() : input;
        for (int i = 0; i < list.size(); i++) {
            GroomScheduleRangeInput r = list.get(i);
            String key = "ranges[" + i + "]";
            if (r == null || r.weekday() == null || r.weekday() < 1 || r.weekday() > 7) {
                errors.put(key, "Chọn thứ trong tuần.");
                continue;
            }
            Integer start = minutes(r.start(), false);
            Integer end = minutes(r.end(), true);
            if (start == null || end == null) {
                errors.put(key, "Giờ không hợp lệ (định dạng HH:mm).");
            } else if (end <= start) {
                errors.put(key, WEEKDAYS[r.weekday() - 1] + ": giờ kết thúc phải sau giờ bắt đầu.");
            } else {
                ranges.add(new GroomScheduleRange(r.weekday(), start, end));
            }
        }
        if (errors.isEmpty()) {
            List<GroomScheduleRange> sorted = ranges.stream()
                    .sorted(Comparator.comparingInt(GroomScheduleRange::weekday).thenComparingInt(GroomScheduleRange::startMin)).toList();
            for (int i = 1; i < sorted.size(); i++) {
                GroomScheduleRange a = sorted.get(i - 1);
                GroomScheduleRange b = sorted.get(i);
                if (a.weekday() == b.weekday() && b.startMin() < a.endMin()) {
                    errors.put("ranges", WEEKDAYS[a.weekday() - 1] + ": các khoảng giờ không được chồng nhau.");
                    break;
                }
            }
            ranges = new ArrayList<>(sorted);
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Lịch làm chưa hợp lệ.", errors);
        }
        return ranges;
    }

    private static Integer minutes(String value, boolean endOfDay) {
        if (value == null || !value.trim().matches("\\d{1,2}:\\d{2}")) {
            return null;
        }
        String[] parts = value.trim().split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        if (m > 59 || h > 24 || (h == 24 && (m != 0 || !endOfDay))) {
            return null;
        }
        return h * 60 + m;
    }

    private static String summary(List<GroomScheduleRange> ranges) {
        return ranges.isEmpty() ? "" : ranges.stream()
                .map(r -> WEEKDAYS[r.weekday() - 1] + " " + r.start() + "-" + r.end()).collect(Collectors.joining("; "));
    }

    private List<GroomScheduleRange> ranges(Long accountId) {
        return schedules.findByAccountIdOrderByWeekdayAscStartMinAsc(accountId).stream()
                .map(s -> new GroomScheduleRange(s.getWeekday(), s.getStartMin(), s.getEndMin())).toList();
    }

    // ---- what the other parts ask ---------------------------------------------------------------------------

    /** The barn hours of one club day, empty when the Groom has no schedule or the weekday is off (BR-166). */
    @Transactional(readOnly = true)
    public List<GroomScheduleRange> rangesOn(Long groomId, LocalDate date) {
        return schedules.findByAccountIdAndWeekday(groomId, date.getDayOfWeek().getValue()).stream()
                .map(s -> new GroomScheduleRange(s.getWeekday(), s.getStartMin(), s.getEndMin()))
                .sorted(Comparator.comparingInt(GroomScheduleRange::startMin)).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasSchedule(Long groomId) {
        return schedules.countByAccountId(groomId) > 0;
    }

    @Transactional(readOnly = true)
    public boolean onLeave(Long groomId, LocalDate date) {
        return leaves.existsByAccountIdAndLeaveDateAndCancelledAtIsNull(groomId, date);
    }

    /** True when the Groom has barn hours and the instant falls outside them, or the Groom is on leave that day. */
    @Transactional(readOnly = true)
    public boolean outsideHoursOrOnLeave(Long groomId, Instant at) {
        ZonedDateTime time = clock.toClubTime(at);
        LocalDate day = time.toLocalDate();
        if (onLeave(groomId, day)) {
            return true;
        }
        if (!hasSchedule(groomId)) {
            return false;
        }
        int minute = time.getHour() * 60 + time.getMinute();
        return rangesOn(groomId, day).stream().noneMatch(r -> minute >= r.startMin() && minute < r.endMin());
    }

    /** On duty that day: no schedule at all or hours that weekday, and no leave (the 18:00 reminder, BR-128). */
    @Transactional(readOnly = true)
    public boolean onDuty(Long groomId, LocalDate date) {
        if (onLeave(groomId, date)) {
            return false;
        }
        return !hasSchedule(groomId) || !rangesOn(groomId, date).isEmpty();
    }

    // ---- leave ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<GroomLeave> leaveOf(Long accountId, LocalDate from, LocalDate to) {
        AccountPrincipal user = access.require(Permission.VIEW_SCHEDULES);
        if (user.role() == Role.GROOM && !user.id().equals(accountId)) {
            throw ApiException.forbidden();
        }
        accounts.findById(accountId).orElseThrow(() -> ApiException.notFound("tài khoản"));
        LocalDate start = from == null ? clock.today() : from;
        LocalDate end = to == null ? start.plusDays(365) : to;
        return leaves.findByAccountIdAndLeaveDateBetweenOrderByLeaveDateAscIdAsc(accountId, start, end);
    }

    /** Leave for today or later; the "đột xuất" label only reminds and never blocks (BR-167, BR-168). */
    @Transactional
    public GroomLeave recordLeave(Long accountId, LocalDate date, String reason) {
        AccountPrincipal actor = access.require(Permission.MANAGE_SCHEDULES);
        UserAccount account = accounts.findByIdForUpdate(accountId).orElseThrow(() -> ApiException.notFound("tài khoản"));
        if (account.getRole() != Role.GROOM || !account.isActive()) {
            throw ApiException.invalid("VALIDATION_FAILED", "date", "Chỉ ghi nghỉ phép cho Groom đang hoạt động.");
        }
        if (date == null) {
            throw ApiException.invalid("VALIDATION_FAILED", "date", "Bắt buộc chọn ngày nghỉ.");
        }
        LocalDate today = clock.today();
        if (date.isBefore(today)) {
            throw ApiException.invalid("LEAVE_IN_PAST", "date", "Không ghi nghỉ phép cho ngày đã qua.");
        }
        if (leaves.existsByAccountIdAndLeaveDateAndCancelledAtIsNull(accountId, date)) {
            throw leaveExists(date);
        }
        int noticeDays = (int) ChronoUnit.DAYS.between(today, date);
        boolean late = noticeDays < settings.leaveNoticeDays();
        UserAccount recorder = accounts.findById(actor.id()).orElseThrow();
        String text = reason == null || reason.isBlank() ? null : reason.trim();
        GroomLeave leave;
        try {
            leave = leaves.saveAndFlush(new GroomLeave(accountId, date, noticeDays, late, text, recorder, clock.now()));
        } catch (DataIntegrityViolationException e) {
            throw leaveExists(date);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", account.getUsername());
        details.put("date", date.toString());
        details.put("late", late);
        if (text != null) {
            details.put("reason", text);
        }
        audit.record(actor, "LEAVE_RECORDED", "ACCOUNT", accountId, details);
        // The assigned Head Trainers are asked to reassign sessions of that day; nothing is changed (BR-168).
        events.publishEvent(new GroomUnavailable(accountId, account.getDisplayName(),
                "nghỉ phép ngày " + date.format(DAY_MONTH), date));
        return leave;
    }

    @Transactional
    public GroomLeave cancelLeave(Long leaveId) {
        AccountPrincipal actor = access.require(Permission.MANAGE_SCHEDULES);
        GroomLeave leave = leaves.findById(leaveId).orElseThrow(() -> ApiException.notFound("ngày nghỉ phép"));
        if (leave.isCancelled()) {
            throw new ApiException(HttpStatus.CONFLICT, "LEAVE_CANCELLED", "Ngày nghỉ phép này đã được hủy.");
        }
        if (leave.getLeaveDate().isBefore(clock.today())) {
            throw new ApiException(HttpStatus.CONFLICT, "LEAVE_IN_PAST", "Không hủy được ngày nghỉ phép đã qua.");
        }
        leave.cancel(accounts.findById(actor.id()).orElseThrow(), clock.now());
        audit.record(actor, "LEAVE_CANCELLED", "ACCOUNT", leave.getAccountId(),
                Map.of("date", leave.getLeaveDate().toString()));
        return leave;
    }

    private static ApiException leaveExists(LocalDate date) {
        String message = "Đã ghi nghỉ phép ngày " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".";
        return new ApiException(HttpStatus.CONFLICT, "LEAVE_EXISTS", message, Map.of("date", message));
    }
}
