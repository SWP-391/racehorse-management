package vn.racehorse.audit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.audit.dto.AuditEntryDto;
import vn.racehorse.audit.entity.AuditEntry;
import vn.racehorse.audit.repository.AuditEntryRepository;
import vn.racehorse.audit.service.AuditActionLabels;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.config.ClubProperties;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Search;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.Permission;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Read-only audit log for the Club Manager, filtered by club-local date range and user (BR-093). */
@Tag(name = "Nhật ký", description = "Nhật ký thao tác chỉ đọc cho Quản lý CLB")
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AuditEntryRepository entries;
    private final AccessGuard access;
    private final ClubProperties club;
    private final ClubClock clock;

    public AuditController(AuditEntryRepository entries, AccessGuard access, ClubProperties club, ClubClock clock) {
        this.entries = entries;
        this.access = access;
        this.club = club;
        this.clock = clock;
    }

    @Operation(summary = "Tìm trong nhật ký theo ngày và người dùng")
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryDto> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q) {
        access.require(Permission.VIEW_AUDIT_LOG);
        LocalDate today = clock.toClubTime(clock.now()).toLocalDate();
        LocalDate start = from != null ? from : today.minusDays(30);
        LocalDate end = to != null ? to : today;
        if (end.isBefore(start)) {
            throw ApiException.invalid("INVALID_RANGE", "to", "Ngày kết thúc phải sau ngày bắt đầu.");
        }
        Instant fromInstant = start.atStartOfDay(club.zone()).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(club.zone()).toInstant();
        PageParams params = PageParams.of(page, size, q);
        String action1 = action == null || action.isBlank() ? "" : action;
        boolean all = params.q() == null;
        List<String> names = all ? List.of(NONE) : matching(entries.actorNames(), params.q(), false);
        List<String> actions = all ? List.of(NONE) : matching(entries.actions(), params.q(), true);
        List<String> targets = all ? List.of(NONE) : targetsMatching(params.q());
        Page<AuditEntry> rows = entries.page(fromInstant, toInstant, actorId, action1, all, names, actions, targets,
                PageRequest.of(params.page(), params.size()));
        return new PageResponse<>(rows.map(e -> new AuditEntryDto(e.getId(), e.getActorId(), e.getActorName(),
                e.getOccurredAt(), DISPLAY.format(clock.toClubTime(e.getOccurredAt())), e.getAction(), e.getTargetType(),
                e.getTargetId(), e.getDetails())).getContent(), params.page(), params.size(), rows.getTotalElements());
    }

    /** An empty `in` list is not portable, so a value no row can hold stands in for "nothing matched". */
    private static final String NONE = "\u0001";

    private static List<String> matching(List<String> values, String q, boolean byLabel) {
        List<String> found = values.stream()
                .filter(v -> Search.matches(q, v, byLabel ? AuditActionLabels.of(v) : null)).toList();
        return found.isEmpty() ? List.of(NONE) : found;
    }

    private List<String> targetsMatching(String q) {
        List<String> found = entries.targetTypes().stream()
                .filter(t -> Search.matches(q, t, AuditActionLabels.target(t))).toList();
        return found.isEmpty() ? List.of(NONE) : found;
    }

    /** Audit entries can never be changed or removed (BR-092, AC-026). */
    @Operation(summary = "Không cho sửa mục nhật ký (luôn bị từ chối)")
    @PutMapping("/{id}")
    public void update(@PathVariable Long id) {
        throw immutable();
    }

    @Operation(summary = "Không cho sửa mục nhật ký (luôn bị từ chối)")
    @PatchMapping("/{id}")
    public void patch(@PathVariable Long id) {
        throw immutable();
    }

    @Operation(summary = "Không cho xóa mục nhật ký (luôn bị từ chối)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        throw immutable();
    }

    private ApiException immutable() {
        access.require(Permission.VIEW_AUDIT_LOG);
        return new ApiException(HttpStatus.METHOD_NOT_ALLOWED, "AUDIT_IMMUTABLE",
                "Nhật ký thao tác không thể sửa hoặc xóa.");
    }
}
