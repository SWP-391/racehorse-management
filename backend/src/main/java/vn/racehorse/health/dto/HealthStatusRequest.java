package vn.racehorse.health.dto;

import jakarta.validation.constraints.Size;
import vn.racehorse.health.entity.HealthStatus;

public record HealthStatusRequest(HealthStatus status, @Size(max = 2000) String reason) {
}
