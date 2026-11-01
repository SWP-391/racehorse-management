package vn.racehorse.horse.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.billing.service.OwnerPeriodService;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.common.ClubClock;
import vn.racehorse.horse.dto.HorseAncestorInput;
import vn.racehorse.horse.dto.HorseProfile;
import vn.racehorse.horse.entity.ArchiveReason;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.entity.PedigreeAncestor;
import vn.racehorse.horse.entity.PedigreePosition;
import vn.racehorse.horse.entity.Sex;
import vn.racehorse.horse.event.HorseRegistered;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.horse.repository.PedigreeAncestorRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Horse registry: profiles, ownership and owner visibility, pedigree, archive and restore (REQ-022, REQ-030). */
@Service
public class HorseService {

    static final String ARCHIVED = "Ngựa đã ngừng quản lý: hồ sơ chỉ xem.";

    private final HorseRepository horses;
    private final PedigreeAncestorRepository ancestors;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final ApplicationEventPublisher events;
    private final HorseTrainerService trainers;
    private final OwnerPeriodService ownerPeriods;
    private final JdbcTemplate jdbc;

    public HorseService(HorseRepository horses, PedigreeAncestorRepository ancestors, UserAccountRepository accounts,
                        AccessGuard access, AuditService audit, ClubClock clock, ApplicationEventPublisher events,
                        HorseTrainerService trainers, OwnerPeriodService ownerPeriods, JdbcTemplate jdbc) {
        this.horses = horses;
        this.ancestors = ancestors;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.events = events;
        this.trainers = trainers;
        this.ownerPeriods = ownerPeriods;
        this.jdbc = jdbc;
    }

