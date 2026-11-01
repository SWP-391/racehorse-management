package vn.racehorse.health.dto;

import vn.racehorse.health.entity.HealthStatus;

import java.util.Map;

/** Counts over the whole herd for the health page's cards and chart, so they do not depend on the page shown. */
public record HealthSummary(long total, Map<HealthStatus, Long> byStatus, long locked) {
}
