package vn.racehorse.billing.dto;

import java.util.List;

public record BillingPriceList(BillingPriceDailyFeeView dailyFee, List<BillingPriceFeeTypeView> feeTypes) {
}
