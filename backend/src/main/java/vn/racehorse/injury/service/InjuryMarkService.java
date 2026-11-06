package vn.racehorse.injury.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.injury.dto.InjuryMarkView;
import vn.racehorse.injury.entity.InjuryMark;
import vn.racehorse.injury.entity.InjuryMarkEvent;
import vn.racehorse.injury.entity.MarkStatus;
import vn.racehorse.injury.repository.InjuryMarkEventRepository;
import vn.racehorse.injury.repository.InjuryMarkRepository;
import vn.racehorse.region.entity.BodyRegion;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.veterinary.entity.Examination;
import vn.racehorse.veterinary.repository.ExaminationRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Injury marks on the shared 3D model and their recovery history (REQ-014). Marks never touch the health status,
 * the lock or the training rules (BR-101): this service does not depend on the health or lock code.
 */
@Service
public class InjuryMarkService {

    private final InjuryMarkRepository marks;
    private final InjuryMarkEventRepository events;
    private final HorseRepository horses;
    private final ExaminationRepository exams;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;

    public InjuryMarkService(InjuryMarkRepository marks, InjuryMarkEventRepository events, HorseRepository horses,
                             ExaminationRepository exams, UserAccountRepository accounts, AccessGuard access,
                             AuditService audit, ClubClock clock) {
        this.marks = marks;
        this.events = events;
        this.horses = horses;
        this.exams = exams;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public InjuryMarkView create(Long horseId, String regionCode, Long examinationId) {
        AccountPrincipal actor = access.require(Permission.MANAGE_INJURY_MARKS);
        Horse horse = horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: hồ sơ chỉ xem.");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        BodyRegion region = BodyRegion.parse(regionCode);
        if (region == null) errors.put("regionCode", "Chọn một vùng cơ thể có trong danh mục.");
        Examination exam = examinationId == null ? null : exams.findById(examinationId).orElse(null);
        if (examinationId == null || exam == null || !exam.getHorseId().equals(horseId)) {
            errors.put("examinationId", "Chọn hồ sơ khám của ngựa này.");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dấu chấn thương chưa hợp lệ.", errors);
        }
        var by = accounts.findById(actor.id()).orElseThrow();
        InjuryMark mark = marks.save(new InjuryMark(horseId, examinationId, region, by, clock.now()));
        InjuryMarkEvent created = events.save(
                new InjuryMarkEvent(mark.getId(), null, MarkStatus.NEW, today(), null, by, clock.now()));
        audit.record(actor, "INJURY_MARK_CREATED", "HORSE", horseId,
                Map.of("markId", mark.getId(), "region", region.name(), "regionName", region.vietnamese(),
                        "examinationId", examinationId));
        return new InjuryMarkView(mark, exam, List.of(created));
    }

    @Transactional
    public InjuryMarkView changeStatus(Long markId, MarkStatus next, String note) {
        AccountPrincipal actor = access.require(Permission.MANAGE_INJURY_MARKS);
        InjuryMark mark = marks.findById(markId).orElseThrow(() -> ApiException.notFound("dấu chấn thương"));
        Horse horse = horses.findById(mark.getHorseId()).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: hồ sơ chỉ xem.");
        }
        if (next == null) {
            throw ApiException.invalid("VALIDATION_FAILED", "status", "Chọn trạng thái hồi phục.");
        }
        if (next == MarkStatus.CANCELLED) {
            // Cancelling needs a reason (BR-112): only through cancel().
            throw ApiException.invalid("VALIDATION_FAILED", "status", "Dùng thao tác hủy dấu nhập nhầm, kèm lý do.");
        }
        return move(actor, mark, next, blank(note), "INJURY_MARK_STATUS_CHANGED");
    }

    /** A mark created by mistake is cancelled with a reason while still Mới, never deleted (BR-112, BR-103). */
    @Transactional
    public InjuryMarkView cancel(Long markId, String reason) {
        AccountPrincipal actor = access.require(Permission.MANAGE_INJURY_MARKS);
        InjuryMark mark = marks.findById(markId).orElseThrow(() -> ApiException.notFound("dấu chấn thương"));
        Horse horse = horses.findById(mark.getHorseId()).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: hồ sơ chỉ xem.");
        }
        if (mark.getStatus() != MarkStatus.NEW) {
            String message = "Chỉ hủy được dấu còn ở trạng thái Mới.";
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_MARK_TRANSITION", message, Map.of("status", message));
        }
        if (blank(reason) == null) {
            throw ApiException.invalid("VALIDATION_FAILED", "reason", "Bắt buộc nhập lý do hủy.");
        }
        return move(actor, mark, MarkStatus.CANCELLED, blank(reason), "INJURY_MARK_CANCELLED");
    }

    /** Every change is dated by the club clock, never by the caller (BR-113). */
    private InjuryMarkView move(AccountPrincipal actor, InjuryMark mark, MarkStatus next, String note, String auditAction) {
        MarkStatus from = mark.getStatus();
        if (!from.canMoveTo(next)) {
            String message = switch (from) {
                case RECOVERED -> "Dấu đã hồi phục không mở lại được. Nếu là chấn thương mới, hãy tạo dấu mới.";
                case CANCELLED -> "Dấu đã hủy (nhập nhầm) không đổi trạng thái được nữa.";
                default -> "Không thể chuyển trạng thái này.";
            };
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_MARK_TRANSITION", message,
                    Map.of("status", message));
        }
        var by = accounts.findById(actor.id()).orElseThrow();
        mark.moveTo(next);
        events.save(new InjuryMarkEvent(mark.getId(), from, next, today(), note, by, clock.now()));
        audit.record(actor, auditAction, "HORSE", mark.getHorseId(),
                Map.of("markId", mark.getId(), "from", from.name(), "to", next.name()));
        return view(mark);
    }

    @Transactional(readOnly = true)
    public List<InjuryMarkView> list(Long horseId) {
        access.require(Permission.VIEW_INJURY_MARKS);
        horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        List<InjuryMark> found = marks.findByHorseIdOrderByCreatedAtDescIdDesc(horseId);
        Map<Long, List<InjuryMarkEvent>> byMark = found.isEmpty() ? Map.of()
                : events.findByMarkIdInOrderByIdAsc(found.stream().map(InjuryMark::getId).toList()).stream()
                        .collect(Collectors.groupingBy(InjuryMarkEvent::getMarkId));
        Map<Long, Examination> examById = exams.findByHorseIdOrderByExamDateDescIdDesc(horseId).stream()
                .collect(Collectors.toMap(Examination::getId, Function.identity()));
        return found.stream()
                .map(m -> new InjuryMarkView(m, examById.get(m.getExaminationId()), byMark.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    /** Marks are never deleted (BR-103): a manager gets a refusal, anyone else access denied. */
    @Transactional(readOnly = true)
    public void refuseDelete(Long markId) {
        access.require(Permission.MANAGE_INJURY_MARKS);
        marks.findById(markId).orElseThrow(() -> ApiException.notFound("dấu chấn thương"));
        throw new ApiException(HttpStatus.CONFLICT, "MARK_NOT_DELETABLE",
                "Dấu chấn thương và lịch sử được giữ lại, không xóa được.");
    }

    private InjuryMarkView view(InjuryMark mark) {
        Examination exam = exams.findById(mark.getExaminationId()).orElse(null);
        return new InjuryMarkView(mark, exam, events.findByMarkIdInOrderByIdAsc(List.of(mark.getId())));
    }

    private LocalDate today() {
        return clock.toClubTime(clock.now()).toLocalDate();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
