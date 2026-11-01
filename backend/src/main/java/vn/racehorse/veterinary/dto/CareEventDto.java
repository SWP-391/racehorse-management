package vn.racehorse.veterinary.dto;

import vn.racehorse.veterinary.entity.CareEvent;
import vn.racehorse.veterinary.entity.CareEventKind;

import java.time.Instant;
import java.time.LocalDate;

/** No price: the Veterinarian never sees one (BR-182); the quantity is the dose taken from stock. */
public record CareEventDto(CareEventKind kind, LocalDate eventDate, String performer, String product,
                           String batch, String note, LocalDate previousDue, LocalDate newDue, String by,
                           Instant at, Long supplyItemId, Long quantity) {
    public static CareEventDto of(CareEvent e) {
        return new CareEventDto(e.getKind(), e.getEventDate(), e.getPerformer(), e.getProduct(), e.getBatch(),
                e.getNote(), e.getPreviousDue(), e.getNewDue(), e.getBy().getDisplayName(), e.getAt(),
                e.getSupplyItemId(), e.getQuantity());
    }
}
