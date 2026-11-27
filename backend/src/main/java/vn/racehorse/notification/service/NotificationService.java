package vn.racehorse.notification.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.CareRole;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.notification.entity.Notification;
import vn.racehorse.notification.repository.NotificationRepository;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;

import java.util.List;
import java.util.Objects;

/** In-app notifications only, no email or SMS (NFR-006, TECH-019). Users see only their own. */
@Service
public class NotificationService {

    private static final int LIMIT = 50;

    private final NotificationRepository notifications;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final ClubClock clock;

    public NotificationService(NotificationRepository notifications, UserAccountRepository accounts,
                               AccessGuard access, ClubClock clock) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.access = access;
        this.clock = clock;
    }

    /** One notification per active account holding the role, written in the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyRole(Role role, String message, String link) {
        for (UserAccount account : accounts.findByRoleOrderByDisplayNameAsc(role)) {
            if (account.isActive()) {
                notifications.save(new Notification(account.getId(), message, link, clock.now()));
            }
        }
    }

    /** Like {@link #notifyRole} but skips an account that already has an unread notice with the same link; returns the accounts that got one. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> notifyRoleOnce(Role role, String message, String link) {
        List<Long> notified = new java.util.ArrayList<>();
        for (UserAccount account : accounts.findByRoleOrderByDisplayNameAsc(role)) {
            if (account.isActive()
                    && !notifications.existsByRecipientIdAndLinkAndReadAtIsNull(account.getId(), link)) {
                notifications.save(new Notification(account.getId(), message, link, clock.now()));
                notified.add(account.getId());
            }
        }
        return notified;
    }

    /**
     * The one person who gets the Veterinarian notices of a horse (BR-264, TECH-F5): the substitute while the return date
     * has not come, else the main Veterinarian, else the Club Manager. An inactive person is skipped.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long veterinarianOf(Horse horse) {
        CareRole vet = CareRole.VETERINARIAN;
        UserAccount temp = vet.temp(horse);
        if (temp != null && temp.isActive() && vet.until(horse) != null && clock.today().isBefore(vet.until(horse))) {
            return temp.getId();
        }
        UserAccount main = vet.main(horse);
        if (main != null && main.isActive()) {
            return main.getId();
        }
        return accounts.findByRoleOrderByDisplayNameAsc(Role.CLUB_MANAGER).stream().filter(UserAccount::isActive)
                .map(UserAccount::getId).findFirst().orElse(null);
    }

    /** A notice that used to go to every Veterinarian, now to the Veterinarian of the horse; returns the recipient. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long notifyVeterinarianOf(Horse horse, String message, String link) {
        Long to = veterinarianOf(horse);
        if (to != null) {
            notifications.save(new Notification(to, message, link, clock.now()));
        }
        return to;
    }

    /** As {@link #notifyVeterinarianOf} but not again while the person has an unread notice with the same link; null then. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long notifyVeterinarianOfOnce(Horse horse, String message, String link) {
        Long to = veterinarianOf(horse);
        if (to == null || notifications.existsByRecipientIdAndLinkAndReadAtIsNull(to, link)) {
            return null;
        }
        notifications.save(new Notification(to, message, link, clock.now()));
        return to;
    }

    /** One notification to one account, written in the caller's transaction (BR-116, TECH-063). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyAccount(Long accountId, String message, String link) {
        notifications.save(new Notification(accountId, message, link, clock.now()));
    }

    @Transactional(readOnly = true)
    public List<Notification> mine() {
        AccountPrincipal user = access.current();
        return notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(user.id(), PageRequest.of(0, LIMIT));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByRecipientIdAndReadAtIsNull(access.current().id());
    }

    @Transactional
    public Notification markRead(Long id) {
        AccountPrincipal user = access.current();
        Notification n = notifications.findById(id).orElseThrow(() -> ApiException.notFound("thông báo"));
        if (!Objects.equals(n.getRecipientId(), user.id())) {
            throw ApiException.forbidden();
        }
        n.markRead(clock.now());
        return n;
    }
}
