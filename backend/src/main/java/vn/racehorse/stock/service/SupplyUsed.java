package vn.racehorse.stock.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Published in the transaction that writes a use line, for the billing change to subscribe to (the stock never
 * imports billing). {@code refType}/{@code refId} name the care event or chore entry the use belongs to.
 */
public record SupplyUsed(Long horseId, Source source, Long supplyItemId, long quantity, BigDecimal unitPrice,
                         LocalDate date, String refType, Long refId) {

    public enum Source { CARE, FEED, FEED_BACKFILL }
}
