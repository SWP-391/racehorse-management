package vn.racehorse.stable.dto;

import jakarta.validation.constraints.DecimalMax;
import vn.racehorse.common.validation.Bounds;

import java.math.BigDecimal;

/** {@code quantity} is accepted only to be refused on an edit (QUANTITY_BY_MOVEMENT, BR-171). */
public record ItemRequest(String name, String category, Long unitId,
                          @DecimalMax(value = Bounds.PRICE_MAX, message = Bounds.MAX_MESSAGE) BigDecimal price,
                          @DecimalMax(value = Bounds.QUANTITY_MAX, message = Bounds.MAX_MESSAGE) BigDecimal openingQuantity,
                          BigDecimal quantity, Boolean active) {
    public SupplyItemInput input() {
        return new SupplyItemInput(name, category, unitId, price, openingQuantity, quantity, active);
    }
}
