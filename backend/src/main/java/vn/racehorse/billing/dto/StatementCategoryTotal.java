package vn.racehorse.billing.dto;

import java.math.BigDecimal;

/** The total of one category across all horses of a statement, so a category filter needs no sum on the client (BR-203). */
public record StatementCategoryTotal(String category, BigDecimal total) {
}
