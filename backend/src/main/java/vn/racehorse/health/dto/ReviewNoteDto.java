package vn.racehorse.health.dto;

import java.time.Instant;

public record ReviewNoteDto(String author, String note, Instant createdAt) {
}
