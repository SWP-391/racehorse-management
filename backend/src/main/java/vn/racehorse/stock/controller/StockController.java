package vn.racehorse.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.Permission;
import vn.racehorse.stable.entity.SupplyCategory;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stable.repository.SupplyItemRepository;
import vn.racehorse.stock.dto.AdjustmentRequest;
import vn.racehorse.stock.dto.ExpectedDto;
import vn.racehorse.stock.dto.ExpectedRequest;
import vn.racehorse.stock.dto.MedicineDto;
import vn.racehorse.stock.dto.MovementDto;
import vn.racehorse.stock.dto.PriceRequest;
import vn.racehorse.stock.dto.ReceiptRequest;
import vn.racehorse.stock.dto.StockForecast;
import vn.racehorse.stock.dto.SupplyItemPrice;
import vn.racehorse.stock.dto.WarningDto;
import vn.racehorse.stock.dto.WarningRequest;
import vn.racehorse.stock.entity.StockMovement;
import vn.racehorse.stock.service.StockForecastService;
import vn.racehorse.stock.service.StockService;
import vn.racehorse.training.service.ClubSettings;

import java.util.List;
import java.util.Map;

/** Prices, receipts, adjustments, expected receipts, the movement history, medicines for the picker and the threshold. */
@Tag(name = "Kho", description = "Giá, nhập kho, điều chỉnh, dự kiến nhập và lịch sử xuất nhập")
@RestController
@RequestMapping("/api")
public class StockController {

    private final StockService stock;
    private final StockForecastService forecasts;
    private final SupplyItemRepository items;
    private final UserAccountRepository accounts;
    private final ClubSettings settings;
    private final AccessGuard access;

    public StockController(StockService stock, StockForecastService forecasts, SupplyItemRepository items,
                           UserAccountRepository accounts, ClubSettings settings, AccessGuard access) {
        this.stock = stock;
        this.forecasts = forecasts;
        this.items = items;
        this.accounts = accounts;
        this.settings = settings;
        this.access = access;
    }

    @Operation(summary = "Đặt giá vật tư")
    @PutMapping("/supplies/{id}/price")
    public SupplyItemPrice price(@PathVariable Long id, @Valid @RequestBody PriceRequest request) {
        SupplyItem item = stock.setPrice(id, request.price());
        return new SupplyItemPrice(item.getId(), item.getUnitPrice());
    }

    @Operation(summary = "Nhập kho")
    @PostMapping("/supplies/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementDto receive(@PathVariable Long id, @Valid @RequestBody ReceiptRequest r) {
        return dto(stock.receive(id, r.quantity(), r.note(), r.expectedReceiptId()));
    }

    @Operation(summary = "Điều chỉnh tồn kho")
    @PostMapping("/supplies/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementDto adjust(@PathVariable Long id, @Valid @RequestBody AdjustmentRequest r) {
        return dto(stock.adjust(id, r.newQuantity(), r.delta(), r.reason()));
    }

    @Operation(summary = "Lịch sử xuất nhập kho")
    @GetMapping("/supplies/{id}/movements")
    public PageResponse<MovementDto> movements(@PathVariable Long id, @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return Paging.page(stock.movements(id).stream().map(this::dto).toList(), PageParams.of(page, size, null));
    }

    @Operation(summary = "Danh sách dự kiến nhập")
    @GetMapping("/supplies/{id}/expected-receipts")
    public List<ExpectedDto> expected(@PathVariable Long id) {
        return stock.expectedOf(id).stream().map(ExpectedDto::of).toList();
    }

    @Operation(summary = "Thêm dự kiến nhập")
    @PostMapping("/supplies/{id}/expected-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpectedDto expect(@PathVariable Long id, @Valid @RequestBody ExpectedRequest r) {
        return ExpectedDto.of(stock.expect(id, r.expectedDate(), r.quantity()));
    }

    @Operation(summary = "Hủy dự kiến nhập")
    @DeleteMapping("/supplies/{id}/expected-receipts/{expectedId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelExpected(@PathVariable Long id, @PathVariable Long expectedId) {
        stock.cancelExpected(id, expectedId);
    }

    /**
     * Active medicines in stock for the picker; with {@code all=true} every active medicine with its state, for the
     * Veterinarian's read-only list (PERM-050, BR-172).
     */
    @Operation(summary = "Thuốc trong kho")
    @GetMapping("/stock/medicines")
    public List<MedicineDto> medicines(@RequestParam(required = false) Boolean all) {
        access.require(Permission.VIEW_STOCK);
        Map<Long, StockForecast> forecast = forecasts.forecast();
        return items.findByActiveTrueOrderByNameAsc().stream()
                .filter(i -> i.getCategory() == SupplyCategory.MEDICINE)
                .filter(i -> Boolean.TRUE.equals(all) || i.getQuantity() > 0)
                .map(i -> new MedicineDto(i.getId(), i.getName(), i.getUnit(), i.getQuantity(),
                        forecast.get(i.getId()).state())).toList();
    }

    @Operation(summary = "Xem ngưỡng cảnh báo tồn kho")
    @GetMapping("/settings/stock-warning")
    public WarningDto warning() {
        return new WarningDto(settings.stockWarningDaysForManager());
    }

    @Operation(summary = "Đặt ngưỡng cảnh báo tồn kho")
    @PutMapping("/settings/stock-warning")
    public WarningDto setWarning(@RequestBody WarningRequest request) {
        return new WarningDto(settings.setStockWarningDays(request.days()));
    }

    private MovementDto dto(StockMovement m) {
        String actor = m.getActorId() == null ? null
                : accounts.findById(m.getActorId()).map(a -> a.getDisplayName()).orElse(null);
        return new MovementDto(m.getId(), m.getKind(), m.getDelta(), m.getBalanceAfter(), m.getUnitPrice(),
                m.getOccurredOn(), m.getRefType(), m.getRefId(), m.getReason(), actor, m.getCreatedAt());
    }
}
