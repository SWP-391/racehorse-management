package vn.racehorse.incident.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.incident.dto.IncidentDetail;
import vn.racehorse.incident.dto.IncidentHorseOption;
import vn.racehorse.incident.entity.IncidentPhoto;
import vn.racehorse.incident.entity.IncidentReport;
import vn.racehorse.incident.entity.IncidentState;
import vn.racehorse.incident.repository.IncidentPhotoRepository;
import vn.racehorse.incident.repository.IncidentReportRepository;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.region.entity.BodyRegion;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.veterinary.entity.Examination;
import vn.racehorse.veterinary.repository.ExaminationRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The Groom's incident reports and the Veterinarian's handling of them (REQ-020, BR-070, BR-071). */
@Service
public class IncidentService {

    static final int MAX_PHOTOS = 3;
    static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;

    private final IncidentReportRepository reports;
    private final IncidentPhotoRepository photos;
    private final HorseRepository horses;
    private final ExaminationRepository exams;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClubClock clock;

    public IncidentService(IncidentReportRepository reports, IncidentPhotoRepository photos, HorseRepository horses,
                           ExaminationRepository exams, UserAccountRepository accounts, AccessGuard access,
                           AuditService audit, NotificationService notifications, ClubClock clock) {
        this.reports = reports;
        this.photos = photos;
        this.horses = horses;
        this.exams = exams;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Name and registration number only, to choose the horse for a report (PERM-002, AC-106). */
    @Transactional(readOnly = true)
    public List<IncidentHorseOption> horseOptions() {
        access.require(Permission.VIEW_HORSE_OPTIONS);
        return horses.findAllByOrderByNameAsc().stream().filter(h -> !h.isArchived())
                .map(h -> new IncidentHorseOption(h.getId(), h.getName(), h.getRegistrationNumber())).toList();
    }

    @Transactional
    public IncidentDetail send(Long horseId, String description, String bodyRegion, List<MultipartFile> files) {
        AccountPrincipal actor = access.require(Permission.SEND_INCIDENTS);
        Map<String, String> errors = new LinkedHashMap<>();
        Horse horse = horseId == null ? null : horses.findById(horseId).orElse(null);
        if (horse == null || horse.isArchived()) {
            errors.put("horseId", "Hãy chọn một con ngựa trong danh sách.");
        }
        if (description == null || description.isBlank()) {
            errors.put("description", "Bắt buộc nhập mô tả.");
        }
        // Optional, but only from the one catalog (BR-111, BR-110).
        BodyRegion region = BodyRegion.parse(bodyRegion == null || bodyRegion.isBlank() ? null : bodyRegion);
        if (region == null && bodyRegion != null && !bodyRegion.isBlank()) {
            errors.put("bodyRegion", "Chọn một vùng cơ thể có trong danh mục.");
        }
        List<MultipartFile> given = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (given.size() > MAX_PHOTOS) {
            errors.put("photos", "Tối đa " + MAX_PHOTOS + " ảnh cho mỗi báo cáo. Ảnh thứ " + (MAX_PHOTOS + 1) + " bị từ chối.");
        }
        List<IncidentPhoto> accepted = new ArrayList<>();
        for (int i = 0; i < Math.min(given.size(), MAX_PHOTOS); i++) {
            MultipartFile f = given.get(i);
            if (f.getSize() > MAX_PHOTO_BYTES) {
                errors.put("photos[" + i + "]", "Ảnh \"" + f.getOriginalFilename() + "\" vượt quá 10 MB.");
                continue;
            }
            byte[] bytes;
            try {
                bytes = f.getBytes();
            } catch (IOException e) {
                errors.put("photos[" + i + "]", "Không đọc được ảnh \"" + f.getOriginalFilename() + "\".");
                continue;
            }
            String type = sniff(bytes);
            if (type == null) {
                errors.put("photos[" + i + "]", "Tệp \"" + f.getOriginalFilename() + "\" không phải ảnh JPG hoặc PNG.");
                continue;
            }
            accepted.add(new IncidentPhoto(null, safeName(f.getOriginalFilename()), type, bytes));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Báo cáo sự cố chưa hợp lệ.", errors);
        }
        UserAccount groom = accounts.findById(actor.id()).orElseThrow();
        IncidentReport report = reports.save(new IncidentReport(horseId, groom, description.trim(), region,
                clock.now()));
        for (IncidentPhoto p : accepted) {
            photos.save(new IncidentPhoto(report.getId(), p.getFileName(), p.getContentType(), p.getContent()));
        }
        audit.record(actor, "INCIDENT_SENT", "INCIDENT", report.getId(),
                Map.of("horseId", horseId, "photos", accepted.size()));
        notifications.notifyRole(Role.VETERINARIAN,
                "Báo cáo sự cố mới về ngựa \"" + horse.getName() + "\": " + shorten(description.trim()),
                "/incidents/" + report.getId());
        return new IncidentDetail(report, horse, photos.idsOfReport(report.getId()));
    }

    /** The Groom's own reports, or every report for Veterinarians. */
    @Transactional(readOnly = true)
    public List<IncidentDetail> list() {
        AccountPrincipal user = access.current();
        List<IncidentReport> rows;
        if (Permission.HANDLE_INCIDENTS.allows(user.role())) {
            rows = reports.findAllByOrderByCreatedAtDescIdDesc();
        } else if (Permission.SEND_INCIDENTS.allows(user.role())) {
            rows = reports.findByGroomIdOrderByCreatedAtDescIdDesc(user.id());
        } else {
            throw ApiException.forbidden();
        }
        return rows.stream().map(r -> new IncidentDetail(r, horses.findById(r.getHorseId()).orElseThrow(),
                photos.idsOfReport(r.getId()))).toList();
    }

    /** Opening by a Veterinarian acknowledges a new report (TECH-036). */
    @Transactional
    public IncidentDetail open(Long id) {
        AccountPrincipal user = access.current();
        IncidentReport r = reports.findById(id).orElseThrow(() -> ApiException.notFound("báo cáo sự cố"));
        if (Permission.HANDLE_INCIDENTS.allows(user.role())) {
            if (r.getState() == IncidentState.NEW) {
                r.acknowledge(accounts.findById(user.id()).orElseThrow(), clock.now());
                audit.record(user, "INCIDENT_ACKNOWLEDGED", "INCIDENT", id, Map.of("horseId", r.getHorseId()));
            }
        } else if (!(Permission.SEND_INCIDENTS.allows(user.role()) && Objects.equals(r.getGroom().getId(), user.id()))) {
            throw ApiException.forbidden();
        }
        return new IncidentDetail(r, horses.findById(r.getHorseId()).orElseThrow(), photos.idsOfReport(id));
    }

    @Transactional
    public IncidentDetail close(Long id, String note, Long examinationId) {
        AccountPrincipal actor = access.require(Permission.HANDLE_INCIDENTS);
        IncidentReport r = reports.findById(id).orElseThrow(() -> ApiException.notFound("báo cáo sự cố"));
        if (r.getState() == IncidentState.HANDLED) {
            throw new ApiException(HttpStatus.CONFLICT, "INCIDENT_STATE", "Báo cáo đã được xử lý.");
        }
        if (note == null || note.isBlank()) {
            throw ApiException.invalid("NOTE_REQUIRED", "note", "Bắt buộc nhập ghi chú xử lý.");
        }
        if (examinationId != null) {
            Examination e = exams.findById(examinationId).orElse(null);
            if (e == null || !Objects.equals(e.getHorseId(), r.getHorseId())) {
                throw ApiException.invalid("INVALID_EXAMINATION", "examinationId",
                        "Hồ sơ khám phải thuộc cùng con ngựa của báo cáo.");
            }
        }
        UserAccount vet = accounts.findById(actor.id()).orElseThrow();
        if (r.getState() == IncidentState.NEW) {
            r.acknowledge(vet, clock.now());
        }
        r.close(vet, note.trim(), examinationId, clock.now());
        audit.record(actor, "INCIDENT_HANDLED", "INCIDENT", id,
                Map.of("horseId", r.getHorseId(), "examinationId", examinationId == null ? "" : examinationId));
        return new IncidentDetail(r, horses.findById(r.getHorseId()).orElseThrow(), photos.idsOfReport(id));
    }

    @Transactional(readOnly = true)
    public IncidentPhoto photo(Long reportId, Long photoId) {
        AccountPrincipal user = access.current();
        IncidentReport r = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("báo cáo sự cố"));
        boolean own = Permission.SEND_INCIDENTS.allows(user.role()) && Objects.equals(r.getGroom().getId(), user.id());
        if (!own && !Permission.HANDLE_INCIDENTS.allows(user.role())) {
            throw ApiException.forbidden();
        }
        IncidentPhoto p = photos.findById(photoId).orElseThrow(() -> ApiException.notFound("ảnh"));
        if (!Objects.equals(p.getReportId(), reportId)) {
            throw ApiException.notFound("ảnh");
        }
        return p;
    }

    /** Accepts only real JPEG or PNG content, whatever the file is called (TECH-035). */
    static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (b.length >= png.length) {
            for (int i = 0; i < png.length; i++) {
                if (b[i] != png[i]) {
                    return null;
                }
            }
            return "image/png";
        }
        return null;
    }

    private static String safeName(String name) {
        String n = name == null ? "anh" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return n.length() > 100 ? n.substring(n.length() - 100) : n;
    }

    private static String shorten(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
