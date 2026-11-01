package vn.racehorse.stock.dto;

import vn.racehorse.stock.service.StockForecastService;

/** A medicine as the Veterinarian sees it: quantity and state, never a price (BR-182, AC-344). */
public record MedicineDto(Long id, String name, String unit, long quantity, StockForecastService.State state) {
}
