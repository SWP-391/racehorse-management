package vn.racehorse.notification.dto;

import java.util.List;

/** The caller's notifications, newest first, one page at a time; "unread" is the count over all of them. */
public record Inbox(long unread, List<NotificationDto> items, int page, int size, long total) {
}
