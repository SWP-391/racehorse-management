package vn.racehorse.billing.dto;

import java.math.BigDecimal;

/** The total charged to one owner in the scope of the billing overview. */
public record BillingOwnerTotal(Long ownerId, String ownerName, BigDecimal total) {
}
