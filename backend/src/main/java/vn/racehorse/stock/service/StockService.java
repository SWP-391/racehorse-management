package vn.racehorse.stock.service;

import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stable.repository.SupplyItemRepository;
import vn.racehorse.stock.dto.StockLine;
import vn.racehorse.stock.entity.ExpectedReceipt;
import vn.racehorse.stock.entity.StockMovement;
import vn.racehorse.stock.entity.SupplyPrice;
import vn.racehorse.stock.exception.InsufficientStockException;
import vn.racehorse.stock.repository.ExpectedReceiptRepository;
import vn.racehorse.stock.repository.StockMovementRepository;
import vn.racehorse.stock.repository.SupplyPriceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The stock ledger: the only writer of {@code supply_item.quantity} (BR-171, TECH-153). Every operation locks the
 * affected items in ascending id order, refuses a negative balance and writes immutable lines in the caller's
 * transaction. {@code use} and {@code reverse} carry no permission check because the care and chore services
 * decide who may act; the receipt, adjustment, price and expected-receipt operations are for the Club Manager.
 */
@Service
public class StockService {

    /** One quantity limit for every supply input, the same as the client's BOUNDS.quantity (BR-197). */
    public static final long MAX_QUANTITY = 1_000_000L;
    static final BigDecimal MAX_PRICE = new BigDecimal("1000000000");

    private final SupplyItemRepository items;
    private final StockMovementRepository movements;
    private final SupplyPriceRepository prices;
    private final ExpectedReceiptRepository expected;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final ApplicationEventPublisher events;
    private final EntityManager em;

    public StockService(SupplyItemRepository items, StockMovementRepository movements, SupplyPriceRepository prices,
                        ExpectedReceiptRepository expected, AccessGuard access, AuditService audit, ClubClock clock,
                        ApplicationEventPublisher events, EntityManager em) {
        this.em = em;
        this.items = items;
        this.movements = movements;
        this.prices = prices;
        this.expected = expected;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.events = events;
    }

    // ---- internal operations: used by the care and chore services, no permission check ----------------------

