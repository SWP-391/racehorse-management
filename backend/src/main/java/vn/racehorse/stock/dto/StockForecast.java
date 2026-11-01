package vn.racehorse.stock.dto;

import vn.racehorse.stock.service.StockForecastService.State;

public record StockForecast(Long itemId, long dailyUse, long expected, long careDemand, Long daysOfCover, State state) {
}
