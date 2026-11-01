package vn.racehorse.training.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Club settings set only by the Club Manager: grace period before auto-close (BR-094), late-change threshold (BR-123),
 * the notice a leave needs to count as planned (BR-167), the days of cover that turn a supply item to Cần nhập (BR-175).
 */
@Service
public class ClubSettings {

    static final String GRACE_PERIOD = "grace_period_minutes";
    static final String LATE_CHANGE = "late_change_threshold_hours";
    static final String LEAVE_NOTICE = "leave_notice_days";
    static final String STOCK_WARNING = "stock_warning_days";

    private final JdbcTemplate jdbc;
    private final AccessGuard access;
    private final AuditService audit;

    public ClubSettings(JdbcTemplate jdbc, AccessGuard access, AuditService audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
    }

    /** Used by the auto-close job; no permission check. */
    public Duration gracePeriod() {
        String value = jdbc.queryForObject("select value from club_setting where key = ?", String.class, GRACE_PERIOD);
        return Duration.ofMinutes(Long.parseLong(value));
    }

    @Transactional(readOnly = true)
    public int gracePeriodMinutesForManager() {
        access.require(Permission.CONFIGURE_GRACE_PERIOD);
        return (int) gracePeriod().toMinutes();
    }

    @Transactional
    public int setGracePeriodMinutes(Integer minutes) {
        AccountPrincipal actor = access.require(Permission.CONFIGURE_GRACE_PERIOD);
        if (minutes == null || minutes <= 0) {
            throw ApiException.invalid("INVALID_GRACE_PERIOD", "minutes", "Thời gian gia hạn phải lớn hơn 0 phút.");
        }
        int previous = (int) gracePeriod().toMinutes();
        jdbc.update("update club_setting set value = ? where key = ?", String.valueOf(minutes), GRACE_PERIOD);
        audit.record(actor, "SETTING_CHANGED", "SETTING", null,
                Map.of("key", GRACE_PERIOD, "from", previous, "to", minutes));
        return minutes;
    }

    /** Used when the responsible person changes; no permission check (BR-117, TECH-086). */
    public Duration lateChangeThreshold() {
        String value = jdbc.queryForObject("select value from club_setting where key = ?", String.class, LATE_CHANGE);
        return Duration.ofHours(Long.parseLong(value));
    }

    @Transactional(readOnly = true)
    public int lateChangeThresholdHoursForManager() {
        access.require(Permission.CONFIGURE_LATE_CHANGE_THRESHOLD);
        return (int) lateChangeThreshold().toHours();
    }

    /** A positive whole number of hours, set only by the Club Manager and audited (BR-123, PERM-034). */
    @Transactional
    public int setLateChangeThresholdHours(BigDecimal hours) {
        AccountPrincipal actor = access.require(Permission.CONFIGURE_LATE_CHANGE_THRESHOLD);
        if (hours == null || hours.signum() <= 0 || hours.stripTrailingZeros().scale() > 0
                || hours.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw ApiException.invalid("INVALID_LATE_CHANGE_THRESHOLD", "hours",
                    "Ngưỡng đổi gấp phải là số giờ nguyên lớn hơn 0.");
        }
        int value = hours.intValueExact();
        int previous = (int) lateChangeThreshold().toHours();
        jdbc.update("update club_setting set value = ? where key = ?", String.valueOf(value), LATE_CHANGE);
        audit.record(actor, "SETTING_CHANGED", "SETTING", null,
                Map.of("key", LATE_CHANGE, "from", previous, "to", value));
        return value;
    }

    /** Used when a leave is recorded; no permission check (BR-167, TECH-145). */
    public int leaveNoticeDays() {
        String value = jdbc.queryForObject("select value from club_setting where key = ?", String.class, LEAVE_NOTICE);
        return Integer.parseInt(value);
    }

    @Transactional(readOnly = true)
    public int leaveNoticeDaysForManager() {
        access.require(Permission.CONFIGURE_THRESHOLDS);
        return leaveNoticeDays();
    }

    /** A positive whole number of days, set only by the Club Manager and audited; it applies to later leave only (PERM-054). */
    @Transactional
    public int setLeaveNoticeDays(BigDecimal days) {
        AccountPrincipal actor = access.require(Permission.CONFIGURE_THRESHOLDS);
        if (days == null || days.signum() <= 0 || days.stripTrailingZeros().scale() > 0
                || days.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw ApiException.invalid("INVALID_LEAVE_NOTICE", "days", "Số ngày báo trước phải là số nguyên lớn hơn 0.");
        }
        int value = days.intValueExact();
        int previous = leaveNoticeDays();
        jdbc.update("update club_setting set value = ? where key = ?", String.valueOf(value), LEAVE_NOTICE);
        audit.record(actor, "SETTING_CHANGED", "SETTING", null,
                Map.of("key", LEAVE_NOTICE, "from", previous, "to", value));
        return value;
    }

    /** Used by the forecast and the daily notice; no permission check (BR-175, TECH-161). */
    public int stockWarningDays() {
        String value = jdbc.queryForObject("select value from club_setting where key = ?", String.class, STOCK_WARNING);
        return Integer.parseInt(value);
    }

    @Transactional(readOnly = true)
    public int stockWarningDaysForManager() {
        access.require(Permission.CONFIGURE_THRESHOLDS);
        return stockWarningDays();
    }

    /** A whole number of days from 1 to 365, set only by the Club Manager and audited (PERM-054). */
    @Transactional
    public int setStockWarningDays(BigDecimal days) {
        AccountPrincipal actor = access.require(Permission.CONFIGURE_THRESHOLDS);
        if (days == null || days.stripTrailingZeros().scale() > 0 || days.compareTo(BigDecimal.ONE) < 0
                || days.compareTo(BigDecimal.valueOf(365)) > 0) {
            throw ApiException.invalid("INVALID_STOCK_WARNING", "days", "Số ngày cảnh báo phải là số nguyên từ 1 đến 365.");
        }
        int value = days.intValueExact();
        int previous = stockWarningDays();
        jdbc.update("update club_setting set value = ? where key = ?", String.valueOf(value), STOCK_WARNING);
        audit.record(actor, "SETTING_CHANGED", "SETTING", null,
                Map.of("key", STOCK_WARNING, "from", previous, "to", value));
        return value;
    }
}
