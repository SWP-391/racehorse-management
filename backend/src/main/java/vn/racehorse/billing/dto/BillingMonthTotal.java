package vn.racehorse.billing.dto;

import java.math.BigDecimal;

/** The total charged in one month, for the billing overview (BR-209, BR-180). */
public record BillingMonthTotal(String month, BigDecimal total) {
}
