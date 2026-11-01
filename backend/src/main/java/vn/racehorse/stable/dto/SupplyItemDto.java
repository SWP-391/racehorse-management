package vn.racehorse.stable.dto;

import vn.racehorse.stable.entity.SupplyCategory;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stock.dto.StockForecast;
import vn.racehorse.stock.service.StockForecastService;

import java.math.BigDecimal;

/**
 * What one viewer sees of an item: the Club Manager has the stock, the price and the forecast; a Groom has the
 * catalog only, no quantity and no price (AC-231). Absent fields are left out of the JSON.
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record SupplyItemDto(Long id, String name, SupplyCategory category, String unit, Long unitId, boolean active, Long quantity,
                      BigDecimal unitPrice, Long daysOfCover, StockForecastService.State state, Long dailyUse,
                      Long expectedQuantity) {
    public static SupplyItemDto catalog(SupplyItem i) {
        return new SupplyItemDto(i.getId(), i.getName(), i.getCategory(), i.getUnit(), i.getUnitId(), i.isActive(), null, null, null, null,
                null, null);
    }

    public static SupplyItemDto full(SupplyItem i, StockForecast f) {
        return new SupplyItemDto(i.getId(), i.getName(), i.getCategory(), i.getUnit(), i.getUnitId(), i.isActive(), i.getQuantity(),
                i.getUnitPrice(), f == null ? null : f.daysOfCover(), f == null ? null : f.state(),
                f == null ? null : f.dailyUse(), f == null ? null : f.expected());
    }
}
