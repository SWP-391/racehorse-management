package vn.racehorse.inbox.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.horse.service.HorseTrainerService;
import vn.racehorse.inbox.dto.HorseOption;
import vn.racehorse.inbox.dto.Recipient;
import vn.racehorse.veterinary.repository.ExaminationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Who may write to whom (BR-228, BR-229, TECH-241). One place used both to offer recipients and to refuse a start, so
 * the form and the server cannot disagree. Default deny: a pair that no clause names is refused.
 *
 * <pre>
 *  any role --> Club Manager role          Club Manager --> any active account
 *  Owner  <-> Head Trainer of the owner's horse (temporary trainer while one is set)
 *  Owner  <-> Veterinarian who recorded an examination of the owner's horse
 *  Veterinarian <-> Head Trainer responsible for the horse
 *  Groom  <-> any active Head Trainer
 * </pre>
 */
@Component
public class MessagingPolicy {

    static final String NOT_ALLOWED = "Bạn không thể nhắn tin cho người này.";

    private final HorseRepository horses;
    private final HorseTrainerService trainers;
    private final ExaminationRepository examinations;
    private final UserAccountRepository accounts;

    public MessagingPolicy(HorseRepository horses, HorseTrainerService trainers, ExaminationRepository examinations,
                           UserAccountRepository accounts) {
        this.horses = horses;
        this.trainers = trainers;
        this.examinations = examinations;
        this.accounts = accounts;
    }

    private static boolean bound(Role a, Role b) {
        return pair(a, b, Role.HORSE_OWNER, Role.HEAD_TRAINER) || pair(a, b, Role.HORSE_OWNER, Role.VETERINARIAN)
                || pair(a, b, Role.VETERINARIAN, Role.HEAD_TRAINER);
    }

    private static boolean pair(Role a, Role b, Role x, Role y) {
        return (a == x && b == y) || (a == y && b == x);
    }

    private static ApiException notAllowed() {
        return new ApiException(HttpStatus.FORBIDDEN, "RECIPIENT_NOT_ALLOWED", NOT_ALLOWED,
                Map.of("recipient", NOT_ALLOWED));
    }

    private static ApiException horseRefused(String code, String message) {
        return ApiException.invalid(code, "horseId", message);
    }

    /**
     * Refuses unless {@code sender} may start a conversation with {@code recipient} (null: the Club Manager role)
     * about {@code horse} (null: none). The relation is checked here only, when the conversation starts (BR-230).
     */
    public void check(UserAccount sender, UserAccount recipient, Horse horse) {
        if (horse != null) {
            if (horse.isArchived()) {
                throw horseRefused("HORSE_ARCHIVED", "Không thể chọn ngựa đã lưu trữ.");
            }
            if (sender.getRole() == Role.HORSE_OWNER && !Objects.equals(horse.getOwner().getId(), sender.getId())) {
                throw horseRefused("HORSE_NOT_LINKED", "Ngựa này không thuộc về bạn.");
            }
        }
        if (recipient == null) {
            if (sender.getRole() == Role.CLUB_MANAGER) {
                throw notAllowed();
            }
            return;
        }
        if (Objects.equals(sender.getId(), recipient.getId()) || !recipient.isActive()) {
            throw notAllowed();
        }
        Role a = sender.getRole();
        Role b = recipient.getRole();
        if (a == Role.CLUB_MANAGER || b == Role.CLUB_MANAGER || pair(a, b, Role.GROOM, Role.HEAD_TRAINER)) {
            return;
        }
        if (!bound(a, b)) {
            throw notAllowed();
        }
        if (horse == null) {
            throw horseRefused("HORSE_REQUIRED", "Bắt buộc chọn ngựa.");
        }
        UserAccount other = a == Role.HORSE_OWNER ? recipient : sender;
        UserAccount person = a == Role.HORSE_OWNER ? sender : recipient;
        if (pair(a, b, Role.HORSE_OWNER, Role.HEAD_TRAINER)) {
            requireOwn(horse, person);
            requireTrainer(horse, other);
        } else if (pair(a, b, Role.HORSE_OWNER, Role.VETERINARIAN)) {
            requireOwn(horse, person);
            if (!examinations.existsByHorseIdAndRecordedById(horse.getId(), other.getId())) {
                throw notAllowed();
            }
        } else {
            UserAccount trainer = a == Role.HEAD_TRAINER ? sender : recipient;
            requireTrainer(horse, trainer);
        }
    }

