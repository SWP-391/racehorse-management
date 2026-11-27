package vn.racehorse.notification.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.racehorse.notification.entity.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable page);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    /** Unread notices of one person that point at one link, cleared when the target is opened (BR-233). */
    List<Notification> findByRecipientIdAndLinkAndReadAtIsNull(Long recipientId, String link);

    /** True when the person already has the same message unread (same link, so one open notice per horse). */
    boolean existsByRecipientIdAndLinkAndReadAtIsNull(Long recipientId, String link);
}
