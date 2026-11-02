package vn.racehorse.stable.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.stable.dto.SupplyUnitResponse;
import vn.racehorse.stable.entity.SupplyUnit;
import vn.racehorse.stable.repository.SupplyUnitRepository;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The catalog of units supplies are counted in (BR-194, BR-195). A unit is renamed or deactivated, never deleted; a
 * rename reaches every item because items point to the unit row.
 */
@Service
public class SupplyUnitService {

    static final int MAX_NAME = 20;

    private final SupplyUnitRepository units;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;

    public SupplyUnitService(SupplyUnitRepository units, AccessGuard access, AuditService audit, ClubClock clock) {
        this.units = units;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SupplyUnitResponse> list() {
        access.require(Permission.MANAGE_SUPPLY_UNITS);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : units.itemCounts()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return units.findAllByOrderByNameAsc().stream()
                .map(u -> SupplyUnitResponse.of(u, counts.getOrDefault(u.getId(), 0L))).toList();
    }

    @Transactional
    public SupplyUnit add(String rawName) {
        AccountPrincipal actor = access.require(Permission.MANAGE_SUPPLY_UNITS);
        String name = checkedName(rawName, null);
        SupplyUnit unit;
        try {
            unit = units.saveAndFlush(new SupplyUnit(name, clock.now()));
        } catch (DataIntegrityViolationException e) {
            // A concurrent add won the unique index: same message as the pre-check.
            throw duplicate(name);
        }
        audit.record(actor, "SUPPLY_UNIT_ADDED", "SUPPLY_UNIT", unit.getId(), Map.of("name", name));
        return unit;
    }

    @Transactional
    public SupplyUnit rename(Long id, String rawName) {
        AccountPrincipal actor = access.require(Permission.MANAGE_SUPPLY_UNITS);
        SupplyUnit unit = find(id);
        String name = checkedName(rawName, unit.getId());
        String old = unit.getName();
        unit.rename(name);
        try {
            units.flush();
        } catch (DataIntegrityViolationException e) {
            throw duplicate(name);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", old);
        details.put("to", name);
        audit.record(actor, "SUPPLY_UNIT_RENAMED", "SUPPLY_UNIT", unit.getId(), details);
        return unit;
    }

    @Transactional
    public SupplyUnit setActive(Long id, boolean active) {
        AccountPrincipal actor = access.require(Permission.MANAGE_SUPPLY_UNITS);
        SupplyUnit unit = find(id);
        if (unit.isActive() != active) {
            unit.setActive(active);
            audit.record(actor, active ? "SUPPLY_UNIT_ACTIVATED" : "SUPPLY_UNIT_DEACTIVATED", "SUPPLY_UNIT",
                    unit.getId(), Map.of("name", unit.getName()));
        }
        return unit;
    }

    /** An active unit for a new or changed item; {@code keep} is the item's own unit, allowed even when deactivated. */
    SupplyUnit requireUsable(Long unitId, Long keep) {
        if (unitId == null) {
            throw ApiException.invalid("UNIT_REQUIRED", "unitId", "Bắt buộc chọn đơn vị.");
        }
        SupplyUnit unit = units.findById(unitId)
                .orElseThrow(() -> ApiException.invalid("UNIT_UNKNOWN", "unitId", "Đơn vị không có trong danh mục."));
        if (!unit.isActive() && !unit.getId().equals(keep)) {
            throw ApiException.invalid("UNIT_INACTIVE", "unitId", "Đơn vị đã ngừng dùng, hãy chọn đơn vị khác.");
        }
        return unit;
    }

    private SupplyUnit find(Long id) {
        return units.findById(id).orElseThrow(() -> ApiException.notFound("đơn vị"));
    }

    /** NFC, trimmed, runs of spaces collapsed; 1 to 20 characters, one letter at least, letters digits space and "/". */
    static String normalise(String raw) {
        return raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC).replaceAll("\\s+", " ").trim();
    }

    private String checkedName(String raw, Long selfId) {
        String name = normalise(raw);
        String problem = null;
        if (name.isEmpty()) {
            problem = "Bắt buộc nhập tên đơn vị.";
        } else if (name.length() > MAX_NAME) {
            problem = "Tên đơn vị tối đa " + MAX_NAME + " ký tự.";
        } else if (!name.matches("[\\p{L}\\p{Nd} /]+")) {
            problem = "Tên đơn vị chỉ gồm chữ, số, dấu cách và dấu \"/\".";
        } else if (name.codePoints().noneMatch(Character::isLetter)) {
            problem = "Tên đơn vị phải có ít nhất một chữ cái.";
        }
        if (problem != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Đơn vị chưa hợp lệ.",
                    Map.of("name", problem));
        }
        units.findByNameIgnoreCase(name).filter(u -> !u.getId().equals(selfId)).ifPresent(u -> {
            throw duplicate(u.getName());
        });
        return name;
    }

    private static ApiException duplicate(String name) {
        String message = "Đã có đơn vị \"" + name + "\".";
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_UNIT", message, Map.of("name", message));
    }
}
