package vn.racehorse.horse.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Breed;
import vn.racehorse.horse.repository.BreedRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Breed catalog: read by every signed-in role, kept by the Club Manager (BR-246, PERM-064, DEC-081). */
@Service
public class BreedService {

    private final BreedRepository breeds;
    private final AccessGuard access;
    private final AuditService audit;

    public BreedService(BreedRepository breeds, AccessGuard access, AuditService audit) {
        this.breeds = breeds;
        this.access = access;
        this.audit = audit;
    }

    /** Every entry for anyone signed in; the client offers the active ones on the horse form. */
    @Transactional(readOnly = true)
    public List<Breed> list() {
        access.current();
        return breeds.findAllByOrderByNameAsc();
    }

    @Transactional
    public Breed add(String name) {
        AccountPrincipal actor = access.require(Permission.MANAGE_BREEDS);
        String clean = clean(name, null);
        Breed breed = breeds.save(new Breed(clean));
        audit.record(actor, "BREED_ADDED", "BREED", breed.getId(), Map.of("name", clean));
        return breed;
    }

    /** Renames and/or switches the entry; "Khác" is never deactivated (AC-548). */
    @Transactional
    public Breed update(Long id, String name, Boolean active) {
        AccountPrincipal actor = access.require(Permission.MANAGE_BREEDS);
        Breed breed = breeds.findById(id).orElseThrow(() -> ApiException.notFound("giống ngựa"));
        Map<String, Object> changes = new LinkedHashMap<>();
        String clean = clean(name, breed);
        if (!clean.equals(breed.getName())) {
            changes.put("name", Map.of("from", breed.getName(), "to", clean));
            breed.rename(clean);
        }
        if (active != null && active != breed.isActive()) {
            if (!active && breed.isProtectedEntry()) {
                throw new ApiException(HttpStatus.CONFLICT, "BREED_PROTECTED", "Mục \"Khác\" không thể ngừng dùng.");
            }
            changes.put("active", Map.of("from", breed.isActive(), "to", active));
            breed.setActive(active);
        }
        if (!changes.isEmpty()) {
            audit.record(actor, "BREED_UPDATED", "BREED", breed.getId(), changes);
        }
        return breed;
    }

    /** A trimmed name, unique ignoring case among the other entries (AC-548). */
    private String clean(String name, Breed self) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw ApiException.invalid("REQUIRED", "name", "Bắt buộc nhập.");
        }
        breeds.findByNameIgnoreCase(clean).filter(b -> self == null || !b.getId().equals(self.getId()))
                .ifPresent(b -> {
                    throw new ApiException(HttpStatus.CONFLICT, "BREED_DUPLICATE", "Tên giống đã tồn tại.",
                            Map.of("name", "Tên giống đã tồn tại."));
                });
        return clean;
    }
}
