package vn.racehorse.simulation.dto;

import vn.racehorse.training.service.TrainingEnums.DataSource;

import java.util.List;

public record LiveDto(boolean active, Long sessionId, DataSource dataSource, String label, Integer elapsedSeconds,
                      SampleDto current, List<SampleDto> recent, List<AlertDto> alerts,
                      List<ThresholdLineDto> thresholds, SimulationSummary summary) {
}
