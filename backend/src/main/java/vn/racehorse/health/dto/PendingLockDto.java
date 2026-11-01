package vn.racehorse.health.dto;

import java.time.Instant;

public record PendingLockDto(String reason, String requestedBy, Instant requestedAt, Long sessionId) {
}
