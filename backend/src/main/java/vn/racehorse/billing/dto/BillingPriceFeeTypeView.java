package vn.racehorse.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record BillingPriceFeeTypeView(Long id, String name, boolean active, BigDecimal currentPrice, List<BillingPriceRow> prices) {
}
