package vn.racehorse.billing.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the Club Manager's billing overview draws, all summed by the service (the client sums no money, BR-180). `scope` is
 * a month `yyyy-MM` or `all`; `months` is the trailing twelve months ending at the chosen month (every month listed, zero
 * included, so the bars are always the same number), or every month with charges (at most the last 24) for `all`.
 */
public record BillingOverview(String scope, BigDecimal total, int owners, List<BillingMonthTotal> months,
                              List<StatementCategoryTotal> categories, List<BillingOwnerTotal> topOwners) {
}
