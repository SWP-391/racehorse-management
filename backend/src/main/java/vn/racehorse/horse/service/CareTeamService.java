package vn.racehorse.horse.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.dto.CarePersonLoad;
import vn.racehorse.horse.dto.UnassignedHorse;
import vn.racehorse.horse.entity.CareRole;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The care team of a horse: the three people chosen at take-in, change of one person, move of one horse or of all
 * horses of a person, and the substitutes of the Veterinarian and the Groom (BR-256 to BR-259, TECH-F1, TECH-F2).
 * The Head Trainer keeps its own hand-over and loan in {@link HorseTrainerService}; this service calls it for that
 * role so the audit entries and notices stay the same. The limit of 10 is {@link CareTeamLimit}.
 */
@Service
public class CareTeamService {

    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClubClock clock;
    private final CareTeamLimit limit;
    private final HorseTrainerService trainers;

    public CareTeamService(HorseRepository horses, UserAccountRepository accounts, AccessGuard access,
                           AuditService audit, NotificationService notifications, ClubClock clock,
                           CareTeamLimit limit, HorseTrainerService trainers) {
        this.horses = horses;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.limit = limit;
        this.trainers = trainers;
    }

    // ---- choosing people -----------------------------------------------------------------------------------

    private static String fieldOf(CareRole role) {
        return switch (role) {
            case HEAD_TRAINER -> "trainerId";
            case VETERINARIAN -> "veterinarianId";
            case GROOM -> "groomId";
        };
    }

    /** An active person of the role, else 400 {@code INVALID_<ROLE>} on the field of the role. */
    public UserAccount requirePerson(CareRole role, Long id) {
        if (role == CareRole.HEAD_TRAINER) {
            return trainers.requireTrainerAccount(id, fieldOf(role));
        }
        if (id == null) {
            throw requiredError(List.of(role));
        }
        return accounts.findById(id).filter(a -> a.isActive() && a.getRole() == role.role())
                .orElseThrow(() -> ApiException.invalid("INVALID_" + role.name(), fieldOf(role),
                        role.label() + " phải là tài khoản đang hoạt động đúng vai trò."));
    }

