package vn.racehorse.stock.dto;

import jakarta.validation.constraints.DecimalMax;
import vn.racehorse.common.validation.Bounds;

import java.math.BigDecimal;

public record ReceiptRequest(@DecimalMax(value = Bounds.QUANTITY_MAX, message = Bounds.MAX_MESSAGE) BigDecimal quantity,
                             String note, Long expectedReceiptId) {
}
