package vn.racehorse.horse.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.common.ClubClock;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.security.AccountPrincipal;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Who writes the training data of a horse: one assigned Head Trainer, optionally a temporary one until a return date
 * (BR-152, BR-153, BR-155, BR-156, TECH-129 to TECH-133). Every Head Trainer reads every horse; only the effective
 * trainer writes. The effective trainer is computed from the dates, so rights switch on the return day even when
 * the job has not run yet.
 */
@Service
public class HorseTrainerService {

    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClubClock clock;

    public HorseTrainerService(HorseRepository horses, UserAccountRepository accounts, AuditService audit,
                               NotificationService notifications, ClubClock clock) {
        this.horses = horses;
        this.accounts = accounts;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    public LocalDate today() {
        return clock.toClubTime(clock.now()).toLocalDate();
    }

    /** The temporary trainer while today is before the return date, otherwise the main trainer (BR-156). */
    public UserAccount effectiveTrainer(Horse horse, LocalDate today) {
        if (horse.getTempTrainer() != null && horse.getTempUntil() != null && today.isBefore(horse.getTempUntil())) {
            return horse.getTempTrainer();
        }
        return horse.getMainTrainer();
    }

    public UserAccount effectiveTrainer(Horse horse) {
        return effectiveTrainer(horse, today());
    }

    @Transactional(readOnly = true)
    public Long effectiveTrainerId(Long horseId) {
        UserAccount trainer = effectiveTrainer(find(horseId));
        return trainer == null ? null : trainer.getId();
    }

    /** The effective trainer of a horse by id; null when the horse has none (reads for DTOs). */
    @Transactional(readOnly = true)
    public UserAccount effectiveTrainerOf(Long horseId) {
        return effectiveTrainer(find(horseId));
    }

    /**
     * The one rule for "a horse is assigned to this Head Trainer on this date": the main trainer, or the temporary
     * trainer until the return date (BR-153, BR-156, BR-157). Writes (`requireWriter`) and the timetable both ask it.
     */
    public boolean isAssignedOn(Horse horse, Long trainerId, LocalDate date) {
        UserAccount trainer = effectiveTrainer(horse, date);
        return trainer != null && Objects.equals(trainer.getId(), trainerId);
    }

    public boolean canWrite(Horse horse, AccountPrincipal user) {
        return user != null && isAssignedOn(horse, user.id(), today());
    }

    /**
     * Refuses unless the caller is the effective trainer of the horse (BR-153, AC-289, AC-291). The permission of
     * the action has already been checked by {@code AccessGuard.require}.
     */
    @Transactional(readOnly = true)
    public void requireWriter(AccountPrincipal user, Long horseId) {
        Horse horse = find(horseId);
        if (!canWrite(horse, user)) {
            UserAccount trainer = effectiveTrainer(horse);
            String who = trainer == null ? "chưa có HLV" : "HLV " + trainer.getDisplayName();
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED_TRAINER",
                    "Ngựa này do " + who + " phụ trách; bạn chỉ có quyền xem.");
        }
    }

    /** An active Head Trainer, else `INVALID_TRAINER` on the given field (AC-287). */
    public UserAccount requireTrainerAccount(Long trainerId, String field) {
        if (trainerId == null) {
            throw ApiException.invalid("TRAINER_REQUIRED", field, "Bắt buộc chọn HLV phụ trách.");
        }
        return java.util.Optional.ofNullable(trainerId).flatMap(accounts::findById)
                .filter(a -> a.isActive() && a.getRole() == Role.HEAD_TRAINER)
                .orElseThrow(() -> ApiException.invalid("INVALID_TRAINER", field,
                        "HLV phụ trách phải là tài khoản Huấn luyện viên đang hoạt động."));
    }

    // ---- assign, hand over, lend (PERM-045) ----------------------------------------------------------------

    /** Registration: the trainer chosen by the Club Manager becomes the main trainer, no handover notice (BR-152). */
    void setAtRegistration(Horse horse, UserAccount trainer) {
        horse.setMainTrainer(trainer, clock.now());
    }

    /** Changes the main trainer: plans, sessions and history stay as they are; both trainers are told (BR-155). */
    @Transactional
    public Horse handOver(AccountPrincipal actor, Long horseId, Long trainerId) {
        Horse horse = find(horseId);
        requireNotArchived(horse);
        UserAccount next = requireTrainerAccount(trainerId, "trainerId");
        UserAccount before = horse.getMainTrainer();
        if (before != null && before.getId().equals(next.getId())) {
            return horse;
        }
        UserAccount effectiveBefore = effectiveTrainer(horse);
        horse.setMainTrainer(next, clock.now());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", before == null ? null : before.getId());
        details.put("to", next.getId());
        audit.record(actor, "HORSE_TRAINER_CHANGED", "HORSE", horse.getId(), details);
        String what = "Ngựa \"" + horse.getName() + "\"";
        if (before != null) {
            notifications.notifyAccount(before.getId(), what + " không còn do bạn phụ trách.", "/horses/" + horseId);
        }
        notifications.notifyAccount(next.getId(), what + " được giao cho bạn phụ trách.", "/horses/" + horseId);
        if (horse.getTempTrainer() != null && effectiveBefore != null && before != null
                && !effectiveBefore.getId().equals(before.getId())) {
            // A temporary trainer is still in charge; the new main trainer takes over when the loan ends.
            notifications.notifyAccount(horse.getTempTrainer().getId(),
                    what + ": HLV chính đã đổi, bạn vẫn phụ trách tạm đến hết thời hạn.", "/horses/" + horseId);
        }
        return horse;
    }

