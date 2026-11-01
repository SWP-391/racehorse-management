package vn.racehorse.stable.service;

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
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.stable.dto.SupplyItemInput;
import vn.racehorse.stable.entity.ProposalState;
import vn.racehorse.stable.entity.RestockProposal;
import vn.racehorse.stable.entity.SupplyCategory;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stable.entity.SupplyUnit;
import vn.racehorse.stable.repository.RestockProposalRepository;
import vn.racehorse.stable.repository.SupplyItemRepository;
import vn.racehorse.stock.service.StockService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Supply catalog, restock proposals and their decisions (BR-130 to BR-132). Approving never changes the stock. The
 * quantity on hand is the balance of the stock ledger and changes only through it (BR-171): an item is added with a
 * price and an optional opening quantity, never edited to a new quantity.
 */
@Service
public class SupplyService {

    private static final int MAX_NAME = 200;
    private static final int MAX_TEXT = 1000;

    private final SupplyItemRepository items;
    private final RestockProposalRepository proposals;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClubClock clock;
    private final StockService stock;
    private final SupplyUnitService units;

    public SupplyService(SupplyItemRepository items, RestockProposalRepository proposals,
                         UserAccountRepository accounts, AccessGuard access, AuditService audit,
                         NotificationService notifications, ClubClock clock, StockService stock,
                         SupplyUnitService units) {
        this.stock = stock;
        this.units = units;
        this.items = items;
        this.proposals = proposals;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** The catalog; a Groom sees active items only (PERM-040). */
    @Transactional(readOnly = true)
    public List<SupplyItem> list() {
        AccountPrincipal user = access.require(Permission.VIEW_SUPPLIES);
        return Permission.MANAGE_SUPPLIES.allows(user.role()) ? items.findAllByOrderByNameAsc()
                : items.findByActiveTrueOrderByNameAsc();
    }

    @Transactional
    public SupplyItem create(SupplyItemInput in) {
        AccountPrincipal actor = access.require(Permission.MANAGE_SUPPLIES);
        Valid v = validate(in, null);
        Map<String, String> errors = new LinkedHashMap<>();
        if (in.price() == null) {
            errors.put("price", "Bắt buộc nhập giá (từ 0 trở lên).");
        }
        BigDecimal price = in.price() == null ? null : priceOrError(in.price(), errors);
        Long opening = in.openingQuantity() == null ? Long.valueOf(0)
                : wholeNumber(in.openingQuantity(), 0, errors, "openingQuantity",
                "Số lượng tồn đầu phải là số nguyên từ 0 trở lên.");
        failIfAny(errors, "Vật tư chưa hợp lệ.");
        requireUnique(v.name, v.category, null);
        SupplyItem item;
        try {
            item = items.saveAndFlush(new SupplyItem(v.name, v.category, v.unit, price, clock.now()));
        } catch (DataIntegrityViolationException e) {
            // A concurrent create won the unique index (BR-130).
            throw duplicate(v.name);
        }
        stock.openingPrice(item, actor.id());
        stock.openingReceipt(item, opening, actor.id());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", item.getName());
        details.put("category", item.getCategory().name());
        details.put("unit", item.getUnit());
        details.put("price", price.toPlainString());
        details.put("quantity", item.getQuantity());
        audit.record(actor, "SUPPLY_CREATED", "SUPPLY_ITEM", item.getId(), details);
        return item;
    }

    /**
     * Edits name, category, unit and active; an item is deactivated, never deleted (BR-130). A quantity in the request
     * is refused, not ignored: the quantity changes only by receipt or adjustment (BR-171).
     */
    @Transactional
    public SupplyItem update(Long id, SupplyItemInput in) {
        AccountPrincipal actor = access.require(Permission.MANAGE_SUPPLIES);
        SupplyItem item = items.findById(id).orElseThrow(() -> ApiException.notFound("vật tư"));
        if (in.quantity() != null) {
            throw ApiException.invalid("QUANTITY_BY_MOVEMENT", "quantity",
                    "Số lượng tồn chỉ thay đổi bằng phiếu nhập hoặc điều chỉnh kho có lý do.");
        }
        Valid v = validate(in, item.getUnitId());
        requireUnique(v.name, v.category, item.getId());
        boolean active = in.active() == null ? item.isActive() : in.active();

        Map<String, Object> changes = new LinkedHashMap<>();
        diff(changes, "name", item.getName(), v.name);
        diff(changes, "category", item.getCategory(), v.category);
        diff(changes, "unit", item.getUnit(), v.unit.getName());
        diff(changes, "active", item.isActive(), active);
        item.update(v.name, v.category, v.unit, active);
        try {
            items.flush();
        } catch (DataIntegrityViolationException e) {
            throw duplicate(v.name);
        }
        if (!changes.isEmpty()) {
            audit.record(actor, "SUPPLY_UPDATED", "SUPPLY_ITEM", item.getId(), changes);
        }
        return item;
    }

    @Transactional
    public RestockProposal propose(Long itemId, BigDecimal quantity, String note) {
        AccountPrincipal actor = access.require(Permission.PROPOSE_RESTOCK);
        SupplyItem item = items.findById(itemId).orElseThrow(() -> ApiException.notFound("vật tư"));
        Map<String, String> errors = new LinkedHashMap<>();
        Long wanted = wholeNumber(quantity, 1, errors, "quantity", "Số lượng đề xuất phải là số nguyên lớn hơn 0.");
        if (note != null && note.trim().length() > MAX_TEXT) {
            errors.put("note", "Ghi chú tối đa " + MAX_TEXT + " ký tự.");
        }
        failIfAny(errors, "Đề xuất chưa hợp lệ.");
        if (!item.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLY_INACTIVE", "Vật tư đã ngừng sử dụng, không nhận đề xuất mới.");
        }
        UserAccount groom = accounts.findById(actor.id()).orElseThrow();
        RestockProposal p = proposals.save(new RestockProposal(item, groom, wanted,
                note == null || note.isBlank() ? null : note.trim(), clock.now()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("itemId", item.getId());
        details.put("quantity", wanted);
        audit.record(actor, "RESTOCK_PROPOSED", "RESTOCK_PROPOSAL", p.getId(), details);
        notifications.notifyRole(Role.CLUB_MANAGER, "Đề xuất bổ sung mới: " + groom.getDisplayName() + " đề xuất thêm "
                + wanted + " " + item.getUnit() + " \"" + item.getName() + "\".", "/supplies");
        return p;
    }

    /** All proposals for the Club Manager, the Groom's own for a Groom (PERM-040). */
    @Transactional(readOnly = true)
    public List<RestockProposal> listProposals() {
        AccountPrincipal user = access.require(Permission.VIEW_SUPPLIES);
        return Permission.DECIDE_RESTOCK.allows(user.role()) ? proposals.findAllByOrderByCreatedAtDescIdDesc()
                : proposals.findByGroomIdOrderByCreatedAtDescIdDesc(user.id());
    }

    @Transactional
    public RestockProposal approve(Long id) {
        AccountPrincipal actor = access.require(Permission.DECIDE_RESTOCK);
        RestockProposal p = pending(id);
        decide(actor, p, ProposalState.APPROVED, null);
        return p;
    }

    @Transactional
    public RestockProposal reject(Long id, String reason) {
        AccountPrincipal actor = access.require(Permission.DECIDE_RESTOCK);
        RestockProposal p = pending(id);
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalid("VALIDATION_FAILED", "reason", "Bắt buộc nhập lý do từ chối.");
        }
        decide(actor, p, ProposalState.REJECTED, reason.trim());
        return p;
    }

    private RestockProposal pending(Long id) {
        RestockProposal p = proposals.findForDecision(id).orElseThrow(() -> ApiException.notFound("đề xuất"));
        if (p.getState() != ProposalState.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "PROPOSAL_DECIDED", "Đề xuất đã được xử lý.");
        }
        return p;
    }

    private void decide(AccountPrincipal actor, RestockProposal p, ProposalState to, String reason) {
        UserAccount manager = accounts.findById(actor.id()).orElseThrow();
        p.decide(to, manager, reason, clock.now());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("itemId", p.getItem().getId());
        details.put("quantity", p.getQuantity());
        if (reason != null) {
            details.put("reason", reason);
        }
        audit.record(actor, to == ProposalState.APPROVED ? "RESTOCK_APPROVED" : "RESTOCK_REJECTED",
                "RESTOCK_PROPOSAL", p.getId(), details);
        String what = "\"" + p.getItem().getName() + "\" (" + p.getQuantity() + " " + p.getItem().getUnit() + ")";
        notifications.notifyAccount(p.getGroom().getId(), to == ProposalState.APPROVED
                ? "Đề xuất bổ sung " + what + " đã được duyệt."
                : "Đề xuất bổ sung " + what + " bị từ chối: " + reason, "/supplies");
    }

    // ---- validation ---------------------------------------------------------------------------------------

    private record Valid(String name, SupplyCategory category, SupplyUnit unit) {
    }

    /** {@code keepUnitId} is the item's unit before an edit: it stays allowed even when it was deactivated since. */
    private Valid validate(SupplyItemInput in, Long keepUnitId) {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty()) {
            errors.put("name", "Bắt buộc nhập tên vật tư.");
        } else if (name.length() > MAX_NAME) {
            errors.put("name", "Tên vật tư tối đa " + MAX_NAME + " ký tự.");
        }
        SupplyCategory category = SupplyCategory.parse(in.category());
        if (category == null) {
            errors.put("category", "Chọn một nhóm: Thức ăn, Thuốc hoặc Dụng cụ.");
        }
        SupplyUnit unit = null;
        try {
            unit = units.requireUsable(in.unitId(), keepUnitId);
        } catch (ApiException e) {
            errors.put("unitId", e.getMessage());
        }
        failIfAny(errors, "Vật tư chưa hợp lệ.");
        return new Valid(name, category, unit);
    }

