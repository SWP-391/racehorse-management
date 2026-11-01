package vn.racehorse.stable.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Area;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.AreaRepository;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.stable.dto.StallAreaView;
import vn.racehorse.stable.dto.StallBarnRow;
import vn.racehorse.stable.dto.StallBarnView;
import vn.racehorse.stable.dto.StallSlotView;
import vn.racehorse.stable.entity.Chore;
import vn.racehorse.stable.entity.ChoreEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A horse has at most one stall, an area of the catalog plus a slot within its capacity, set only by the Club Manager;
 * the barn page shows every area with all its stalls (BR-129, BR-164, BR-165).
 */
@Service
public class StallService {

    private final HorseRepository horses;
    private final AreaRepository areas;
    private final ChoreService chores;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;

    public StallService(HorseRepository horses, AreaRepository areas, ChoreService chores, AccessGuard access,
                        AuditService audit, ClubClock clock) {
        this.horses = horses;
        this.areas = areas;
        this.chores = chores;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Places or moves a horse into a stall. The area row is locked first, so a placement and a resize take turns; the
     * unique index on (area, slot) stays the last guard against two placements at the same moment.
     */
    @Transactional
    public Horse set(Long horseId, Long areaId, Integer slot) {
        AccountPrincipal actor = access.require(Permission.MANAGE_STALLS);
        Horse horse = find(horseId);
        requireActive(horse);
        Map<String, String> errors = new LinkedHashMap<>();
        if (areaId == null) {
            errors.put("area", "Bắt buộc chọn khu.");
        }
        if (slot == null) {
            errors.put("slot", "Bắt buộc chọn ô.");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Chuồng chưa hợp lệ.", errors);
        }
        Area area = areas.findByIdForUpdate(areaId).orElseThrow(() -> ApiException.notFound("khu"));
        if (slot < 1 || slot > area.getCapacity()) {
            String message = "Ô phải từ 1 đến " + area.getCapacity() + ".";
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Chuồng chưa hợp lệ.",
                    Map.of("slot", message));
        }
        List<Horse> inside = horses.findByAreaId(area.getId());
        List<Horse> others = inside.stream().filter(h -> !Objects.equals(h.getId(), horse.getId())).toList();
        if (others.size() >= area.getCapacity()) {
            String message = "Khu \"" + area.getName() + "\" đã đủ chỗ, hãy tạo khu mới.";
            throw new ApiException(HttpStatus.CONFLICT, "AREA_FULL", message, Map.of("area", message));
        }
        for (Horse other : others) {
            if (other.getStallSlot() == slot.intValue()) {
                throw taken(area, slot, other.getName());
            }
        }
        String before = describe(horse);
        horse.placeInStall(area, slot, clock.now());
        try {
            horses.flush();
        } catch (DataIntegrityViolationException e) {
            // A concurrent placement won the unique index (BR-164).
            throw taken(area, slot, "khác");
        }
        if (!Objects.equals(before, describe(horse))) {
            audit.record(actor, "STALL_CHANGED", "HORSE", horse.getId(), Map.of("from", before, "to", describe(horse)));
        }
        return horse;
    }

    @Transactional
    public void clear(Long horseId) {
        AccountPrincipal actor = access.require(Permission.MANAGE_STALLS);
        Horse horse = find(horseId);
        requireActive(horse);
        if (horse.getArea() != null || horse.getLegacyStall() != null) {
            String before = describe(horse);
            horse.clearStall(clock.now());
            audit.record(actor, "STALL_CHANGED", "HORSE", horse.getId(), Map.of("from", before, "to", ""));
        }
    }

    /**
     * Every area with all its stalls, empty ones as placeholders, and the horses that have no stall; a Groom gets
     * nothing else of the horse, a Horse Owner gets only own horses and no placeholders (BR-165, TECH-143).
     */
    @Transactional(readOnly = true)
    public StallBarnView barn() {
        AccountPrincipal user = access.require(Permission.VIEW_BARN);
        boolean owner = user.role() == Role.HORSE_OWNER;
        Map<Long, Map<Chore, ChoreEntry>> done = chores.doneByHorse(clock.today());
        List<Horse> visible = chores.visibleHorses(user);
        List<StallAreaView> views = new ArrayList<>();
        for (Area area : areas.findAllByOrderByNameAsc()) {
            List<StallSlotView> slots = new ArrayList<>();
            for (int slot = 1; slot <= area.getCapacity(); slot++) {
                int number = slot;
                StallBarnRow row = visible.stream()
                        .filter(h -> h.getArea() != null && h.getArea().getId().equals(area.getId())
                                && h.getStallSlot() == number)
                        .findFirst().map(h -> row(h, done)).orElse(null);
                if (row != null || !owner) {
                    slots.add(new StallSlotView(slot, row));
                }
            }
            if (!owner || !slots.isEmpty()) {
                views.add(new StallAreaView(area, slots));
            }
        }
        List<StallBarnRow> unplaced = visible.stream().filter(h -> h.getArea() == null)
                .sorted(Comparator.comparing((Horse h) -> h.getName().toLowerCase()))
                .map(h -> row(h, done)).toList();
        return new StallBarnView(views, unplaced, Permission.MANAGE_STALLS.allows(user.role()));
    }

    private static StallBarnRow row(Horse h, Map<Long, Map<Chore, ChoreEntry>> done) {
        return new StallBarnRow(h, done.getOrDefault(h.getId(), Map.of()).size(), Chore.values().length);
    }

    private Horse find(Long id) {
        return horses.findById(id).orElseThrow(() -> ApiException.notFound("ngựa"));
    }

    private static void requireActive(Horse horse) {
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: hồ sơ chỉ xem.");
        }
    }

    private static ApiException taken(Area area, int slot, String horseName) {
        String message = area.getName() + ", ô " + slot + " đang có ngựa \"" + horseName + "\".";
        return new ApiException(HttpStatus.CONFLICT, "STALL_TAKEN", message, Map.of("slot", message));
    }

    private static String describe(Horse h) {
        if (h.getArea() != null) {
            return h.getArea().getName() + ", ô " + h.getStallSlot();
        }
        return h.getLegacyStall() == null ? "" : h.getLegacyStall();
    }
}
