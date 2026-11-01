package vn.racehorse.veterinary.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.stable.entity.SupplyCategory;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stable.repository.SupplyItemRepository;
import vn.racehorse.stock.dto.StockLine;
import vn.racehorse.stock.entity.StockMovement;
import vn.racehorse.stock.exception.InsufficientStockException;
import vn.racehorse.stock.service.StockService;
import vn.racehorse.stock.service.SupplyUsed;
import vn.racehorse.veterinary.dto.CareCompletionInput;
import vn.racehorse.veterinary.dto.CareDueView;
import vn.racehorse.veterinary.dto.CareExtras;
import vn.racehorse.veterinary.dto.CareItemInput;
import vn.racehorse.veterinary.dto.CareLastCompletion;
import vn.racehorse.veterinary.entity.CareEvent;
import vn.racehorse.veterinary.entity.CareEventKind;
import vn.racehorse.veterinary.entity.CareItem;
import vn.racehorse.veterinary.entity.CareType;
import vn.racehorse.veterinary.repository.CareEventRepository;
import vn.racehorse.veterinary.repository.CareItemRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodic care items, completions, overrides, postponements and the due lists (REQ-016, BR-061 to BR-064). A
 * vaccination or deworming completion takes its medicine out of stock (BR-172); a care item may carry a planned
 * medicine and dose that the stock forecast counts (BR-175).
 */
@Service
public class CareService {

    static final int DUE_SOON_DAYS = 7;

    private final CareItemRepository items;
    private final CareEventRepository events;
    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final SupplyItemRepository supplies;
    private final StockService stock;

    public CareService(CareItemRepository items, CareEventRepository events, HorseRepository horses,
                       UserAccountRepository accounts, AccessGuard access, AuditService audit, ClubClock clock,
                       SupplyItemRepository supplies, StockService stock) {
        this.supplies = supplies;
        this.stock = stock;
        this.items = items;
        this.events = events;
        this.horses = horses;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    public enum DueState { OK, DUE_SOON, OVERDUE }

    public LocalDate today() {
        return clock.toClubTime(clock.now()).toLocalDate();
    }

    public DueState stateOf(CareItem item) {
        LocalDate today = today();
        if (item.getNextDue().isBefore(today)) {
            return DueState.OVERDUE;
        }
        return !item.getNextDue().isAfter(today.plusDays(DUE_SOON_DAYS)) ? DueState.DUE_SOON : DueState.OK;
    }

    @Transactional(readOnly = true)
    public List<CareDueView> forHorse(Long horseId) {
        access.require(Permission.MANAGE_CARE);
        Horse horse = horse(horseId);
        return items.findByHorseIdOrderByNextDueAscIdAsc(horseId).stream()
                .map(i -> new CareDueView(i, horse, stateOf(i))).toList();
    }

    /** Items due within 7 days or overdue, across the managed herd; only the Veterinarian sees them (BR-064). */
    @Transactional(readOnly = true)
    public List<CareDueView> dueList() {
        access.require(Permission.MANAGE_CARE);
        return items.findByNextDueLessThanEqualOrderByNextDueAscIdAsc(today().plusDays(DUE_SOON_DAYS)).stream()
                .map(i -> new CareDueView(i, horse(i.getHorseId()), stateOf(i)))
                .filter(v -> !v.horse().isArchived()).toList();
    }

    /** Latest completion and "has details" for each item of a page, from one query over the events (BR-187). */
    @Transactional(readOnly = true)
    public Map<Long, CareExtras> extras(Collection<Long> itemIds) {
        access.require(Permission.MANAGE_CARE);
        Map<Long, CareLastCompletion> last = new LinkedHashMap<>();
        Map<Long, Boolean> details = new LinkedHashMap<>();
        if (!itemIds.isEmpty()) {
            for (CareEvent e : events.findForItems(itemIds)) {
                boolean completion = e.getKind() == CareEventKind.COMPLETION;
                if (completion) {
                    last.putIfAbsent(e.getItemId(), new CareLastCompletion(e.getEventDate(), e.getProduct(), e.getPerformer()));
                }
                boolean more = !completion || notBlank(e.getNote()) || notBlank(e.getBatch());
                details.merge(e.getItemId(), more, Boolean::logicalOr);
            }
        }
        Map<Long, CareExtras> result = new LinkedHashMap<>();
        for (Long id : itemIds) {
            result.put(id, new CareExtras(last.get(id), details.getOrDefault(id, false)));
        }
        return result;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Transactional(readOnly = true)
    public List<CareEvent> history(Long itemId) {
        access.require(Permission.MANAGE_CARE);
        item(itemId);
        return events.findByItemIdOrderByAtAscIdAsc(itemId);
    }

    @Transactional
    public CareItem create(Long horseId, CareItemInput in) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CARE);
        Horse horse = horse(horseId);
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: không thể thêm lịch chăm sóc.");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (in.type() == null) errors.put("type", "Bắt buộc chọn.");
        if (in.name() == null || in.name().isBlank()) errors.put("name", "Bắt buộc nhập.");
        if (in.intervalDays() == null || in.intervalDays() <= 0) errors.put("intervalDays", "Chu kỳ phải lớn hơn 0 ngày.");
        if (in.firstDue() == null) errors.put("firstDue", "Bắt buộc nhập ngày đến hạn đầu tiên.");
        if ((in.plannedSupplyItemId() == null) != (in.plannedQuantity() == null)) {
            errors.put("plannedSupplyItemId", "Nhập cả thuốc dự kiến và liều dự kiến, hoặc để trống cả hai.");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thông tin lịch chăm sóc chưa hợp lệ.", errors);
        }
        Long plannedQuantity = in.plannedQuantity() == null ? null : plannedDose(in.plannedSupplyItemId(), in.plannedQuantity());
        CareItem item = items.save(new CareItem(horseId, in.type(), in.name().trim(), in.intervalDays(), in.firstDue(),
                accounts.findById(actor.id()).orElseThrow(), clock.now()));
        if (plannedQuantity != null) {
            item.setPlan(in.plannedSupplyItemId(), plannedQuantity);
        }
        audit.record(actor, "CARE_ITEM_CREATED", "CARE_ITEM", item.getId(),
                Map.of("horseId", horseId, "type", in.type().name(), "intervalDays", in.intervalDays()));
        return item;
    }

