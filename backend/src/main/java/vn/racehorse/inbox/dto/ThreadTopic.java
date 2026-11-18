package vn.racehorse.inbox.dto;

import java.time.Instant;

/** A conversation inside a chat: its subject, horse and state; the chat shows it as a heading before its messages. */
public record ThreadTopic(Long id, String subject, String horseName, String kind, String state, String stateBy,
                          Instant stateAt) {
}
