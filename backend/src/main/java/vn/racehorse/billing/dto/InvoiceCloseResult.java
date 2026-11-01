package vn.racehorse.billing.dto;

import java.math.BigDecimal;

public record InvoiceCloseResult(String month, int invoices, BigDecimal total) {
}
