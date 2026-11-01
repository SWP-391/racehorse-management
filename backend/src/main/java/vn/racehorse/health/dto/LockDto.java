package vn.racehorse.health.dto;

import vn.racehorse.health.entity.LockKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LockDto(Long id, LockKind kind, String reason, String placedBy, Instant placedAt,
                      LocalDate reviewDate, List<ReviewNoteDto> reviewNotes) {
}