    /** Lends the horse to another trainer until the return date (BR-156). */
    @Transactional
    public Horse lend(AccountPrincipal actor, Long horseId, Long trainerId, LocalDate until) {
        Horse horse = find(horseId);
        requireNotArchived(horse);
        UserAccount temporary = requireTrainerAccount(trainerId, "trainerId");
        if (horse.getMainTrainer() != null && horse.getMainTrainer().getId().equals(temporary.getId())) {
            throw ApiException.invalid("INVALID_TRAINER", "trainerId", "HLV tạm phải khác HLV chính.");
        }
        if (until == null || !until.isAfter(today())) {
            throw ApiException.invalid("INVALID_UNTIL", "until", "Ngày trả ngựa phải sau hôm nay.");
        }
        UserAccount replaced = horse.getTempTrainer();
        horse.lendTo(temporary, until, clock.now());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", horse.getMainTrainer() == null ? null : horse.getMainTrainer().getId());
        details.put("to", temporary.getId());
        details.put("until", until.toString());
        details.put("replaced", replaced == null ? null : replaced.getId());
        audit.record(actor, "HORSE_TEMP_TRAINER_SET", "HORSE", horse.getId(), details);
        String what = "Ngựa \"" + horse.getName() + "\"";
        notifications.notifyAccount(temporary.getId(),
                what + " được giao cho bạn phụ trách tạm đến trước ngày " + until + ".", "/horses/" + horseId);
        if (horse.getMainTrainer() != null) {
            notifications.notifyAccount(horse.getMainTrainer().getId(),
                    what + " được giao tạm cho " + temporary.getDisplayName() + " đến trước ngày " + until + ".",
                    "/horses/" + horseId);
        }
        if (replaced != null && !replaced.getId().equals(temporary.getId())) {
            notifications.notifyAccount(replaced.getId(), what + " không còn do bạn phụ trách tạm.",
                    "/horses/" + horseId);
        }
        return horse;
    }

    /** Ends a loan early; the horse is back with the main trainer at once. */
    @Transactional
    public Horse endLoanEarly(AccountPrincipal actor, Long horseId) {
        Horse horse = find(horseId);
        if (horse.getTempTrainer() == null) {
            return horse;
        }
        endLoan(horse, actor, "EARLY");
        return horse;
    }

    /** Clears every loan whose return date has come, once each, and tells both trainers (TECH-133, AC-286). */
    @Transactional
    public int endExpiredLoans() {
        LocalDate today = today();
        List<Horse> due = horses.findByTempUntilLessThanEqual(today);
        for (Horse horse : due) {
            endLoan(horse, null, "EXPIRED");
        }
        return due.size();
    }

    private void endLoan(Horse horse, AccountPrincipal actor, String reason) {
        UserAccount temporary = horse.getTempTrainer();
        horse.endLoan(clock.now());
        Map<String, Object> details = Map.of("reason", reason, "temporary", temporary.getId());
        if (actor == null) {
            audit.recordSystem("HORSE_TEMP_TRAINER_ENDED", "HORSE", horse.getId(), details);
        } else {
            audit.record(actor, "HORSE_TEMP_TRAINER_ENDED", "HORSE", horse.getId(), details);
        }
        String what = "Ngựa \"" + horse.getName() + "\"";
        notifications.notifyAccount(temporary.getId(), what + " đã trả về HLV chính, bạn không còn phụ trách tạm.",
                "/horses/" + horse.getId());
        if (horse.getMainTrainer() != null) {
            notifications.notifyAccount(horse.getMainTrainer().getId(), what + " đã trở lại do bạn phụ trách.",
                    "/horses/" + horse.getId());
        }
    }

    // ---- notices (BR-157, TECH-137) -------------------------------------------------------------------------

    /**
     * A notice to the effective trainer of the horse; with no trainer it goes to the Club Managers with a prefix,
     * so a notice is never lost.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyTrainer(Long horseId, String message, String link) {
        UserAccount trainer = effectiveTrainer(find(horseId));
        if (trainer != null) {
            notifications.notifyAccount(trainer.getId(), message, link);
        } else {
            notifications.notifyRole(Role.CLUB_MANAGER, "Ngựa chưa có HLV phụ trách. " + message, link);
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private Horse find(Long horseId) {
        return horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
    }

    private static void requireNotArchived(Horse horse) {
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", HorseService.ARCHIVED);
        }
    }
}
