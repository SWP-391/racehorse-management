package vn.racehorse.veterinary.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.billing.dto.ChargeSpec;
import vn.racehorse.billing.entity.Charge;
import vn.racehorse.billing.entity.ExamFeeType;
import vn.racehorse.billing.service.BillingPriceService;
import vn.racehorse.billing.service.ChargeService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.health.service.HealthService;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.region.entity.BodyRegion;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.veterinary.dto.ExaminationInput;
import vn.racehorse.veterinary.dto.ExaminationWeightPoint;
import vn.racehorse.veterinary.entity.Examination;
import vn.racehorse.veterinary.repository.ExaminationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Examination records, their restricted reading, and the Owner's weight view (REQ-012, REQ-013, REQ-024). */
@Service
public class ExaminationService {

    private final ExaminationRepository exams;
    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final HealthService health;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final BillingPriceService billingPrices;
    private final ChargeService charges;

    public ExaminationService(ExaminationRepository exams, HorseRepository horses, UserAccountRepository accounts,
                              HealthService health, AccessGuard access, AuditService audit, ClubClock clock,
                              BillingPriceService billingPrices, ChargeService charges) {
        this.exams = exams;
        this.horses = horses;
        this.accounts = accounts;
        this.health = health;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.billingPrices = billingPrices;
        this.charges = charges;
    }

    @Transactional
    public Examination record(Long horseId, ExaminationInput in) {
        AccountPrincipal actor = access.require(Permission.RECORD_EXAMINATIONS);
        Horse horse = horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: hồ sơ chỉ xem.");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (in.diagnosis() == null || in.diagnosis().isBlank()) errors.put("diagnosis", "Bắt buộc nhập chẩn đoán.");
        // The body area stays optional (BR-060) but only from the one catalog (BR-110).
        BodyRegion region = BodyRegion.parse(blank(in.bodyRegion()));
        if ((region == null && blank(in.bodyRegion()) != null) || blank(in.bodyArea()) != null) {
            errors.put("bodyRegion", "Vùng cơ thể phải chọn từ danh mục vùng cơ thể.");
        } else if (region == null && blank(in.bodyDetail()) != null) {
            errors.put("bodyRegion", "Chọn vùng cơ thể trước khi ghi chi tiết.");
        }
        if (in.weightKg() != null && in.weightKg().signum() <= 0) errors.put("weightKg", "Cân nặng phải lớn hơn 0.");
        LocalDate today = clock.toClubTime(clock.now()).toLocalDate();
        LocalDate date = in.examDate() == null ? today : in.examDate();
        if (date.isAfter(today)) errors.put("examDate", "Ngày khám không được ở tương lai.");
        // The fee comes from the price list (BR-178): required once a priced type exists, never shown to the vet.
        ExamFeeType feeType = null;
        java.math.BigDecimal fee = null;
        if (in.feeTypeId() != null) {
            feeType = billingPrices.feeType(in.feeTypeId()).filter(ExamFeeType::isActive).orElse(null);
            if (feeType == null) errors.put("feeTypeId", "Loại phí khám không hợp lệ.");
        } else if (billingPrices.anyOptionToday()) {
            errors.put("feeTypeId", "Chọn loại phí khám.");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thông tin khám chưa hợp lệ.", errors);
        }
        if (feeType != null) {
            fee = billingPrices.examOn(feeType.getId(), date).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                    "NO_PRICE_FOR_DATE", "Loại phí khám này chưa có giá tại ngày khám " + date + "."));
        }
        Examination exam = exams.save(new Examination(horseId, date, in.diagnosis().trim(), region,
                blank(in.bodyDetail()), in.weightKg(), blank(in.treatment()), blank(in.prescription()),
                accounts.findById(actor.id()).orElseThrow(), clock.now()));
        health.markExamined(horseId);
        if (feeType != null) {
            exam.chargeFeeType(feeType.getId());
            charges.record(new ChargeSpec(horseId, null, Charge.Category.EXAM, date,
                    "Khám: " + feeType.getName(), java.math.BigDecimal.ONE, fee, null, "EXAM:" + exam.getId(),
                    "EXAM:" + exam.getId(), null, actor.id()));
        }
        audit.record(actor, "EXAMINATION_RECORDED", "HORSE", horseId, Map.of("examinationId", exam.getId()));
        return exam;
    }

    /** Details for the Veterinarian and, read-only, the Head Trainer (PERM-017, BR-095). */
    @Transactional(readOnly = true)
    public List<Examination> history(Long horseId) {
        access.require(Permission.VIEW_EXAMINATIONS);
        horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        return exams.findByHorseIdOrderByExamDateDescIdDesc(horseId);
    }

    /** Weights only, for an Owner's own horses (BR-081); never activity status. */
    @Transactional(readOnly = true)
    public List<ExaminationWeightPoint> weights(Long horseId) {
        AccountPrincipal user = access.require(Permission.VIEW_READINESS);
        Horse horse = horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        if (user.role() == Role.HORSE_OWNER && !Objects.equals(horse.getOwner().getId(), user.id())) {
            throw ApiException.forbidden();
        }
        return exams.findByHorseIdAndWeightKgIsNotNullOrderByExamDateAscIdAsc(horseId).stream()
                .map(e -> new ExaminationWeightPoint(e.getExamDate(), e.getWeightKg())).toList();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
