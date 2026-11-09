package vn.racehorse.simulation.dto;

import vn.racehorse.simulation.entity.Measure;
import vn.racehorse.simulation.entity.TelemetryAlert;
import vn.racehorse.simulation.service.SimulationService;
import vn.racehorse.training.service.TrainingEnums.DataSource;

import java.math.BigDecimal;
import java.time.Instant;

public record AlertDto(Long sessionId, Measure measure, BigDecimal limit, BigDecimal value, int offsetSecond,
                       Instant raisedAt, DataSource dataSource, String label) {
    public static AlertDto of(TelemetryAlert a) {
        return new AlertDto(a.getSessionId(), a.getMeasure(), a.getLimit(), a.getValue(), a.getOffsetSecond(),
                a.getRaisedAt(), a.getDataSource(), SimulationService.LABEL);
    }
}
