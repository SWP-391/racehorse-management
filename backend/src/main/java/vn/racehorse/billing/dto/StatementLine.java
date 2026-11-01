package vn.racehorse.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementLine(LocalDate date, String description, BigDecimal quantity, BigDecimal unitPrice,
                   BigDecimal amount, String reason) {
}
