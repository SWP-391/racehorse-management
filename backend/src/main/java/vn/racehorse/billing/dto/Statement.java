package vn.racehorse.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Statement(String month, boolean closed, Long ownerId, String ownerName, BigDecimal total,
                        List<StatementHorseGroup> horses, List<StatementCategoryTotal> categories, Long invoiceId,
                        String invoiceNumber, java.time.Instant closedAt, LocalDate dailyFeeThrough) {
}
