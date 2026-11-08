package vn.racehorse.simulation.dto;

import vn.racehorse.simulation.entity.Measure;
import vn.racehorse.simulation.entity.TelemetryThreshold;

import java.math.BigDecimal;
import java.time.Instant;

public record SimulationThresholdDto(Measure measure, BigDecimal limit, Instant setAt) {
    public static SimulationThresholdDto of(TelemetryThreshold t) {
        return new SimulationThresholdDto(t.getMeasure(), t.getLimit(), t.getSetAt());
    }
}
