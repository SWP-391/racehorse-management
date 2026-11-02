package vn.racehorse.stable.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vn.racehorse.stable.dto.AreaRow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalog of stable areas: a unique name and a capacity of stalls; an area is deleted only while empty and its
 * capacity never drops below the highest occupied stall (BR-163, BR-164). The area row is locked for every change.
 */
@Service
public class AreaService {

    static final int MAX_NAME = 100;
    static final int MAX_CAPACITY = 200;

    private final AreaRepository areas;
    private final HorseRepository horses;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;

    public AreaService(AreaRepository areas, HorseRepository horses, AccessGuard access, AuditService audit,
                       ClubClock clock) {
        this.areas = areas;
        this.horses = horses;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AreaRow> list() {
        AccountPrincipal user = access.require(Permission.VIEW_BARN);
        if (user.role() == vn.racehorse.account.entity.Role.HORSE_OWNER) {
            // A Horse Owner is offered only the areas that hold a horse of theirs, with the count of their own (BR-006, BR-209).
            Map<Long, Long> mine = horses.findByOwnerIdOrderByNameAsc(user.id()).stream()
                    .filter(h -> h.getArea() != null)
                    .collect(java.util.stream.Collectors.groupingBy(h -> h.getArea().getId(), java.util.stream.Collectors.counting()));
            return areas.findAllByOrderByNameAsc().stream().filter(a -> mine.containsKey(a.getId()))
                    .map(a -> new AreaRow(a, mine.get(a.getId()))).toList();
        }
        return areas.findAllByOrderByNameAsc().stream()
                .map(a -> new AreaRow(a, horses.countByAreaId(a.getId()))).toList();
    }

    @Transactional
    public AreaRow create(String name, Integer capacity) {
        AccountPrincipal actor = access.require(Permission.MANAGE_AREAS);
        String trimmed = validName(name, capacity);
        requireNameFree(trimmed, null);
        Area area;
        try {
            area = areas.saveAndFlush(new Area(trimmed, capacity, clock.now()));
        } catch (DataIntegrityViolationException e) {
            throw duplicate(trimmed);
        }
        audit.record(actor, "AREA_CREATED", "AREA", area.getId(), Map.of("name", trimmed, "capacity", capacity));
        return new AreaRow(area, 0);
    }

    @Transactional
    public AreaRow update(Long id, String name, Integer capacity) {
        AccountPrincipal actor = access.require(Permission.MANAGE_AREAS);
        Area area = areas.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("khu"));
        String trimmed = validName(name, capacity);
        requireNameFree(trimmed, area.getId());
        List<Horse> inside = horses.findByAreaId(area.getId());
        int highest = inside.stream().mapToInt(Horse::getStallSlot).max().orElse(0);
        if (capacity < highest) {
            String message = "Sức chứa không thể thấp hơn ô số " + highest + " đang có ngựa.";
            throw new ApiException(HttpStatus.CONFLICT, "CAPACITY_BELOW_OCCUPIED", message,
                    Map.of("capacity", message));
        }
        String oldName = area.getName();
        int oldCapacity = area.getCapacity();
        if (!oldName.equals(trimmed)) {
            area.rename(trimmed);
            audit.record(actor, "AREA_RENAMED", "AREA", area.getId(), Map.of("from", oldName, "to", trimmed));
        }
        if (oldCapacity != capacity) {
            area.resize(capacity);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("name", trimmed);
            details.put("from", oldCapacity);
            details.put("to", capacity);
            audit.record(actor, "AREA_RESIZED", "AREA", area.getId(), details);
        }
        try {
            areas.flush();
        } catch (DataIntegrityViolationException e) {
            throw duplicate(trimmed);
        }
        return new AreaRow(area, inside.size());
    }

    @Transactional
    public void delete(Long id) {
        AccountPrincipal actor = access.require(Permission.MANAGE_AREAS);
        Area area = areas.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("khu"));
        if (horses.countByAreaId(area.getId()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "AREA_NOT_EMPTY",
                    "Khu còn ngựa, hãy chuyển hết ngựa sang khu khác trước khi xóa.");
        }
        areas.delete(area);
        audit.record(actor, "AREA_DELETED", "AREA", area.getId(), Map.of("name", area.getName()));
    }

    private static String validName(String name, Integer capacity) {
        Map<String, String> errors = new LinkedHashMap<>();
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            errors.put("name", "Bắt buộc nhập tên khu.");
        } else if (trimmed.length() > MAX_NAME) {
            errors.put("name", "Tên khu tối đa " + MAX_NAME + " ký tự.");
        }
        if (capacity == null || capacity < 1 || capacity > MAX_CAPACITY) {
            errors.put("capacity", "Sức chứa phải là số ô nguyên từ 1 đến " + MAX_CAPACITY + ".");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Khu chưa hợp lệ.", errors);
        }
        return trimmed;
    }

    private void requireNameFree(String name, Long exceptId) {
        Optional<Area> other = areas.findByNameIgnoreCase(name);
        if (other.isPresent() && !other.get().getId().equals(exceptId)) {
            throw duplicate(other.get().getName());
        }
    }

    private static ApiException duplicate(String name) {
        String message = "Đã có khu \"" + name + "\".";
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_AREA", message, Map.of("name", message));
    }
}