    private void requireOwn(Horse horse, UserAccount owner) {
        if (!Objects.equals(horse.getOwner().getId(), owner.getId())) {
            throw horseRefused("HORSE_NOT_LINKED", "Ngựa này không thuộc về chủ ngựa đã chọn.");
        }
    }

    private void requireTrainer(Horse horse, UserAccount trainer) {
        UserAccount effective = trainers.effectiveTrainer(horse);
        if (effective == null || !Objects.equals(effective.getId(), trainer.getId())) {
            throw notAllowed();
        }
    }

    /** The horses the sender may name in the form. */
    public List<HorseOption> horseOptions(UserAccount sender) {
        List<Horse> all = switch (sender.getRole()) {
            case HORSE_OWNER -> horses.findByOwnerIdOrderByNameAsc(sender.getId());
            case HEAD_TRAINER -> horses.findAllByOrderByNameAsc().stream()
                    .filter(h -> isTrainerOf(h, sender)).toList();
            case VETERINARIAN, CLUB_MANAGER -> horses.findAllByOrderByNameAsc();
            case GROOM -> List.<Horse>of();
        };
        return all.stream().filter(h -> !h.isArchived()).map(h -> new HorseOption(h.getId(), h.getName())).toList();
    }

    private boolean isTrainerOf(Horse horse, UserAccount trainer) {
        UserAccount effective = trainers.effectiveTrainer(horse);
        return effective != null && Objects.equals(effective.getId(), trainer.getId());
    }

    /** The recipients offered to the sender for the chosen horse (null: none); every one of them passes {@link #check}. */
    public List<Recipient> offered(UserAccount sender, Horse horse) {
        List<Recipient> out = new ArrayList<>();
        if (sender.getRole() != Role.CLUB_MANAGER) {
            out.add(new Recipient(null, "Quản lý CLB", Role.CLUB_MANAGER, true));
        }
        List<UserAccount> candidates = switch (sender.getRole()) {
            case CLUB_MANAGER -> accounts.findAllByOrderByDisplayNameAsc();
            case GROOM -> accounts.findByRoleOrderByDisplayNameAsc(Role.HEAD_TRAINER);
            case HEAD_TRAINER -> {
                List<UserAccount> list = new ArrayList<>(accounts.findByRoleOrderByDisplayNameAsc(Role.GROOM));
                if (horse != null) {
                    list.add(horse.getOwner());
                    list.addAll(accounts.findByRoleOrderByDisplayNameAsc(Role.VETERINARIAN));
                }
                yield list;
            }
            case VETERINARIAN -> {
                List<UserAccount> list = new ArrayList<>();
                if (horse != null) {
                    UserAccount trainer = trainers.effectiveTrainer(horse);
                    if (trainer != null) {
                        list.add(trainer);
                    }
                    list.add(horse.getOwner());
                }
                yield list;
            }
            case HORSE_OWNER -> {
                List<UserAccount> list = new ArrayList<>();
                if (horse != null) {
                    UserAccount trainer = trainers.effectiveTrainer(horse);
                    if (trainer != null) {
                        list.add(trainer);
                    }
                    list.addAll(accounts.findAllById(examinations.examinerIds(horse.getId())));
                }
                yield list;
            }
        };
        candidates.stream().filter(UserAccount::isActive).distinct()
                .sorted(Comparator.comparing(UserAccount::getDisplayName))
                .forEach(c -> {
                    if (allowed(sender, c, horse)) {
                        out.add(new Recipient(c.getId(), c.getDisplayName(), c.getRole(), false));
                    }
                });
        return out;
    }

    private boolean allowed(UserAccount sender, UserAccount recipient, Horse horse) {
        try {
            check(sender, recipient, horse);
            return true;
        } catch (ApiException refused) {
            return false;
        }
    }
}
