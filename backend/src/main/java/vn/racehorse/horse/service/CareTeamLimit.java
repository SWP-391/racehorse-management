package vn.racehorse.horse.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.CareRole;
import vn.racehorse.horse.repository.HorseRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * THE limit of 10 horses per Head Trainer, Veterinarian and Groom (BR-256, BR-257, TECH-F2). It counts taken-in, not
 * archived horses by main person only: a temporary trainer, a substitute and a waiting, refused or withdrawn horse do
 * not count; the Horse Owner has no limit. Called by take-in, change of person, move and bulk move, inside their
 * transaction: the row of the target person is locked first ({@code select ... for update}), so two simultaneous
 * assignments take turns and the second one sees the first one's horse (AC-616 of the care team test).
 * A person already above the limit keeps every horse and is only refused new ones (AC-589).
 */
@Service
public class CareTeamLimit {

    public static final int MAX = 10;

    private final HorseRepository horses;
    private final UserAccountRepository accounts;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    public CareTeamLimit(HorseRepository horses, UserAccountRepository accounts) {
        this.horses = horses;
        this.accounts = accounts;
    }

    public long count(CareRole role, Long personId) {
        return switch (role) {
            case HEAD_TRAINER -> horses.countAsTrainer(personId);
            case VETERINARIAN -> horses.countAsVet(personId);
            case GROOM -> horses.countAsGroom(personId);
        };
    }

    /**
     * Locks the person and refuses with 409 {@code CARE_TEAM_FULL} when {@code adding} more horses would pass the
     * limit; the message names the person and the limit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRoom(CareRole role, UserAccount person, int adding) {
        UserAccount locked = accounts.findByIdForUpdate(person.getId()).orElse(person);
        // The caller read the person before this lock; a deactivation that committed in between must not be missed.
        em.refresh(locked);
        if (!locked.isActive()) {
            throw ApiException.invalid("INVALID_" + role.name(), role == CareRole.HEAD_TRAINER ? "trainerId"
                    : role == CareRole.VETERINARIAN ? "veterinarianId" : "groomId",
                    role.label() + " phải là tài khoản đang hoạt động đúng vai trò.");
        }
        long now = count(role, locked.getId());
        if (now + adding > MAX) {
            Map<String, String> extra = new LinkedHashMap<>();
            extra.put("person", locked.getDisplayName());
            extra.put("personId", String.valueOf(locked.getId()));
            extra.put("role", role.name());
            extra.put("limit", String.valueOf(MAX));
            extra.put("count", String.valueOf(now));
            String message = role.label() + " " + locked.getDisplayName() + " đang phụ trách " + now
                    + " ngựa; mỗi người tối đa " + MAX + " ngựa."
                    + (adding > 1 ? " Không thể nhận thêm " + adding + " ngựa." : " Không thể nhận thêm ngựa.");
            throw new ApiException(HttpStatus.CONFLICT, "CARE_TEAM_FULL", message, extra);
        }
    }
}