    /** 400 {@code CARE_TEAM_REQUIRED} naming every missing role (AC-580). */
    public static ApiException requiredError(List<CareRole> missing) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        for (CareRole r : missing) {
            fields.put(fieldOf(r), "Bắt buộc chọn " + r.label() + ".");
            labels.add(r.label());
        }
        return new ApiException(HttpStatus.BAD_REQUEST, "CARE_TEAM_REQUIRED",
                "Bắt buộc chọn " + String.join(" và ", labels) + " phụ trách ngựa.", fields);
    }

    /** The Veterinarian and the Groom of a take-in: both mandatory, both checked, the limit asked for each (BR-256). */
    public UserAccount[] requireTeamForTakeIn(UserAccount trainer, Long vetId, Long groomId) {
        List<CareRole> missing = new ArrayList<>();
        if (vetId == null) {
            missing.add(CareRole.VETERINARIAN);
        }
        if (groomId == null) {
            missing.add(CareRole.GROOM);
        }
        if (!missing.isEmpty()) {
            throw requiredError(missing);
        }
        UserAccount vet = requirePerson(CareRole.VETERINARIAN, vetId);
        UserAccount groom = requirePerson(CareRole.GROOM, groomId);
        limit.requireRoom(CareRole.HEAD_TRAINER, trainer, 1);
        limit.requireRoom(CareRole.VETERINARIAN, vet, 1);
        limit.requireRoom(CareRole.GROOM, groom, 1);
        return new UserAccount[]{vet, groom};
    }

    // ---- change and move one horse -----------------------------------------------------------------------------

    /** Changes the main person of one role of one horse (PERM-068; the trainer also by PERM-045). */
    @Transactional
    public Horse change(Long horseId, CareRole role, Long toUserId) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CARE_TEAM);
        if (role == null) {
            throw ApiException.invalid("ROLE_REQUIRED", "role", "Bắt buộc chọn vai trò.");
        }
        if (role == CareRole.HEAD_TRAINER) {
            return trainers.handOver(actor, horseId, toUserId);
        }
        Horse horse = horses.findByIdForUpdate(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        HorseService.requireTakenIn(horse);
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", HorseService.ARCHIVED);
        }
        UserAccount next = requirePerson(role, toUserId);
        UserAccount before = role.main(horse);
        if (before != null && before.getId().equals(next.getId())) {
            return horse;
        }
        limit.requireRoom(role, next, 1);
        role.setMain(horse, next, clock.now());
        recordChange(actor, horse, role, before, next);
        return horse;
    }

    /**
     * Moves every taken-in, not archived horse of a person to another person of the same role, atomically: one
     * check of the limit for all of them, refused whole when it would pass 10 (AC-585).
     */
    @Transactional
    public List<Horse> moveAll(Long fromUserId, CareRole role, Long toUserId) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CARE_TEAM);
        if (role == null) {
            throw ApiException.invalid("ROLE_REQUIRED", "role", "Bắt buộc chọn vai trò.");
        }
        UserAccount from = accounts.findById(fromUserId).orElseThrow(() -> ApiException.notFound("tài khoản"));
        if (from.getRole() != role.role()) {
            throw ApiException.invalid("INVALID_" + role.name(), "role", "Người này không giữ vai trò " + role.label() + ".");
        }
        UserAccount to = requirePerson(role, toUserId);
        if (from.getId().equals(to.getId())) {
            throw ApiException.invalid("SAME_PERSON", fieldOf(role), "Chọn một người khác để chuyển ngựa.");
        }
        List<Horse> moving = mainHorsesOf(role, from.getId());
        if (moving.isEmpty()) {
            return moving;
        }
        limit.requireRoom(role, to, moving.size());
        for (Horse horse : moving) {
            role.setMain(horse, to, clock.now());
            recordChange(actor, horse, role, from, to);
        }
        return moving;
    }

    private void recordChange(AccountPrincipal actor, Horse horse, CareRole role, UserAccount before, UserAccount next) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("role", role.name());
        details.put("from", before == null ? null : before.getId());
        details.put("to", next.getId());
        audit.record(actor, role == CareRole.HEAD_TRAINER ? "HORSE_TRAINER_CHANGED" : "HORSE_CARE_PERSON_CHANGED",
                "HORSE", horse.getId(), details);
        String what = "Ngựa \"" + horse.getName() + "\"";
        if (before != null) {
            notifications.notifyAccount(before.getId(), what + " không còn do bạn phụ trách.", "/horses/" + horse.getId());
        }
        notifications.notifyAccount(next.getId(), what + " được giao cho bạn phụ trách.", "/horses/" + horse.getId());
    }

    public List<Horse> mainHorsesOf(CareRole role, Long personId) {
        return switch (role) {
            case HEAD_TRAINER -> horses.mainOfTrainer(personId);
            case VETERINARIAN -> horses.mainOfVet(personId);
            case GROOM -> horses.mainOfGroom(personId);
        };
    }

    // ---- substitutes ------------------------------------------------------------------------------------------

    /** Sets a substitute Veterinarian or Groom until a return date (BR-258); not counted toward the limit (BR-257). */
    @Transactional
    public Horse substitute(Long horseId, CareRole role, Long userId, LocalDate until) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CARE_TEAM);
        requireSubstituteRole(role);
        Horse horse = horses.findByIdForUpdate(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        HorseService.requireTakenIn(horse);
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", HorseService.ARCHIVED);
        }
        UserAccount person = requirePerson(role, userId);
        if (role.main(horse) != null && role.main(horse).getId().equals(person.getId())) {
            throw ApiException.invalid("INVALID_" + role.name(), fieldOf(role), "Người thay thế phải khác người phụ trách chính.");
        }
        if (until == null || !until.isAfter(clock.today())) {
            throw ApiException.invalid("INVALID_UNTIL", "until", "Ngày trả ngựa phải sau hôm nay.");
        }
        UserAccount replaced = role.temp(horse);
        role.setTemp(horse, person, until, clock.now());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("role", role.name());
        details.put("from", role.main(horse) == null ? null : role.main(horse).getId());
        details.put("to", person.getId());
        details.put("until", until.toString());
        details.put("replaced", replaced == null ? null : replaced.getId());
        audit.record(actor, "HORSE_SUBSTITUTE_SET", "HORSE", horse.getId(), details);
        String what = "Ngựa \"" + horse.getName() + "\"";
        notifications.notifyAccount(person.getId(),
                what + " được giao cho bạn phụ trách thay đến trước ngày " + until + ".", "/horses/" + horseId);
        if (role.main(horse) != null) {
            notifications.notifyAccount(role.main(horse).getId(),
                    what + " được giao tạm cho " + person.getDisplayName() + " đến trước ngày " + until + ".",
                    "/horses/" + horseId);
        }
        if (replaced != null && !replaced.getId().equals(person.getId())) {
            notifications.notifyAccount(replaced.getId(), what + " không còn do bạn phụ trách thay.", "/horses/" + horseId);
        }
        return horse;
    }

    /** Ends a substitute early; the horse is back with the main person at once. */
    @Transactional
    public Horse endSubstitute(Long horseId, CareRole role) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CARE_TEAM);
        requireSubstituteRole(role);
        Horse horse = horses.findByIdForUpdate(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        HorseService.requireTakenIn(horse);
        if (role.temp(horse) != null) {
            endSubstitute(horse, role, actor, "EARLY");
        }
        return horse;
    }

    /** Clears every substitute whose return date has come, once each, and tells both people (BR-258). */
    @Transactional
    public int endExpiredSubstitutes() {
        LocalDate today = clock.today();
        int ended = 0;
        for (Horse h : horses.findByTempVetUntilLessThanEqual(today)) {
            endSubstitute(h, CareRole.VETERINARIAN, null, "EXPIRED");
            ended++;
        }
        for (Horse h : horses.findByTempGroomUntilLessThanEqual(today)) {
            endSubstitute(h, CareRole.GROOM, null, "EXPIRED");
            ended++;
        }
        return ended;
    }

    private void endSubstitute(Horse horse, CareRole role, AccountPrincipal actor, String reason) {
        UserAccount temporary = role.temp(horse);
        role.setTemp(horse, null, null, clock.now());
        Map<String, Object> details = Map.of("role", role.name(), "reason", reason, "temporary", temporary.getId());
        if (actor == null) {
            audit.recordSystem("HORSE_SUBSTITUTE_ENDED", "HORSE", horse.getId(), details);
        } else {
            audit.record(actor, "HORSE_SUBSTITUTE_ENDED", "HORSE", horse.getId(), details);
        }
        String what = "Ngựa \"" + horse.getName() + "\"";
        notifications.notifyAccount(temporary.getId(), what + " đã trả về người phụ trách chính, bạn không còn phụ trách thay.",
                "/horses/" + horse.getId());
        if (role.main(horse) != null) {
            notifications.notifyAccount(role.main(horse).getId(), what + " đã trở lại do bạn phụ trách.",
                    "/horses/" + horse.getId());
        }
    }

    private static void requireSubstituteRole(CareRole role) {
        if (role != CareRole.VETERINARIAN && role != CareRole.GROOM) {
            throw ApiException.invalid("ROLE_NOT_SUPPORTED", "role",
                    "Huấn luyện viên được giao tạm bằng thao tác riêng của huấn luyện viên.");
        }
    }

    // ---- reading ------------------------------------------------------------------------------------------------

    /** The active and inactive people of a role with their main load, for the choosers (PERM-063, PERM-068). */
    @Transactional(readOnly = true)
    public List<CarePersonLoad> loads(CareRole role) {
        access.requireAny(Permission.MANAGE_CARE_TEAM, Permission.TAKE_IN_HORSES);
        return accounts.findByRoleOrderByDisplayNameAsc(role.role()).stream()
                .filter(UserAccount::isActive)
                .map(a -> {
                    long n = limit.count(role, a.getId());
                    return new CarePersonLoad(a.getId(), a.getDisplayName(), a.isActive(), n, CareTeamLimit.MAX,
                            n >= CareTeamLimit.MAX);
                }).toList();
    }

    /** Taken-in, not archived horses that still miss a person: the warning of the Club Manager (BR-260). */
    @Transactional(readOnly = true)
    public List<UnassignedHorse> unassigned() {
        access.require(Permission.MANAGE_CARE_TEAM);
        return horses.withoutFullCareTeam().stream().map(h -> {
            List<CareRole> missing = new ArrayList<>();
            for (CareRole r : CareRole.values()) {
                if (r.main(h) == null) {
                    missing.add(r);
                }
            }
            return new UnassignedHorse(h.getId(), h.getName(), missing);
        }).toList();
    }

    public static boolean same(UserAccount a, UserAccount b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }
}
