package vn.racehorse.billing.dto;

import java.math.BigDecimal;

public record StatementOwnerRow(Long ownerId, String ownerName, BigDecimal total, boolean closed, Long invoiceId) {
}
