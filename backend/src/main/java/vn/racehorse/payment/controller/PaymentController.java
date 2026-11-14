package vn.racehorse.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.payment.dto.AssignRequest;
import vn.racehorse.payment.dto.PaymentView;
import vn.racehorse.payment.dto.TransferRow;
import vn.racehorse.payment.dto.UnpaidInvoice;
import vn.racehorse.payment.service.PaymentService;

import java.util.List;

/** The owner's payment of an invoice and the Club Manager's payment lists (PERM-059, PERM-060, TECH-236 to TECH-238). */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Thanh toán hóa đơn")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @Operation(summary = "Mở thanh toán của một hóa đơn của chính chủ ngựa (tạo mã thanh toán lần đầu)")
    @PostMapping("/invoices/{id}/open")
    public PaymentView open(@PathVariable Long id) {
        return payments.open(id);
    }

    @Operation(summary = "Trạng thái thanh toán của một hóa đơn của chính chủ ngựa")
    @GetMapping("/invoices/{id}")
    public PaymentView status(@PathVariable Long id) {
        return payments.status(id);
    }

    @Operation(summary = "Các khoản tiền đã nhận và áp dụng vào hóa đơn")
    @GetMapping("/received")
    public PageResponse<TransferRow> received(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return Paging.page(payments.received(), PageParams.of(page, size, null));
    }

    @Operation(summary = "Các khoản chuyển tiền chưa khớp hóa đơn")
    @GetMapping("/unmatched")
    public PageResponse<TransferRow> unmatched(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return Paging.page(payments.unmatched(), PageParams.of(page, size, null));
    }

    @Operation(summary = "Hóa đơn chưa thanh toán, để gán khoản chuyển tiền")
    @GetMapping("/unpaid-invoices")
    public List<UnpaidInvoice> unpaidInvoices() {
        return payments.unpaidInvoices();
    }

    @Operation(summary = "Gán một khoản chuyển tiền chưa khớp cho một hóa đơn chưa thanh toán (bắt buộc lý do)")
    @PostMapping("/transfers/{id}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        payments.assign(id, request.invoiceId(), request.reason());
    }
}