    /**
     * Takes the lines out of stock. All or nothing: a short item makes the whole use fail with
     * {@link InsufficientStockException} and nothing is written. Each line is priced at the item's price of this moment.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<StockMovement> use(Long horseId, List<StockLine> lines, SupplyUsed.Source source, String refType, Long refId,
                                   LocalDate occurredOn, Long actorId) {
        Map<Long, Long> wanted = new TreeMap<>();
        for (StockLine l : lines) {
            if (l.quantity() > 0) {
                wanted.merge(l.supplyItemId(), l.quantity(), Long::sum);
            }
        }
        if (wanted.isEmpty()) {
            return List.of();
        }
        Map<Long, SupplyItem> locked = lock(wanted.keySet());
        List<InsufficientStockException.Shortage> short_ = new ArrayList<>();
        wanted.forEach((id, qty) -> {
            SupplyItem item = locked.get(id);
            if (item.getQuantity() < qty) {
                short_.add(new InsufficientStockException.Shortage(id, item.getName(), item.getUnit(), qty,
                        item.getQuantity()));
            }
        });
        if (!short_.isEmpty()) {
            throw new InsufficientStockException(short_);
        }
        StockMovement.Kind kind = source == SupplyUsed.Source.CARE ? StockMovement.Kind.USE_CARE
                : StockMovement.Kind.USE_FEED;
        List<StockMovement> written = new ArrayList<>();
        wanted.forEach((id, qty) -> {
            SupplyItem item = locked.get(id);
            item.applyDelta(-qty);
            written.add(movements.save(new StockMovement(id, kind, -qty, item.getQuantity(), item.getUnitPrice(),
                    occurredOn, refType, refId, null, null, actorId, clock.now())));
            events.publishEvent(new SupplyUsed(horseId, source, id, qty, item.getUnitPrice(), occurredOn, refType, refId));
        });
        return written;
    }

    /**
     * Puts back what one care event or chore entry took, one reversal per original line at the original price
     * (BR-173). A reference with nothing left to reverse changes nothing.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void reverse(String refType, Long refId, Long actorId) {
        List<StockMovement> original = movements.findByRefTypeAndRefIdOrderByIdAsc(refType, refId).stream()
                .filter(m -> (m.getKind() == StockMovement.Kind.USE_CARE || m.getKind() == StockMovement.Kind.USE_FEED)
                        && !movements.existsByReversesId(m.getId())).toList();
        if (original.isEmpty()) {
            return;
        }
        Map<Long, SupplyItem> locked = lock(original.stream().map(StockMovement::getSupplyItemId).toList());
        for (StockMovement m : original) {
            SupplyItem item = locked.get(m.getSupplyItemId());
            item.applyDelta(-m.getDelta());
            movements.save(new StockMovement(item.getId(), StockMovement.Kind.REVERSAL, -m.getDelta(), item.getQuantity(),
                    m.getUnitPrice(), m.getOccurredOn(), refType, refId, m.getId(), null, actorId, clock.now()));
        }
        events.publishEvent(new SupplyUseReversed(refType, refId));
    }

    /** The first line of a new item: its opening quantity (BR-171). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void openingReceipt(SupplyItem item, long quantity, Long actorId) {
        if (quantity <= 0) {
            return;
        }
        Map<Long, SupplyItem> locked = lock(List.of(item.getId()));
        SupplyItem row = locked.get(item.getId());
        row.applyDelta(quantity);
        movements.save(new StockMovement(row.getId(), StockMovement.Kind.RECEIPT, quantity, row.getQuantity(),
                row.getUnitPrice(), clock.today(), null, null, null, "Tồn đầu kỳ", actorId, clock.now()));
    }

    /** The price row of a new item, written with the item itself. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void openingPrice(SupplyItem item, Long actorId) {
        prices.save(new SupplyPrice(item.getId(), item.getUnitPrice(), clock.today(), actorId, clock.now()));
    }

    // ---- Club Manager operations --------------------------------------------------------------------------

    /** A delivery: adds to the stock, priced at the item's current price, and closes the expected receipt it fulfils. */
    @Transactional
    public StockMovement receive(Long itemId, BigDecimal quantity, String note, Long expectedReceiptId) {
        AccountPrincipal actor = access.require(Permission.MANAGE_STOCK);
        long qty = wholeAtLeast(quantity, 1, "quantity", "Số lượng nhập phải là số nguyên lớn hơn 0.");
        SupplyItem item = lock(List.of(itemId)).get(itemId);
        if (item == null) {
            throw ApiException.notFound("vật tư");
        }
        if (item.getQuantity() + qty > MAX_QUANTITY) {
            throw ApiException.invalid("VALIDATION_FAILED", "quantity", "Số lượng tồn vượt giới hạn.");
        }
        if (expectedReceiptId != null) {
            ExpectedReceipt er = expected.findById(expectedReceiptId).orElseThrow(() -> ApiException.notFound("lô dự kiến"));
            if (!er.getSupplyItemId().equals(itemId) || er.getState() != ExpectedReceipt.State.EXPECTED) {
                throw ApiException.invalid("VALIDATION_FAILED", "expectedReceiptId", "Lô dự kiến không còn hiệu lực.");
            }
            er.close(ExpectedReceipt.State.RECEIVED, clock.now());
        }
        item.applyDelta(qty);
        StockMovement m = movements.save(new StockMovement(itemId, StockMovement.Kind.RECEIPT, qty, item.getQuantity(),
                item.getUnitPrice(), clock.today(), null, null, null, blankToNull(note), actor.id(), clock.now()));
        audit.record(actor, "STOCK_RECEIVED", "SUPPLY_ITEM", itemId, Map.of("quantity", qty, "balance", item.getQuantity()));
        return m;
    }

