package vn.racehorse.simulation.dto;

import vn.racehorse.simulation.entity.TelemetryAlert;
import vn.racehorse.simulation.entity.TelemetryThreshold;
import vn.racehorse.simulation.service.TelemetrySource.Sample;
import vn.racehorse.training.service.TrainingEnums.DataSource;

import java.util.List;

public record SimulationLive(boolean active, Long sessionId, DataSource dataSource, String label, Integer elapsedSeconds,
                   Sample current, List<Sample> recent, List<TelemetryAlert> alerts,
                   List<TelemetryThreshold> thresholds, SimulationSummary summary) {
}