    /** Next due = date done + interval, so early or late work shifts the schedule (BR-062, DEC-009). */
    @Transactional
    public CareItem complete(Long itemId, CareCompletionInput in) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CARE);
        CareItem item = openItem(itemId);
        Map<String, String> errors = new LinkedHashMap<>();
        if (in.dateDone() == null) errors.put("dateDone", "Bắt buộc nhập.");
        else if (in.dateDone().isAfter(today())) errors.put("dateDone", "Ngày thực hiện không được ở tương lai.");
        if (in.performer() == null || in.performer().isBlank()) errors.put("performer", "Bắt buộc nhập người thực hiện.");
        boolean medicine = item.getType() != CareType.HOOF_CHECK;
        SupplyItem chosen = null;
        long dose = 0;
        if (medicine) {
            if (in.supplyItemId() == null) errors.put("supplyItemId", "Chọn thuốc từ kho.");
            if (in.quantity() == null) {
                errors.put("quantity", "Bắt buộc nhập số lượng đã dùng.");
            } else if (in.quantity().stripTrailingZeros().scale() > 0 || in.quantity().signum() <= 0
                    || in.quantity().compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) {
                errors.put("quantity", "Số lượng phải là số nguyên lớn hơn 0.");
            } else {
                dose = in.quantity().longValueExact();
            }
            if (in.supplyItemId() != null) {
                chosen = supplies.findById(in.supplyItemId()).orElse(null);
                if (chosen == null || !chosen.isActive() || chosen.getCategory() != SupplyCategory.MEDICINE) {
                    errors.put("supplyItemId", "Chỉ chọn được thuốc đang dùng trong kho.");
                }
            }
        } else if (in.supplyItemId() != null || in.quantity() != null) {
            errors.put("supplyItemId", "Kiểm tra móng không dùng thuốc từ kho.");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thông tin thực hiện chưa hợp lệ.", errors);
        }
        LocalDate previous = item.getNextDue();
        LocalDate next = in.dateDone().plusDays(item.getIntervalDays());
        item.setNextDue(next);
        CareEvent event = events.saveAndFlush(new CareEvent(itemId, CareEventKind.COMPLETION, in.dateDone(),
                in.performer().trim(), medicine ? chosen.getName() : blank(in.product()), blank(in.batch()),
                blank(in.note()), previous, next, accounts.findById(actor.id()).orElseThrow(), clock.now()));
        if (medicine) {
            try {
                StockMovement line = stock.use(item.getHorseId(), List.of(new StockLine(chosen.getId(), dose)),
                        SupplyUsed.Source.CARE, "CARE_EVENT", event.getId(), in.dateDone(), actor.id()).get(0);
                event.takeFromStock(chosen.getId(), dose, line.getUnitPrice());
            } catch (InsufficientStockException e) {
                InsufficientStockException.Shortage s = e.getShortages().get(0);
                String message = "Không đủ \"" + s.name() + "\" trong kho: còn " + s.available() + " " + s.unit() + ".";
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", message, Map.of("quantity", message));
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dateDone", in.dateDone().toString());
        details.put("nextDue", next.toString());
        if (medicine) {
            details.put("supplyItemId", chosen.getId());
            details.put("quantity", dose);
        }
        audit.record(actor, "CARE_COMPLETED", "CARE_ITEM", itemId, details);
        return item;
    }

    /** Sets the medicine and dose planned for the next completion; the forecast counts it (BR-175, TECH-156). */
    @Transactional
    public CareItem setPlan(Long itemId, Long supplyItemId, BigDecimal quantity) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CARE);
        CareItem item = openItem(itemId);
        if (supplyItemId == null || quantity == null) {
            throw ApiException.invalid("VALIDATION_FAILED", "supplyItemId", "Nhập cả thuốc dự kiến và liều dự kiến.");
        }
        long dose = plannedDose(supplyItemId, quantity);
        item.setPlan(supplyItemId, dose);
        audit.record(actor, "CARE_ITEM_PLAN_SET", "CARE_ITEM", itemId, Map.of("supplyItemId", supplyItemId, "quantity", dose));
        return item;
    }

    @Transactional
    public CareItem clearPlan(Long itemId) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CARE);
        CareItem item = openItem(itemId);
        item.setPlan(null, null);
        audit.record(actor, "CARE_ITEM_PLAN_SET", "CARE_ITEM", itemId, Map.of("cleared", true));
        return item;
    }

    /** The dose of an active medicine: a whole number above zero (TECH-156). */
    private long plannedDose(Long supplyItemId, BigDecimal quantity) {
        SupplyItem supply = supplyItemId == null ? null : supplies.findById(supplyItemId).orElse(null);
        if (supply == null || !supply.isActive() || supply.getCategory() != SupplyCategory.MEDICINE) {
            throw ApiException.invalid("VALIDATION_FAILED", "plannedSupplyItemId", "Chỉ chọn được thuốc đang dùng trong kho.");
        }
        if (quantity.stripTrailingZeros().scale() > 0 || quantity.signum() <= 0
                || quantity.compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0) {
            throw ApiException.invalid("VALIDATION_FAILED", "plannedQuantity", "Liều dự kiến phải là số nguyên lớn hơn 0.");
        }
        return quantity.longValueExact();
    }

    @Transactional
    public CareItem overrideDue(Long itemId, LocalDate newDue, String reason) {
        return changeDue(itemId, newDue, reason, CareEventKind.OVERRIDE, "CARE_DUE_OVERRIDDEN");
    }

    @Transactional
    public CareItem postpone(Long itemId, LocalDate newDue, String reason) {
        return changeDue(itemId, newDue, reason, CareEventKind.POSTPONEMENT, "CARE_POSTPONED");
    }

    private CareItem changeDue(Long itemId, LocalDate newDue, String reason, CareEventKind kind, String auditAction) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CARE);
        CareItem item = openItem(itemId);
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalid("REASON_REQUIRED", "reason", "Bắt buộc nhập lý do.");
        }
        if (newDue == null) {
            throw ApiException.invalid("DUE_DATE_REQUIRED", "newDue", "Bắt buộc nhập ngày đến hạn mới.");
        }
        LocalDate previous = item.getNextDue();
        item.setNextDue(newDue);
        events.save(new CareEvent(itemId, kind, today(), null, null, null, reason.trim(), previous, newDue,
                accounts.findById(actor.id()).orElseThrow(), clock.now()));
        audit.record(actor, auditAction, "CARE_ITEM", itemId,
                Map.of("from", previous.toString(), "to", newDue.toString(), "reason", reason.trim()));
        return item;
    }

    private CareItem openItem(Long itemId) {
        CareItem item = item(itemId);
        if (horse(item.getHorseId()).isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: hồ sơ chỉ xem.");
        }
        return item;
    }

    private CareItem item(Long itemId) {
        return items.findById(itemId).orElseThrow(() -> ApiException.notFound("mục chăm sóc"));
    }

    private Horse horse(Long horseId) {
        return horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
