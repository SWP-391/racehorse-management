package vn.racehorse.billing.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.billing.dto.BillingPriceDailyFeeView;
import vn.racehorse.billing.dto.BillingPriceFeeTypeOption;
import vn.racehorse.billing.dto.BillingPriceFeeTypeView;
import vn.racehorse.billing.dto.BillingPriceList;
import vn.racehorse.billing.dto.BillingPriceRow;
import vn.racehorse.billing.entity.ExamFeeType;
import vn.racehorse.billing.entity.ServicePrice;
import vn.racehorse.billing.repository.ExamFeeTypeRepository;
import vn.racehorse.billing.repository.ServicePriceRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The service price list: the daily stable and care fee and one fee per kind of examination, each with an effective
 * date that is never in the past, so a price can never reach work already recorded (BR-178, TECH-164).
 */
@Service
public class BillingPriceService {

    private final ServicePriceRepository prices;
    private final ExamFeeTypeRepository types;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;

    public BillingPriceService(ServicePriceRepository prices, ExamFeeTypeRepository types, AccessGuard access,
                               AuditService audit, ClubClock clock) {
        this.prices = prices;
        this.types = types;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BillingPriceList list() {
        access.require(Permission.SET_PRICES_AND_CLOSE_MONTH);
        LocalDate today = clock.today();
        List<BillingPriceRow> daily = prices.dailyHistory().stream().map(BillingPriceService::row).toList();
        BigDecimal dailyNow = prices.dailyOn(today).map(ServicePrice::getAmount).orElse(null);
        List<BillingPriceFeeTypeView> views = types.findAllByOrderByNameAsc().stream().map(t -> new BillingPriceFeeTypeView(t.getId(),
                t.getName(), t.isActive(), prices.examOn(t.getId(), today).map(ServicePrice::getAmount).orElse(null),
                prices.examHistory(t.getId()).stream().map(BillingPriceService::row).toList())).toList();
        return new BillingPriceList(new BillingPriceDailyFeeView(dailyNow, daily), views);
    }

    @Transactional
    public void setPrice(ServicePrice.Kind kind, Long feeTypeId, BigDecimal amount, LocalDate effectiveFrom) {
        AccountPrincipal actor = access.require(Permission.SET_PRICES_AND_CLOSE_MONTH);
        Map<String, String> errors = new LinkedHashMap<>();
        if (kind == null) errors.put("kind", "Chọn loại giá.");
        if (amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 0) {
            errors.put("amount", "Giá phải là số đồng nguyên, không âm.");
        }
        if (effectiveFrom == null) errors.put("effectiveFrom", "Bắt buộc nhập ngày áp dụng.");
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thông tin giá chưa hợp lệ.", errors);
        }
        if (effectiveFrom.isBefore(clock.today())) {
            throw ApiException.invalid("PRICE_DATE_IN_PAST", "effectiveFrom",
                    "Giá mới chỉ áp dụng từ hôm nay trở đi, không áp ngược về quá khứ.");
        }
        Long typeId = null;
        if (kind == ServicePrice.Kind.EXAM_FEE) {
            ExamFeeType type = types.findById(feeTypeId == null ? -1L : feeTypeId)
                    .orElseThrow(() -> ApiException.invalid("INVALID_FEE_TYPE", "feeTypeId", "Chọn loại phí khám."));
            if (!type.isActive()) {
                throw ApiException.invalid("INVALID_FEE_TYPE", "feeTypeId", "Loại phí khám đã ngừng dùng.");
            }
            typeId = type.getId();
        }
        boolean taken = kind == ServicePrice.Kind.EXAM_FEE
                ? prices.existsByKindAndFeeTypeIdAndEffectiveFrom(kind, typeId, effectiveFrom)
                : prices.existsByKindAndEffectiveFrom(kind, effectiveFrom);
        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, "PRICE_DATE_TAKEN",
                    "Đã có một giá áp dụng từ ngày này; chọn ngày khác.");
        }
        prices.save(new ServicePrice(kind, typeId, amount.setScale(2), effectiveFrom, actor.id(), clock.now()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", kind.name());
        details.put("feeTypeId", typeId);
        details.put("amount", amount.toPlainString());
        details.put("effectiveFrom", effectiveFrom.toString());
        audit.record(actor, "BILLING_PRICE_SET", "SERVICE_PRICE", typeId, details);
    }

    /**
     * A new fee type together with its first price, in one step: a type without a price is refused and nothing is saved,
     * so no fee type exists that cannot be charged (BR-207, BR-178).
     */
    @Transactional
    public ExamFeeType createFeeType(String name, BigDecimal amount, LocalDate effectiveFrom) {
        access.require(Permission.SET_PRICES_AND_CLOSE_MONTH);
        Map<String, String> errors = new LinkedHashMap<>();
        if (amount == null) errors.put("amount", "Bắt buộc nhập giá.");
        if (effectiveFrom == null) errors.put("effectiveFrom", "Bắt buộc nhập ngày áp dụng.");
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Loại phí khám cần có giá.", errors);
        }
        ExamFeeType type = createFeeType(name);
        setPrice(ServicePrice.Kind.EXAM_FEE, type.getId(), amount, effectiveFrom);
        return type;
    }

    @Transactional
    public ExamFeeType createFeeType(String name) {
        AccountPrincipal actor = access.require(Permission.SET_PRICES_AND_CLOSE_MONTH);
        String clean = cleanName(name);
        if (types.existsByNameIgnoreCase(clean)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEE_TYPE", "Đã có loại phí khám tên này.");
        }
        ExamFeeType type = types.save(new ExamFeeType(clean, clock.now()));
        audit.record(actor, "FEE_TYPE_CREATED", "EXAM_FEE_TYPE", type.getId(), Map.of("name", clean));
        return type;
    }

    @Transactional
    public ExamFeeType renameFeeType(Long id, String name) {
        AccountPrincipal actor = access.require(Permission.SET_PRICES_AND_CLOSE_MONTH);
        ExamFeeType type = types.findById(id).orElseThrow(() -> ApiException.notFound("loại phí khám"));
        String clean = cleanName(name);
        if (!type.getName().equalsIgnoreCase(clean) && types.existsByNameIgnoreCase(clean)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEE_TYPE", "Đã có loại phí khám tên này.");
        }
        type.rename(clean);
        audit.record(actor, "FEE_TYPE_UPDATED", "EXAM_FEE_TYPE", id, Map.of("name", clean));
        return type;
    }

    /** A fee type is deactivated, never deleted, so earlier examinations keep their name. */
    @Transactional
    public ExamFeeType deactivateFeeType(Long id) {
        AccountPrincipal actor = access.require(Permission.SET_PRICES_AND_CLOSE_MONTH);
        ExamFeeType type = types.findById(id).orElseThrow(() -> ApiException.notFound("loại phí khám"));
        if (type.isActive()) {
            type.deactivate();
            audit.record(actor, "FEE_TYPE_UPDATED", "EXAM_FEE_TYPE", id, Map.of("active", false));
        }
        return type;
    }

    /** The fee types with a price in force today, for the examination form (no amounts, PERM-016). */
    @Transactional(readOnly = true)
    public List<BillingPriceFeeTypeOption> optionsToday() {
        access.require(Permission.RECORD_EXAMINATIONS);
        LocalDate today = clock.today();
        return types.findAllByOrderByNameAsc().stream()
                .filter(t -> t.isActive() && prices.examOn(t.getId(), today).isPresent())
                .map(t -> new BillingPriceFeeTypeOption(t.getId(), t.getName())).toList();
    }

    public boolean anyOptionToday() {
        LocalDate today = clock.today();
        return types.findAllByOrderByNameAsc().stream()
                .anyMatch(t -> t.isActive() && prices.examOn(t.getId(), today).isPresent());
    }

    public Optional<ExamFeeType> feeType(Long id) {
        return types.findById(id);
    }

    public Optional<BigDecimal> dailyOn(LocalDate day) {
        return prices.dailyOn(day).map(ServicePrice::getAmount);
    }

    public Optional<BigDecimal> examOn(Long feeTypeId, LocalDate day) {
        return prices.examOn(feeTypeId, day).map(ServicePrice::getAmount);
    }

    private static BillingPriceRow row(ServicePrice p) {
        return new BillingPriceRow(p.getId(), p.getAmount(), p.getEffectiveFrom());
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.invalid("VALIDATION_FAILED", "name", "Bắt buộc nhập tên loại phí khám.");
        }
        if (name.trim().length() > 200) {
            throw ApiException.invalid("VALIDATION_FAILED", "name", "Tên tối đa 200 ký tự.");
        }
        return name.trim();
    }
}
