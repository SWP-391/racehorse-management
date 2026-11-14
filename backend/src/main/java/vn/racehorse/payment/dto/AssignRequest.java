package vn.racehorse.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Assign an unmatched transfer to an unpaid invoice, with a reason (BR-222). */
public record AssignRequest(
        @NotNull(message = "Bắt buộc chọn hóa đơn.") Long invoiceId,
        @Size(max = 500, message = "Lý do tối đa 500 ký tự.") String reason) {
}
