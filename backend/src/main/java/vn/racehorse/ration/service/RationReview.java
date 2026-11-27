package vn.racehorse.ration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.racehorse.ration.entity.RationTag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.inbox.service.InboxService;
import vn.racehorse.events.DomainEvents.HealthStatusChanged;
import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.health.entity.HorseHealth;
import vn.racehorse.health.repository.HorseHealthRepository;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.notification.service.NotificationService;
import vn.racehorse.ration.entity.RationVersion;
import vn.racehorse.ration.repository.RationVersionRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Derived "ration needs review" flag and the Veterinarian's notice (BR-249, BR-250, TECH-D2). The flag is computed
 * from the current health status and the current ration tag, so it is never stale. A horse without a ration is not
 * flagged. No permission check: callers decide who may ask.
 */
@Service
public class RationReview {

    private static final Logger log = LoggerFactory.getLogger(RationReview.class);

    private final RationVersionRepository versions;
    private final HorseHealthRepository health;
    private final HorseRepository horses;
    private final NotificationService notifications;
    private final InboxService inbox;
    private final UserAccountRepository accounts;

    public RationReview(RationVersionRepository versions, HorseHealthRepository health, HorseRepository horses,
                        NotificationService notifications, InboxService inbox, UserAccountRepository accounts) {
        this.inbox = inbox;
        this.accounts = accounts;
        this.versions = versions;
        this.health = health;
        this.horses = horses;
        this.notifications = notifications;
    }

    public boolean needsReview(RationVersion current, HealthStatus status) {
        return current != null && !RationFit.matches(status, current.getConditionTag());
    }

    @Transactional(readOnly = true)
    public Set<Long> needingReview(Collection<Long> horseIds) {
        Set<Long> out = new HashSet<>();
        if (horseIds.isEmpty()) {
            return out;
        }
        Map<Long, RationTag> latest = new LinkedHashMap<>();
        for (Object[] row : versions.latestTags(horseIds)) {
            latest.put(((Number) row[0]).longValue(), RationTag.valueOf((String) row[1]));
        }
        for (HorseHealth hh : health.findAllById(latest.keySet())) {
            if (!RationFit.matches(hh.getStatus(), latest.get(hh.getHorseId()))) {
                out.add(hh.getHorseId());
            }
        }
        return out;
    }

    /**
     * After the status change commits, in its own transaction: a failed notice must not undo the status change or the
     * end of a session (BR-249).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStatusChanged(HealthStatusChanged event) {
        try {
            Horse horse = horses.findById(event.horseId()).orElse(null);
            RationVersion current = versions.findFirstByHorseIdOrderByVersionNoDesc(event.horseId()).orElse(null);
            if (horse != null) {
                notice(horse, current, HealthStatus.valueOf(event.to()));
            }
        } catch (RuntimeException e) {
            log.warn("Ration notice for horse {} failed: {}", event.horseId(), e.toString());
        }
    }

    /** Tells the Veterinarians when the horse's ration does not fit its status; once while the notice stays unread. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notice(Horse horse, RationVersion current, HealthStatus status) {
        if (!needsReview(current, status)) {
            return;
        }
        String message = "Khẩu phần của ngựa \"" + horse.getName() + "\" cần xem lại: tình trạng " + status.label()
                + " không khớp loại khẩu phần " + current.getConditionTag().label() + ".";
        Long vet = notifications.notifyVeterinarianOfOnce(horse, message, "/horses/" + horse.getId() + "/ration");
        java.util.List<Long> told = vet == null ? java.util.List.of() : java.util.List.of(vet);
        // The same Veterinarians, once, in the inbox under the one Club Manager's name (BR-253).
        if (!told.isEmpty()) {
            accounts.findByRoleOrderByDisplayNameAsc(Role.CLUB_MANAGER).stream().filter(a -> a.isActive()).findFirst()
                    .ifPresent(manager -> inbox.startAutomatic(manager.getId(), told, horse.getId(),
                            "Khẩu phần của ngựa \"" + horse.getName() + "\" cần xem lại", message));
        }
    }
}
