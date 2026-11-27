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
import vn.racehorse.horse.entity.Breed;
import vn.racehorse.horse.entity.IntakeState;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.entity.PedigreeAncestor;
import vn.racehorse.horse.entity.PedigreePosition;
import vn.racehorse.horse.entity.Sex;
import vn.racehorse.horse.event.HorseRegistered;
import vn.racehorse.horse.repository.BreedRepository;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.horse.repository.PedigreeAncestorRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.HorseVisibility;
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
    private final BreedRepository breeds;
    private final NotificationService notifications;
    private final vn.racehorse.inbox.service.InboxService inbox;
    private final CareTeamService careTeam;
    private final HorseVisibility visibility;

    public HorseService(HorseRepository horses, PedigreeAncestorRepository ancestors, UserAccountRepository accounts,
                        AccessGuard access, AuditService audit, ClubClock clock, ApplicationEventPublisher events,
                        HorseTrainerService trainers, OwnerPeriodService ownerPeriods, JdbcTemplate jdbc,
                        BreedRepository breeds, NotificationService notifications,
                        vn.racehorse.inbox.service.InboxService inbox, CareTeamService careTeam,
                        HorseVisibility visibility) {
        this.careTeam = careTeam;
        this.visibility = visibility;
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
        this.breeds = breeds;
        this.notifications = notifications;
        this.inbox = inbox;
    }

    /**
     * All horses in any intake state for staff with PERM-001, trainers and Veterinarians included in the registry
     * view only through the controller filters; only own horses for a Horse Owner (BR-006). A Head Trainer or a
     * Veterinarian sees taken-in horses only (BR-245).
     */
    @Transactional(readOnly = true)
    public List<Horse> list() {
        access.require(Permission.VIEW_HORSES);
        // One rule for every role (BR-261): the Club Manager all, staff the horses they look after, an owner own.
        return visibility.registry();
    }

    /** A horse the person may not see answers 404 {@code HORSE_NOT_FOUND}, as one that does not exist (BR-261). */
    @Transactional(readOnly = true)
    public Horse get(Long id) {
        access.require(Permission.VIEW_HORSES);
        return visibility.require(id);
    }

    /** The Horse Owner registers an own horse; it waits for take-in and has no trainer yet (BR-243, AC-001, AC-538). */
    @Transactional
    public Horse register(HorseProfile profile) {
        AccountPrincipal actor = access.require(Permission.REGISTER_OWN_HORSE);
        UserAccount owner = accounts.findById(actor.id()).orElseThrow(ApiException::forbidden);
        Breed breed = chooseBreed(profile.breedId(), null, true);
        Horse horse = new Horse(clock.now());
        horse.assignRegistrationNumber(nextRegistrationNumber());
        horse.setProfile(profile.name(), profile.sex(), profile.birthDate(),
                blankToNull(profile.coatColour()), breed, owner, clock.now());
        horse = horses.save(horse);
        audit.record(actor, "HORSE_REGISTERED", "HORSE", horse.getId(), Map.of(
                "name", horse.getName(), "registrationNumber", horse.getRegistrationNumber(),
                "ownerId", owner.getId(), "intakeState", IntakeState.PENDING.name()));
        ownerPeriods.open(horse.getId(), owner.getId(), clock.today());
        notifications.notifyRole(Role.CLUB_MANAGER,
                "Ngựa \"" + horse.getName() + "\" của " + owner.getDisplayName() + " đang chờ tiếp nhận.",
                "/horses/" + horse.getId());
        // The owner writes the automatic conversation to the Club Manager (BR-253).
        inbox.startAutomatic(owner.getId(), java.util.List.of(), horse.getId(),
                "Ngựa \"" + horse.getName() + "\" chờ tiếp nhận", "ngựa \"" + horse.getName() + "\" chờ tiếp nhận.");
        return horse;
    }

    /**
     * The Club Manager takes a waiting horse in with an active Head Trainer: the horse is managed from now, gets the
     * intake lock and the starting status through the registered event, and its daily fee starts today (BR-244,
     * BR-245, BR-008, BR-179, AC-284, AC-540, AC-541).
     */
    @Transactional
    public Horse takeIn(Long id, Long trainerId, Long vetId, Long groomId) {
        AccountPrincipal actor = access.require(Permission.TAKE_IN_HORSES);
        Horse horse = findForUpdate(id);
        requireWaiting(horse);
        requireNotArchived(horse);
        UserAccount trainer = trainers.requireTrainerAccount(trainerId, "trainerId");
        // The three people are all mandatory and each has room for one more horse (BR-256, BR-257, AC-580, AC-581).
        UserAccount[] team = careTeam.requireTeamForTakeIn(trainer, vetId, groomId);
        UserAccount vet = team[0];
        UserAccount groom = team[1];
        horse.accept(trainer, vet, groom, clock.now());
        audit.record(actor, "HORSE_TAKEN_IN", "HORSE", horse.getId(), Map.of(
                "trainerId", trainer.getId(), "veterinarianId", vet.getId(), "groomId", groom.getId(),
                "ownerId", horse.getOwner().getId()));
        notifications.notifyAccount(horse.getOwner().getId(),
                "Ngựa \"" + horse.getName() + "\" đã được tiếp nhận.", "/horses/" + horse.getId());
        notifications.notifyAccount(vet.getId(), "Ngựa \"" + horse.getName() + "\" được giao cho bạn phụ trách.",
                "/horses/" + horse.getId());
        notifications.notifyAccount(groom.getId(), "Ngựa \"" + horse.getName() + "\" được giao cho bạn phụ trách.",
                "/horses/" + horse.getId());
        inbox.startAutomatic(actor.id(), java.util.List.of(horse.getOwner().getId()), horse.getId(),
                "Ngựa \"" + horse.getName() + "\" đã được tiếp nhận",
                "ngựa \"" + horse.getName() + "\" đã được tiếp nhận, HLV phụ trách là " + trainer.getDisplayName()
                        + ", bác sĩ thú y là " + vet.getDisplayName() + ", nhân viên chăm sóc là "
                        + groom.getDisplayName() + ".");
        events.publishEvent(new HorseRegistered(horse.getId(), horse.getName()));
        return horse;
    }

    /** The Club Manager refuses a waiting horse with a reason that goes to the owner (BR-244, AC-542, AC-543). */
    @Transactional
    public Horse refuse(Long id, String reason) {
        AccountPrincipal actor = access.require(Permission.TAKE_IN_HORSES);
        Horse horse = findForUpdate(id);
        requireWaiting(horse);
        String text = blankToNull(reason);
        if (text == null) {
            throw ApiException.invalid("REFUSAL_REASON_REQUIRED", "reason", "Bắt buộc nhập lý do từ chối.");
        }
        horse.refuse(text, clock.now());
        audit.record(actor, "HORSE_REFUSED", "HORSE", horse.getId(), Map.of("reason", text));
        notifications.notifyAccount(horse.getOwner().getId(),
                "Ngựa \"" + horse.getName() + "\" bị từ chối tiếp nhận. Lý do: " + text, "/horses/" + horse.getId());
        inbox.startAutomatic(actor.id(), java.util.List.of(horse.getOwner().getId()), horse.getId(),
                "Ngựa \"" + horse.getName() + "\" bị từ chối tiếp nhận", text);
        return horse;
    }

    /** The owner withdraws a horse that still waits (BR-243, AC-539). */
    @Transactional
    public Horse withdraw(Long id) {
        AccountPrincipal actor = access.require(Permission.REGISTER_OWN_HORSE);
        Horse horse = findForUpdate(id);
        requireOwn(actor, horse);
        requireWaiting(horse);
        horse.withdraw(clock.now());
        audit.record(actor, "HORSE_WITHDRAWN", "HORSE", horse.getId(), Map.of("name", horse.getName()));
        return horse;
    }

    /**
     * The Club Manager edits any horse (PERM-003); the owner edits an own horse only while it waits (PERM-062). A
     * Head Trainer, a Veterinarian or a Groom is refused (AC-005, AC-546).
     */
    @Transactional
    public Horse update(Long id, HorseProfile profile) {
        AccountPrincipal user = access.current();
        boolean owner = user != null && user.role() == Role.HORSE_OWNER;
        AccountPrincipal actor = access.require(owner ? Permission.REGISTER_OWN_HORSE : Permission.MANAGE_HORSES);
        Horse horse = find(id);
        if (owner) {
            requireOwn(actor, horse);
            requireWaiting(horse);
        }
        requireNotArchived(horse);
        UserAccount newOwner = owner ? horse.getOwner() : ownerAccount(profile.ownerId());
        Breed breed = chooseBreed(profile.breedId(), horse.getBreed(), owner);

        Map<String, Object> changes = new LinkedHashMap<>();
        diff(changes, "name", horse.getName(), profile.name());
        diff(changes, "sex", horse.getSex(), profile.sex());
        diff(changes, "birthDate", horse.getBirthDate(), profile.birthDate());
        diff(changes, "coatColour", horse.getCoatColour(), blankToNull(profile.coatColour()));
        diff(changes, "breed", horse.getBreed() == null ? null : horse.getBreed().getName(),
                breed == null ? null : breed.getName());
        diff(changes, "ownerId", horse.getOwner().getId(), newOwner.getId());

        boolean ownerChanged = !Objects.equals(horse.getOwner().getId(), newOwner.getId());
        horse.setProfile(profile.name(), profile.sex(), profile.birthDate(),
                blankToNull(profile.coatColour()), breed, newOwner, clock.now());
        if (ownerChanged) {
            // Charges follow the owner of their own day (BR-181).
            ownerPeriods.change(horse.getId(), newOwner.getId(), clock.today());
        }
        if (!changes.isEmpty()) {
            audit.record(actor, "HORSE_UPDATED", "HORSE", horse.getId(), changes);
        }
        // A changed trainer goes through the same path as the dedicated hand-over, audited and announced (BR-155).
        // A horse that is not taken in has no trainer: take-in chooses it.
        if (!owner && horse.isTakenIn() && (profile.trainerId() != null || horse.getMainTrainer() == null)) {
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
        Horse horse = get(id);
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

    private Horse findForUpdate(Long id) {
        return horses.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("ngựa"));
    }

    private Horse find(Long id) {
        return horses.findById(id).orElseThrow(() -> ApiException.notFound("ngựa"));
    }

    /** A horse that is waiting, refused or withdrawn cannot be stalled, scheduled or fed (BR-245, AC-544). */
    public static void requireTakenIn(Horse horse) {
        if (!horse.isTakenIn()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_NOT_TAKEN_IN",
                    "Ngựa chưa được tiếp nhận nên chưa thể thực hiện thao tác này.");
        }
    }

    private void requireOwn(AccountPrincipal actor, Horse horse) {
        if (!Objects.equals(horse.getOwner().getId(), actor.id())) {
            throw HorseVisibility.notFound();
        }
    }

    private void requireWaiting(Horse horse) {
        if (horse.getIntakeState() != IntakeState.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_NOT_WAITING",
                    "Ngựa không ở trạng thái chờ tiếp nhận.");
        }
    }

    /**
     * An active entry of the catalog, or the entry the horse already has even when it was deactivated since (BR-246).
     * A missing choice is allowed only on an edit by the Club Manager of a horse that never had one (BR-003).
     */
    private Breed chooseBreed(Long breedId, Breed current, boolean required) {
        if (breedId == null) {
            if (required || current != null) {
                throw ApiException.invalid("BREED_REQUIRED", "breedId", "Bắt buộc chọn giống ngựa.");
            }
            return null;
        }
        Breed breed = breeds.findById(breedId)
                .orElseThrow(() -> ApiException.invalid("INVALID_BREED", "breedId", "Giống ngựa không có trong danh mục."));
        if (!breed.isActive() && (current == null || !current.getId().equals(breed.getId()))) {
            throw ApiException.invalid("INVALID_BREED", "breedId", "Giống ngựa này đã ngừng dùng.");
        }
        return breed;
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
