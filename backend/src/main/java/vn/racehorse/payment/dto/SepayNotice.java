package vn.racehorse.payment.dto;

import java.math.BigDecimal;

/** The fields of the SePay notice that the application reads (TECH-234); every other field is ignored. */
public record SepayNotice(String id, String content, String code, String transferType, BigDecimal transferAmount,
                          String accountNumber, String referenceCode, String transactionDate) {
}
