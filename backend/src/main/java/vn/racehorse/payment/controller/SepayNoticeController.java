package vn.racehorse.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.payment.dto.SepayNotice;
import vn.racehorse.payment.service.PaymentService;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The notice of a transfer from SePay (BR-225, TECH-234). It needs no session: the sender proves itself with the
 * agreed key in {@code Authorization: Apikey <key>}. An accepted notice is always confirmed with 200 so the service
 * stops repeating it.
 */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Thanh toán hóa đơn")
public class SepayNoticeController {

    private static final String SCHEME = "Apikey ";

    private final PaymentService payments;

    public SepayNoticeController(PaymentService payments) {
        this.payments = payments;
    }

    @Operation(summary = "Nhận thông báo giao dịch chuyển tiền từ SePay (xác thực bằng khóa API)")
    @PostMapping("/sepay")
    public Map<String, Boolean> notice(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody SepayNotice notice) {
        String key = authorization != null && authorization.regionMatches(true, 0, SCHEME, 0, SCHEME.length())
                ? authorization.substring(SCHEME.length()).strip() : null;
        if (!payments.keyMatches(key)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "NOTICE_REFUSED", "Thông báo không hợp lệ.");
        }
        if (notice.id() == null || notice.id().isBlank() || notice.transferAmount() == null
                || notice.transferAmount().compareTo(BigDecimal.ZERO) <= 0) {
            if (isOut(notice)) {
                return Map.of("success", true);
            }
            throw ApiException.invalid("INVALID_NOTICE", "id", "Thông báo thiếu mã giao dịch hoặc số tiền.");
        }
        payments.receive(notice);
        return Map.of("success", true);
    }

    private static boolean isOut(SepayNotice notice) {
        return notice.transferType() != null && "out".equalsIgnoreCase(notice.transferType().strip());
    }
}
