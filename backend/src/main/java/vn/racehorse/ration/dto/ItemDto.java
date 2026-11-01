package vn.racehorse.ration.dto;

import java.math.BigDecimal;

public record ItemDto(String type, Long supplyItemId, String supplyItemName, String unit, boolean active,
                      BigDecimal quantity) {
}
