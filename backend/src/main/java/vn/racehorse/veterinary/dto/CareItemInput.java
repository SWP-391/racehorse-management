package vn.racehorse.veterinary.dto;

import vn.racehorse.veterinary.entity.CareType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CareItemInput(CareType type, String name, Integer intervalDays, LocalDate firstDue,
                        Long plannedSupplyItemId, BigDecimal plannedQuantity) {
    public CareItemInput(CareType type, String name, Integer intervalDays, LocalDate firstDue) {
        this(type, name, intervalDays, firstDue, null, null);
    }
}