    /** Fixes the stock to a counted quantity or by a signed difference, with a mandatory reason (BR-171). */
    @Transactional
    public StockMovement adjust(Long itemId, BigDecimal newQuantity, BigDecimal delta, String reason) {
        AccountPrincipal actor = access.require(Permission.MANAGE_STOCK);
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalid("VALIDATION_FAILED", "reason", "Bắt buộc nhập lý do điều chỉnh.");
        }
        if ((newQuantity == null) == (delta == null)) {
            throw ApiException.invalid("VALIDATION_FAILED", "newQuantity", "Nhập số lượng mới hoặc mức chênh lệch.");
        }
        SupplyItem item = lock(List.of(itemId)).get(itemId);
        if (item == null) {
            throw ApiException.notFound("vật tư");
        }
        long change;
        if (newQuantity != null) {
            change = wholeAtLeast(newQuantity, 0, "newQuantity", "Số lượng tồn phải là số nguyên từ 0 trở lên.") - item.getQuantity();
        } else {
            change = whole(delta, "delta", "Mức chênh lệch phải là số nguyên.");
        }
        long result = item.getQuantity() + change;
        if (result < 0 || result > MAX_QUANTITY) {
            throw ApiException.invalid("NEGATIVE_STOCK", newQuantity != null ? "newQuantity" : "delta",
                    "Tồn kho không được âm hoặc vượt giới hạn.");
        }
        item.applyDelta(change);
        StockMovement m = movements.save(new StockMovement(itemId, StockMovement.Kind.ADJUSTMENT, change, result,
                item.getUnitPrice(), clock.today(), null, null, null, reason.trim(), actor.id(), clock.now()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("delta", change);
        details.put("balance", result);
        details.put("reason", reason.trim());
        audit.record(actor, "STOCK_ADJUSTED", "SUPPLY_ITEM", itemId, details);
        return m;
    }

    /** A new price for later uses; earlier lines keep the price they were made at (BR-171). */
    @Transactional
    public SupplyItem setPrice(Long itemId, BigDecimal price) {
        AccountPrincipal actor = access.require(Permission.MANAGE_STOCK);
        BigDecimal valid = validPrice(price);
        SupplyItem item = items.findById(itemId).orElseThrow(() -> ApiException.notFound("vật tư"));
        BigDecimal before = item.getUnitPrice();
        if (before.compareTo(valid) == 0) {
            return item;
        }
        item.setUnitPrice(valid);
        prices.save(new SupplyPrice(itemId, valid, clock.today(), actor.id(), clock.now()));
        audit.record(actor, "SUPPLY_PRICE_SET", "SUPPLY_ITEM", itemId,
                Map.of("from", before.toPlainString(), "to", valid.toPlainString()));
        return item;
    }

    @Transactional(readOnly = true)
    public List<StockMovement> movements(Long itemId) {
        access.require(Permission.MANAGE_STOCK);
        items.findById(itemId).orElseThrow(() -> ApiException.notFound("vật tư"));
        return movements.findBySupplyItemIdOrderByIdDesc(itemId);
    }

    @Transactional(readOnly = true)
    public List<ExpectedReceipt> expectedOf(Long itemId) {
        access.require(Permission.MANAGE_STOCK);
        return expected.findBySupplyItemIdAndStateOrderByExpectedDateAsc(itemId, ExpectedReceipt.State.EXPECTED);
    }

    @Transactional
    public ExpectedReceipt expect(Long itemId, LocalDate date, BigDecimal quantity) {
        AccountPrincipal actor = access.require(Permission.MANAGE_STOCK);
        items.findById(itemId).orElseThrow(() -> ApiException.notFound("vật tư"));
        if (date == null || date.isBefore(clock.today())) {
            throw ApiException.invalid("VALIDATION_FAILED", "expectedDate", "Ngày dự kiến nhập không được ở quá khứ.");
        }
        long qty = wholeAtLeast(quantity, 1, "quantity", "Số lượng dự kiến phải là số nguyên lớn hơn 0.");
        ExpectedReceipt er = expected.save(new ExpectedReceipt(itemId, date, qty, actor.id(), clock.now()));
        audit.record(actor, "EXPECTED_RECEIPT_ADDED", "SUPPLY_ITEM", itemId,
                Map.of("date", date.toString(), "quantity", qty));
        return er;
    }

    @Transactional
    public void cancelExpected(Long itemId, Long expectedId) {
        AccountPrincipal actor = access.require(Permission.MANAGE_STOCK);
        ExpectedReceipt er = expected.findById(expectedId).orElseThrow(() -> ApiException.notFound("lô dự kiến"));
        if (!er.getSupplyItemId().equals(itemId) || er.getState() != ExpectedReceipt.State.EXPECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "EXPECTED_CLOSED", "Lô dự kiến đã đóng.");
        }
        er.close(ExpectedReceipt.State.CANCELLED, clock.now());
        audit.record(actor, "EXPECTED_RECEIPT_CANCELLED", "SUPPLY_ITEM", itemId, Map.of("quantity", er.getQuantity()));
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private Map<Long, SupplyItem> lock(java.util.Collection<Long> ids) {
        Map<Long, SupplyItem> byId = new LinkedHashMap<>();
        for (SupplyItem i : items.lockAllById(ids)) {
            // The row may already be in this session (a ration read it): refresh so the balance is the one under the lock.
            em.refresh(i);
            byId.put(i.getId(), i);
        }
        return byId;
    }

    public static BigDecimal validPrice(BigDecimal price) {
        if (price == null || price.signum() < 0 || price.compareTo(MAX_PRICE) > 0
                || price.stripTrailingZeros().scale() > 0) {
            throw ApiException.invalid("VALIDATION_FAILED", "price", "Giá phải là số tiền đồng nguyên từ 0 trở lên.");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private static long wholeAtLeast(BigDecimal value, long min, String field, String message) {
        long v = whole(value, field, message);
        if (v < min || v > MAX_QUANTITY) {
            throw ApiException.invalid("VALIDATION_FAILED", field, message);
        }
        return v;
    }

    private static long whole(BigDecimal value, String field, String message) {
        if (value == null || value.stripTrailingZeros().scale() > 0 || value.abs().compareTo(BigDecimal.valueOf(MAX_QUANTITY)) > 0) {
            throw ApiException.invalid("VALIDATION_FAILED", field, message);
        }
        return value.longValueExact();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
