package vn.racehorse.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record StatementHorseGroup(Long horseId, String horseName, BigDecimal total, List<StatementCategoryGroup> categories) {
}
