package vn.racehorse.training.dto;

import java.time.Instant;

public record TrainingVersionDto(int versionNo, String reason, String createdBy, Instant createdAt) {
}
