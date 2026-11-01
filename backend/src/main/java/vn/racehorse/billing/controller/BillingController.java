package vn.racehorse.billing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.billing.dto.BillingAdjustmentRequest;
import vn.racehorse.billing.dto.BillingPriceFeeTypeOption;
import vn.racehorse.billing.dto.BillingPriceList;
import vn.racehorse.billing.dto.BillingPriceRequest;
import vn.racehorse.billing.dto.ExportFile;
import vn.racehorse.billing.dto.FeeTypeDto;
import vn.racehorse.billing.dto.FeeTypeRequest;
import vn.racehorse.billing.dto.ClosingStatusResponse;
import vn.racehorse.billing.dto.InvoiceCloseResult;
import vn.racehorse.billing.dto.InvoiceView;
import vn.racehorse.billing.dto.Statement;
import vn.racehorse.billing.dto.StatementOwnerRow;
import vn.racehorse.billing.entity.ServicePrice;
import vn.racehorse.billing.service.BillingPriceService;
import vn.racehorse.billing.service.InvoiceService;
import vn.racehorse.billing.service.StatementExportService;
import vn.racehorse.billing.service.StatementService;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;

import java.util.List;

/** Prices, statements, invoices, month closing and corrections (BR-178 to BR-183, TECH-164, 168, 170, 171). */
@Tag(name = "Thanh toán", description = "Bảng giá, bảng kê, hóa đơn, chốt tháng và điều chỉnh")
@RestController
@RequestMapping("/api")
public class BillingController {

    private final BillingPriceService prices;
    private final StatementService statements;
    private final InvoiceService invoices;
    private final StatementExportService exports;

    public BillingController(BillingPriceService prices, StatementService statements, InvoiceService invoices,
                             StatementExportService exports) {
        this.exports = exports;
        this.prices = prices;
        this.statements = statements;
        this.invoices = invoices;
    }

    // ---- price list ---------------------------------------------------------------------------------------

    @Operation(summary = "Bảng giá hiện hành")
    @GetMapping("/billing/prices")
    public BillingPriceList priceList() {
        return prices.list();
    }

    @Operation(summary = "Đặt giá mới")
    @PostMapping("/billing/prices")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingPriceList setPrice(@Valid @RequestBody BillingPriceRequest r) {
        ServicePrice.Kind kind = null;
        try {
            kind = r.kind() == null ? null : ServicePrice.Kind.valueOf(r.kind());
        } catch (IllegalArgumentException ignored) {
            // reported as a missing kind by the service
        }
        prices.setPrice(kind, r.feeTypeId(), r.amount(), r.effectiveFrom());
        return prices.list();
    }

    @Operation(summary = "Tạo loại phí")
    @PostMapping("/billing/fee-types")
    @ResponseStatus(HttpStatus.CREATED)
    public FeeTypeDto createFeeType(@RequestBody FeeTypeRequest r) {
        return FeeTypeDto.of(prices.createFeeType(r.name()));
    }

    @Operation(summary = "Đổi tên loại phí")
    @PutMapping("/billing/fee-types/{id}")
    public FeeTypeDto renameFeeType(@PathVariable Long id, @RequestBody FeeTypeRequest r) {
        return FeeTypeDto.of(prices.renameFeeType(id, r.name()));
    }

    @Operation(summary = "Ngừng dùng loại phí")
    @PostMapping("/billing/fee-types/{id}/deactivate")
    public FeeTypeDto deactivateFeeType(@PathVariable Long id) {
        return FeeTypeDto.of(prices.deactivateFeeType(id));
    }

    /** For the examination form of the Veterinarian: names only, never an amount (BR-182). */
    @Operation(summary = "Tên các loại phí khám cho form khám (không kèm số tiền)")
    @GetMapping("/exam-fee-types")
    public List<BillingPriceFeeTypeOption> examFeeTypes() {
        return prices.optionsToday();
    }

    // ---- statements and invoices --------------------------------------------------------------------------

    @Operation(summary = "Bảng kê của chủ ngựa đang đăng nhập")
    @GetMapping("/billing/my-statement")
    public Statement myStatement(@RequestParam(required = false) String month) {
        return statements.mine(month);
    }

    @Operation(summary = "Danh sách chủ ngựa kèm bảng kê")
    @GetMapping("/billing/statements")
    public List<StatementOwnerRow> owners(@RequestParam(required = false) String month) {
        return statements.owners(month);
    }

    @Operation(summary = "Bảng kê của một chủ ngựa")
    @GetMapping("/billing/statements/{ownerId}")
    public Statement statement(@PathVariable Long ownerId, @RequestParam(required = false) String month) {
        return statements.of(ownerId, month);
    }

    @Operation(summary = "Chi phí của một con ngựa")
    @GetMapping("/horses/{id}/costs")
    public Statement horseCosts(@PathVariable Long id, @RequestParam(required = false) String month) {
        return statements.ofHorse(id, month);
    }

    @Operation(summary = "Danh sách hóa đơn")
    @GetMapping("/billing/invoices")
    public PageResponse<InvoiceView> invoiceList(@RequestParam(required = false) Long ownerId,
                                                                @RequestParam(required = false) String month,
                                                                @RequestParam(required = false) Integer page,
                                                                @RequestParam(required = false) Integer size) {
        return Paging.page(invoices.list(ownerId, month), PageParams.of(page, size, null));
    }

    @Operation(summary = "Chi tiết một hóa đơn")
    @GetMapping("/billing/invoices/{id}")
    public Statement invoice(@PathVariable Long id) {
        return invoices.invoice(id);
    }

    // ---- Excel export (PERM-057, BR-204) --------------------------------------------------------------------

    private static ResponseEntity<byte[]> download(ExportFile f) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.name()).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(f.bytes());
    }

    @Operation(summary = "Xuất Excel bảng kê của mọi chủ ngựa trong tháng (Quản lý CLB)")
    @GetMapping("/billing/statements/export")
    public ResponseEntity<byte[]> exportAll(@RequestParam(required = false) String month) {
        return download(exports.allOwners(month));
    }

    @Operation(summary = "Xuất Excel bảng kê của một chủ ngựa")
    @GetMapping("/billing/statements/{ownerId}/export")
    public ResponseEntity<byte[]> exportOwner(@PathVariable Long ownerId, @RequestParam(required = false) String month) {
        return download(exports.ownerMonth(ownerId, month));
    }

    @Operation(summary = "Xuất Excel bảng kê của chủ ngựa đang đăng nhập")
    @GetMapping("/billing/my-statement/export")
    public ResponseEntity<byte[]> exportMine(@RequestParam(required = false) String month) {
        return download(exports.mine(month));
    }

    @Operation(summary = "Xuất Excel một hóa đơn")
    @GetMapping("/billing/invoices/{id}/export")
    public ResponseEntity<byte[]> exportInvoice(@PathVariable Long id) {
        return download(exports.invoice(id));
    }

    @Operation(summary = "Tháng có chốt được không, và vì sao chưa")
    @GetMapping("/billing/months/{month}/closing")
    public ClosingStatusResponse closing(@PathVariable String month) {
        return invoices.closingStatus(month);
    }

    @Operation(summary = "Chốt tháng")
    @PostMapping("/billing/months/{month}/close")
    public InvoiceCloseResult close(@PathVariable String month) {
        return invoices.close(month);
    }

    @Operation(summary = "Điều chỉnh khoản phí đã chốt")
    @PostMapping("/billing/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public void adjust(@Valid @RequestBody BillingAdjustmentRequest r) {
        invoices.adjust(r.ownerId(), r.horseId(), r.date(), r.amount(), r.reason());
    }
}
