package vn.racehorse.stable.dto;

import java.math.BigDecimal;

/** {@code quantity} is only here to be refused on an edit (QUANTITY_BY_MOVEMENT); a new item uses openingQuantity. */
public record SupplyItemInput(String name, String category, Long unitId, BigDecimal price, BigDecimal openingQuantity,
                        BigDecimal quantity, Boolean active) {
}
