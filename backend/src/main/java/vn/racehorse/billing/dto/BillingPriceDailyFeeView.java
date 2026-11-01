package vn.racehorse.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record BillingPriceDailyFeeView(BigDecimal currentPrice, List<BillingPriceRow> prices) {
}
