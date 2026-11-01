package vn.racehorse.veterinary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code supplyItemId} and {@code quantity} name the medicine taken from stock (BR-172); {@code product} is free text for a hoof check only. */
public record CareCompletionInput(LocalDate dateDone, String performer, String product, String batch, String note,
                              Long supplyItemId, BigDecimal quantity) {
    public CareCompletionInput(LocalDate dateDone, String performer, String product, String batch, String note) {
        this(dateDone, performer, product, batch, note, null, null);
    }
}
