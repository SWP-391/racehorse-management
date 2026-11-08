package vn.racehorse.simulation.dto;

import vn.racehorse.simulation.entity.Measure;

import java.math.BigDecimal;

/** A threshold as the chart draws it: the measure and its limit (BR-189). */
public record ThresholdLineDto(Measure measure, BigDecimal limit) {
}
