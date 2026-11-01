package vn.racehorse.stock.dto;

import jakarta.validation.constraints.DecimalMax;
import vn.racehorse.common.validation.Bounds;

import java.math.BigDecimal;

public record PriceRequest(@DecimalMax(value = Bounds.PRICE_MAX, message = Bounds.MAX_MESSAGE) BigDecimal price) {
}
