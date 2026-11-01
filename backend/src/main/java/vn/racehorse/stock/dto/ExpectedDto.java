package vn.racehorse.stock.dto;

import vn.racehorse.stock.entity.ExpectedReceipt;

import java.time.LocalDate;

public record ExpectedDto(Long id, LocalDate expectedDate, long quantity, ExpectedReceipt.State state) {
    public static ExpectedDto of(ExpectedReceipt e) {
        return new ExpectedDto(e.getId(), e.getExpectedDate(), e.getQuantity(), e.getState());
    }
}
