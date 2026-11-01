package vn.racehorse.audit.dto;

import java.time.Instant;

public record AuditEntryDto(Long id, Long actorId, String actorName, Instant occurredAt, String occurredAtClub,
                            String action, String targetType, Long targetId, String details) {
}
