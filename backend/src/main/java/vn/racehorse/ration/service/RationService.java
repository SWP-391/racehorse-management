package vn.racehorse.ration.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.ration.dto.RationItemInput;
import vn.racehorse.ration.dto.RationMealInput;
import vn.racehorse.ration.dto.RationView;
import vn.racehorse.ration.entity.ItemType;
import vn.racehorse.ration.entity.RationItem;
import vn.racehorse.ration.entity.RationMeal;
import vn.racehorse.ration.entity.RationVersion;
import vn.racehorse.ration.repository.RationVersionRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.stable.entity.SupplyCategory;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stable.repository.SupplyItemRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The approved ration of a horse: composed by the Veterinarian, saved as immutable versions (BR-137 to BR-145).
 * It never touches health status, lock, training or supply quantities (BR-145, TECH-104).
 */
@Service
public class RationService {

    private static final BigDecimal MAX_QUANTITY = new BigDecimal("100000");

    private final RationVersionRepository versions;
    private final HorseRepository horses;
    private final SupplyItemRepository supplies;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;

    public RationService(RationVersionRepository versions, HorseRepository horses, SupplyItemRepository supplies,
                         UserAccountRepository accounts, AccessGuard access, AuditService audit, ClubClock clock) {
        this.versions = versions;
        this.horses = horses;
        this.supplies = supplies;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RationView view(Long horseId) {
        AccountPrincipal user = access.require(Permission.VIEW_RATION);
        Horse horse = horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        boolean owner = user.role() == Role.HORSE_OWNER;
        if (owner && !ownsHorse(user, horse)) {
            throw ApiException.forbidden();
        }
        List<RationVersion> all = versions.findByHorseIdOrderByVersionNoDesc(horseId);
        return new RationView(horse, all.isEmpty() ? null : all.get(0), owner ? null : all);
    }

    /** The current meals of every horse the viewer may see, keyed by horse id (used beside the chore "Cho ăn"). */
    @Transactional(readOnly = true)
    public Map<Horse, RationVersion> currentForVisibleHorses() {
        AccountPrincipal user = access.require(Permission.VIEW_RATION);
        List<Horse> visible = (user.role() == Role.HORSE_OWNER
                ? horses.findByOwnerIdOrderByNameAsc(user.id())
                : horses.findAllByOrderByNameAsc()).stream().filter(h -> !h.isArchived()).toList();
        Map<Horse, RationVersion> out = new LinkedHashMap<>();
        if (visible.isEmpty()) {
            return out;
        }
        Map<Long, RationVersion> latest = new LinkedHashMap<>();
        for (RationVersion v : versions.findByHorseIdInOrderByVersionNoDesc(visible.stream().map(Horse::getId).toList())) {
            latest.putIfAbsent(v.getHorseId(), v);
        }
        for (Horse h : visible) {
            RationVersion v = latest.get(h.getId());
            if (v != null) {
                out.put(h, v);
            }
        }
        return out;
    }

    /** The active Thức ăn items a ration may use; the Veterinarian has no access to the supplies page (TECH-101). */
    @Transactional(readOnly = true)
    public List<SupplyItem> supplyOptions() {
        access.require(Permission.COMPOSE_RATION);
        return supplies.findByActiveTrueOrderByNameAsc().stream()
                .filter(i -> i.getCategory() == SupplyCategory.FEED).toList();
    }

    @Transactional
    public RationView save(Long horseId, List<RationMealInput> meals) {
        AccountPrincipal actor = access.require(Permission.COMPOSE_RATION);
        Horse horse = horseId == null ? null : horses.findByIdForUpdate(horseId).orElse(null);
        if (horse == null) {
            throw ApiException.notFound("ngựa");
        }
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED",
                    "Ngựa đã ngừng quản lý: không thể sửa khẩu phần.");
        }
        List<Normal> normal = validate(meals);
        Optional<RationVersion> current = versions.findFirstByHorseIdOrderByVersionNoDesc(horseId);
        if (current.isPresent() && sameContent(current.get(), normal)) {
            return view(horseId);
        }
        UserAccount saver = accounts.findById(actor.id()).orElseThrow();
        int next = current.map(v -> v.getVersionNo() + 1).orElse(1);
        RationVersion version = new RationVersion(horseId, next, clock.today(), saver, clock.now());
        int mealPos = 0;
        for (Normal m : normal) {
            RationMeal meal = new RationMeal(version, mealPos++, m.name(), m.time());
            int itemPos = 0;
            for (NormalItem i : m.items()) {
                meal.addItem(new RationItem(meal, itemPos++, i.type(), i.supply(), i.quantity()));
            }
            version.addMeal(meal);
        }
        versions.saveAndFlush(version);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("horseId", horseId);
        details.put("version", next);
        details.put("meals", normal.size());
        audit.record(actor, "RATION_SAVED", "HORSE", horseId, details);
        return view(horseId);
    }

    // ---- validation (TECH-099) ----------------------------------------------------------------------------

    private record NormalItem(ItemType type, SupplyItem supply, BigDecimal quantity) {
    }

    private record Normal(String name, LocalTime time, List<NormalItem> items) {
    }

    private List<Normal> validate(List<RationMealInput> meals) {
        if (meals == null || meals.isEmpty()) {
            throw invalid("meals", "Khẩu phần cần ít nhất một bữa.");
        }
        List<Normal> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < meals.size(); i++) {
            RationMealInput m = meals.get(i);
            String at = "meals[" + i + "]";
            String name = m == null || m.name() == null ? "" : m.name().trim();
            if (name.isEmpty()) {
                throw invalid(at + ".name", "Bắt buộc nhập tên bữa.");
            }
            if (!names.add(name.toLowerCase())) {
                throw invalid(at + ".name", "Tên bữa \"" + name + "\" bị trùng trong khẩu phần.");
            }
            LocalTime time = parseTime(m.time(), at + ".time");
            if (m.items() == null || m.items().isEmpty()) {
                throw invalid(at + ".items", "Bữa \"" + name + "\" cần ít nhất một thành phần.");
            }
            List<NormalItem> items = new ArrayList<>();
            Set<Long> used = new HashSet<>();
            for (int j = 0; j < m.items().size(); j++) {
                items.add(validateItem(m.items().get(j), at + ".items[" + j + "]", used));
            }
            out.add(new Normal(name, time, items));
        }
        return out;
    }

    private NormalItem validateItem(RationItemInput in, String at, Set<Long> used) {
        if (in == null) {
            throw invalid(at, "Thành phần không hợp lệ.");
        }
        ItemType type = ItemType.parse(in.type());
        if (type == null) {
            throw invalid(at + ".type", "Chọn loại thành phần (Ngũ cốc, Cỏ hoặc Vitamin).");
        }
        SupplyItem supply = in.supplyItemId() == null ? null : supplies.findById(in.supplyItemId()).orElse(null);
        if (supply == null) {
            throw invalid(at + ".supplyItemId", "Vật tư không tồn tại.");
        }
        if (supply.getCategory() != SupplyCategory.FEED) {
            throw invalid(at + ".supplyItemId", "\"" + supply.getName() + "\" không thuộc nhóm Thức ăn.");
        }
        if (!supply.isActive()) {
            throw invalid(at + ".supplyItemId", "Vật tư \"" + supply.getName() + "\" đã ngừng dùng; hãy thay hoặc bỏ.");
        }
        BigDecimal quantity = parseQuantity(in.quantity(), at + ".quantity");
        if (!used.add(supply.getId())) {
            throw invalid(at + ".supplyItemId", "Vật tư \"" + supply.getName() + "\" bị trùng trong bữa.");
        }
        return new NormalItem(type, supply, quantity);
    }

    private static LocalTime parseTime(String value, String field) {
        try {
            if (value == null || !value.trim().matches("\\d{1,2}:\\d{2}")) {
                throw new DateTimeParseException("time", String.valueOf(value), 0);
            }
            return LocalTime.parse(value.trim().length() == 4 ? "0" + value.trim() : value.trim());
        } catch (DateTimeParseException e) {
            throw invalid(field, "Giờ ăn không hợp lệ (HH:mm).");
        }
    }

    private static BigDecimal parseQuantity(String value, String field) {
        BigDecimal q;
        try {
            q = new BigDecimal(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw invalid(field, "Số lượng phải là số lớn hơn 0.");
        }
        q = q.setScale(2, RoundingMode.HALF_UP);
        if (q.signum() <= 0 || q.compareTo(MAX_QUANTITY) >= 0) {
            throw invalid(field, "Số lượng phải lớn hơn 0 và nhỏ hơn 100000.");
        }
        return q;
    }

    private static boolean sameContent(RationVersion current, List<Normal> normal) {
        if (current.getMeals().size() != normal.size()) {
            return false;
        }
        for (int i = 0; i < normal.size(); i++) {
            RationMeal a = current.getMeals().get(i);
            Normal b = normal.get(i);
            if (!a.getName().equals(b.name()) || !a.getTime().equals(b.time())
                    || a.getItems().size() != b.items().size()) {
                return false;
            }
            for (int j = 0; j < b.items().size(); j++) {
                RationItem x = a.getItems().get(j);
                NormalItem y = b.items().get(j);
                if (x.getType() != y.type() || !x.getSupplyItem().getId().equals(y.supply().getId())
                        || x.getQuantity().compareTo(y.quantity()) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean ownsHorse(AccountPrincipal user, Horse horse) {
        return horse.getOwner() != null && user.id().equals(horse.getOwner().getId());
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, Map.of(field, message));
    }
}
