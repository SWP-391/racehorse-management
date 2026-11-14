package vn.racehorse.payment.dto;

import java.math.BigDecimal;

/** What the owner needs to make the transfer without the QR (BR-218). */
public record TransferInfo(String bankCode, String accountNumber, String accountName, BigDecimal amount,
                           String content) {
}