    /** All horses for staff with PERM-001; only own horses for a Horse Owner (BR-006). */
    @Transactional(readOnly = true)
    public List<Horse> list() {
        AccountPrincipal user = access.require(Permission.VIEW_HORSES);
        return user.role() == Role.HORSE_OWNER
                ? horses.findByOwnerIdOrderByNameAsc(user.id())
                : horses.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Horse get(Long id) {
        AccountPrincipal user = access.require(Permission.VIEW_HORSES);
        return visible(user, find(id));
    }

    @Transactional
    public Horse register(HorseProfile profile) {
        AccountPrincipal actor = access.require(Permission.MANAGE_HORSES);
        UserAccount owner = ownerAccount(profile.ownerId());
        UserAccount trainer = trainers.requireTrainerAccount(profile.trainerId(), "trainerId");
        Horse horse = new Horse(clock.now());
        horse.assignRegistrationNumber(nextRegistrationNumber());
        horse.setProfile(profile.name(), profile.sex(), profile.birthDate(),
                blankToNull(profile.coatColour()), blankToNull(profile.breed()), owner, clock.now());
        trainers.setAtRegistration(horse, trainer);
        horse = horses.save(horse);
        audit.record(actor, "HORSE_REGISTERED", "HORSE", horse.getId(), Map.of(
                "name", horse.getName(), "registrationNumber", horse.getRegistrationNumber(),
                "ownerId", owner.getId(), "trainerId", trainer.getId()));
        ownerPeriods.open(horse.getId(), owner.getId(), clock.today());
        events.publishEvent(new HorseRegistered(horse.getId(), horse.getName()));
        return horse;
    }

    @Transactional
    public Horse update(Long id, HorseProfile profile) {
        AccountPrincipal actor = access.require(Permission.MANAGE_HORSES);
        Horse horse = find(id);
        requireNotArchived(horse);
        UserAccount owner = ownerAccount(profile.ownerId());

        Map<String, Object> changes = new LinkedHashMap<>();
        diff(changes, "name", horse.getName(), profile.name());
        diff(changes, "sex", horse.getSex(), profile.sex());
        diff(changes, "birthDate", horse.getBirthDate(), profile.birthDate());
        diff(changes, "coatColour", horse.getCoatColour(), blankToNull(profile.coatColour()));
        diff(changes, "breed", horse.getBreed(), blankToNull(profile.breed()));
        diff(changes, "ownerId", horse.getOwner().getId(), owner.getId());

        boolean ownerChanged = !Objects.equals(horse.getOwner().getId(), owner.getId());
        horse.setProfile(profile.name(), profile.sex(), profile.birthDate(),
                blankToNull(profile.coatColour()), blankToNull(profile.breed()), owner, clock.now());
        if (ownerChanged) {
            // Charges follow the owner of their own day (BR-181).
            ownerPeriods.change(horse.getId(), owner.getId(), clock.today());
        }
        if (!changes.isEmpty()) {
            audit.record(actor, "HORSE_UPDATED", "HORSE", horse.getId(), changes);
        }
        // A changed trainer goes through the same path as the dedicated hand-over, audited and announced (BR-155).
        if (profile.trainerId() != null || horse.getMainTrainer() == null) {
            trainers.handOver(actor, horse.getId(), profile.trainerId());
        }
        return horse;
    }

    /** Changes the main Head Trainer of a horse (PERM-045, BR-155). */
    @Transactional
    public Horse changeTrainer(Long id, Long trainerId) {
        AccountPrincipal actor = access.require(Permission.ASSIGN_TRAINER);
        return trainers.handOver(actor, id, trainerId);
    }

    /** Lends the horse to another trainer until the return date (PERM-045, BR-156). */
    @Transactional
    public Horse lendTrainer(Long id, Long trainerId, LocalDate until) {
        AccountPrincipal actor = access.require(Permission.ASSIGN_TRAINER);
        return trainers.lend(actor, id, trainerId, until);
    }

    @Transactional
    public Horse endLoan(Long id) {
        AccountPrincipal actor = access.require(Permission.ASSIGN_TRAINER);
        return trainers.endLoanEarly(actor, id);
    }

    @Transactional(readOnly = true)
    public Map<PedigreePosition, PedigreeAncestor> pedigree(Long id) {
        AccountPrincipal user = access.require(Permission.VIEW_HORSES);
        Horse horse = visible(user, find(id));
        Map<PedigreePosition, PedigreeAncestor> result = new EnumMap<>(PedigreePosition.class);
        ancestors.findByHorseId(horse.getId()).forEach(a -> result.put(a.getPosition(), a));
        return result;
    }

    /** Replaces the pedigree; positions not given are unknown (BR-004). Integrity per BR-005. */
    @Transactional
    public Map<PedigreePosition, PedigreeAncestor> updatePedigree(Long id, List<HorseAncestorInput> inputs) {
        AccountPrincipal actor = access.require(Permission.MANAGE_HORSES);
        Horse horse = find(id);
        requireNotArchived(horse);

        Map<PedigreePosition, PedigreeAncestor> next = new EnumMap<>(PedigreePosition.class);
        for (HorseAncestorInput input : inputs == null ? List.<HorseAncestorInput>of() : inputs) {
            if (input.position() == null) {
                throw ApiException.invalid("PEDIGREE_POSITION", "ancestors", "Thiếu vị trí trong dòng dõi.");
            }
            String field = "ancestors." + input.position().name();
            if (next.containsKey(input.position())) {
                throw ApiException.invalid("PEDIGREE_DUPLICATE_POSITION", field, "Vị trí bị lặp lại.");
            }
            boolean hasHorse = input.horseId() != null;
            boolean hasName = input.name() != null && !input.name().isBlank();
            if (hasHorse == hasName) {
                throw ApiException.invalid("PEDIGREE_ANCESTOR", field,
                        "Chọn một ngựa trong hệ thống hoặc nhập tên, không chọn cả hai.");
            }
            if (hasHorse) {
                if (Objects.equals(input.horseId(), horse.getId())) {
                    throw ApiException.invalid("PEDIGREE_SELF", field, "Ngựa không thể là tổ tiên của chính nó.");
                }
                Horse ancestor = horses.findById(input.horseId())
                        .orElseThrow(() -> ApiException.invalid("PEDIGREE_UNKNOWN_HORSE", field,
                                "Không tìm thấy ngựa được chọn."));
                if (ancestor.getSex() != input.position().requiredSex()) {
                    throw ApiException.invalid("PEDIGREE_SEX", field, input.position().requiredSex() == Sex.MALE
                            ? "Vị trí này phải là ngựa đực." : "Vị trí này phải là ngựa cái.");
                }
                next.put(input.position(), new PedigreeAncestor(horse.getId(), input.position(), ancestor, null));
            } else {
                next.put(input.position(),
                        new PedigreeAncestor(horse.getId(), input.position(), null, input.name().trim()));
            }
        }

        ancestors.deleteByHorseId(horse.getId());
        ancestors.saveAll(next.values());
        Map<String, Object> details = new LinkedHashMap<>();
        next.forEach((position, a) -> details.put(position.name(),
                a.getAncestorHorse() != null ? "horse:" + a.getAncestorHorse().getId() : "name:" + a.getAncestorName()));
        audit.record(actor, "PEDIGREE_UPDATED", "HORSE", horse.getId(), details);
        return next;
    }

    @Transactional
    public Horse archive(Long id, ArchiveReason reason, String note) {
        AccountPrincipal actor = access.require(Permission.ARCHIVE_HORSES);
        if (reason == null) {
            throw ApiException.invalid("ARCHIVE_REASON_REQUIRED", "reason", "Bắt buộc chọn lý do.");
        }
        Horse horse = find(id);
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý.");
        }
        String freed = horse.getArea() == null ? null : horse.getArea().getName() + ", ô " + horse.getStallSlot();
        boolean hadStall = freed != null || horse.getLegacyStall() != null;
        horse.archive(reason, blankToNull(note), clock.now());
        // Archiving frees the stall in the same transaction; restoring does not bring it back (BR-129, TECH-093).
        if (hadStall) {
            horse.clearStall(clock.now());
            audit.record(actor, "STALL_CHANGED", "HORSE", horse.getId(), Map.of(
                    "from", freed == null ? "" : freed, "to", "", "reason", "ARCHIVED"));
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason.name());
        if (horse.getArchiveNote() != null) {
            details.put("note", horse.getArchiveNote());
        }
        audit.record(actor, "HORSE_ARCHIVED", "HORSE", horse.getId(), details);
        events.publishEvent(new vn.racehorse.events.DomainEvents.HorseArchived(horse.getId()));
        return horse;
    }

