package vn.racehorse.health.dto;

import vn.racehorse.health.entity.Readiness;

public record ReadinessDto(Long horseId, Readiness readiness) {
}
