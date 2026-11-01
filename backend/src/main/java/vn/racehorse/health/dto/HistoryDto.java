package vn.racehorse.health.dto;

import vn.racehorse.health.entity.HealthStatus;

import java.time.Instant;

public record HistoryDto(HealthStatus from, HealthStatus to, String reason, String changedBy, Instant changedAt) {
}
