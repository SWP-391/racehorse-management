package vn.racehorse.inbox.dto;

import java.time.Instant;

/** One line of the inbox list: one conversation; threadKey says which chat with a person it belongs to. */
public record ConversationRow(Long id, String subject, String horseName, String state, String with,
                              String preview, Instant lastAt, long unread, String kind, String threadKey) {
}
