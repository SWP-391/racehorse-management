package vn.racehorse.inbox.dto;

import java.time.Instant;
import java.util.List;

/** A conversation with its messages in time order. */
public record ConversationDetail(Long id, String subject, Long horseId, String horseName, String state, String with,
                                 String stateBy, Instant stateAt, List<MessageDto> messages, String kind,
                                 String threadKey) {
}
