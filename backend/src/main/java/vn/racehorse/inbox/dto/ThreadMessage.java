package vn.racehorse.inbox.dto;

import java.time.Instant;

/** A message of a chat; feedback is true for the first message of a feedback conversation (shown highlighted). */
public record ThreadMessage(Long id, Long conversationId, Long authorId, String authorName, String text, Instant sentAt,
                            boolean mine, boolean feedback) {
}
