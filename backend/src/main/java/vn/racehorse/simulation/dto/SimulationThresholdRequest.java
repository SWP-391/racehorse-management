package vn.racehorse.simulation.dto;

import vn.racehorse.simulation.entity.Measure;

import java.math.BigDecimal;

public record SimulationThresholdRequest(Measure measure, BigDecimal limit) {
}
