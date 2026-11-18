package vn.racehorse.inbox.dto;

import java.time.Instant;

public record MessageDto(Long id, Long authorId, String authorName, String text, Instant sentAt, boolean mine) {
}