    @Transactional
    public Horse restore(Long id) {
        AccountPrincipal actor = access.require(Permission.ARCHIVE_HORSES);
        Horse horse = find(id);
        if (!horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_NOT_ARCHIVED", "Ngựa đang được quản lý.");
        }
        ArchiveReason previous = horse.getArchiveReason();
        horse.restore(clock.now());
        audit.record(actor, "HORSE_RESTORED", "HORSE", horse.getId(), Map.of("previousReason", previous.name()));
        return horse;
    }

    private Horse find(Long id) {
        return horses.findById(id).orElseThrow(() -> ApiException.notFound("ngựa"));
    }

    private Horse visible(AccountPrincipal user, Horse horse) {
        if (user.role() == Role.HORSE_OWNER && !Objects.equals(horse.getOwner().getId(), user.id())) {
            throw ApiException.forbidden();
        }
        return horse;
    }

    private void requireNotArchived(Horse horse) {
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", ARCHIVED);
        }
    }

    /** `NH-` + club-local year + running number from a sequence: atomic, never repeated or reused (BR-192, BR-193). */
    private String nextRegistrationNumber() {
        long next = jdbc.queryForObject("select nextval('horse_number_seq')", Long.class);
        return String.format("NH-%d-%04d", clock.today().getYear(), next);
    }

    private UserAccount ownerAccount(Long ownerId) {
        return accounts.findById(ownerId)
                .filter(a -> a.getRole() == Role.HORSE_OWNER && a.isActive())
                .orElseThrow(() -> ApiException.invalid("INVALID_OWNER", "ownerId",
                        "Chủ sở hữu phải là tài khoản Chủ ngựa đang hoạt động."));
    }

    private static void diff(Map<String, Object> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("from", before == null ? null : before.toString());
            change.put("to", after == null ? null : after.toString());
            changes.put(field, change);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