    /** A whole number of at least {@code min}; anything else (missing, fractional, negative, huge) is refused. */
    private static Long wholeNumber(BigDecimal value, long min, Map<String, String> errors, String field,
                                    String message) {
        if (value == null) {
            errors.put(field, "Bắt buộc nhập số lượng.");
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > 0 || stripped.compareTo(BigDecimal.valueOf(min)) < 0
                || stripped.compareTo(BigDecimal.valueOf(StockService.MAX_QUANTITY)) > 0) {
            errors.put(field, message);
            return null;
        }
        return stripped.longValueExact();
    }

    /** A price of zero or more with at most two decimals; anything else is refused with a field message. */
    private static BigDecimal priceOrError(BigDecimal price, Map<String, String> errors) {
        try {
            return StockService.validPrice(price);
        } catch (ApiException e) {
            errors.put("price", "Giá phải là số tiền đồng nguyên từ 0 trở lên.");
            return null;
        }
    }

    private static void failIfAny(Map<String, String> errors, String message) {
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, errors);
        }
    }

    private void requireUnique(String name, SupplyCategory category, Long selfId) {
        for (SupplyItem other : items.findByNameAndCategory(name, category)) {
            if (!Objects.equals(other.getId(), selfId)) {
                throw duplicate(other.getName());
            }
        }
    }

    private static ApiException duplicate(String name) {
        String message = "Đã có vật tư \"" + name + "\" trong nhóm này.";
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SUPPLY", message, Map.of("name", message));
    }

    private static void diff(Map<String, Object> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("from", before == null ? null : before.toString());
            change.put("to", after == null ? null : after.toString());
            changes.put(field, change);
        }
    }
}
