package vn.racehorse.notification.dto;

import vn.racehorse.notification.entity.Notification;

import java.time.Instant;

public record NotificationDto(Long id, String message, String link, Instant createdAt, boolean read) {
    public static NotificationDto of(Notification n) {
        return new NotificationDto(n.getId(), n.getMessage(), n.getLink(), n.getCreatedAt(), n.getReadAt() != null);
    }
}
