package vn.racehorse.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record StatementCategoryGroup(String category, BigDecimal total, List<StatementLine> lines) {
}
