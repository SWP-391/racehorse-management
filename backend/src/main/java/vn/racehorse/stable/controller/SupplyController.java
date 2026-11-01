package vn.racehorse.stable.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.common.paging.Search;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.Permission;
import vn.racehorse.stable.dto.ItemRequest;
import vn.racehorse.stable.dto.ProposalDto;
import vn.racehorse.stable.dto.ProposalRequest;
import vn.racehorse.stable.dto.RejectRequest;
import vn.racehorse.stable.dto.SupplyItemDto;
import vn.racehorse.stable.entity.ProposalState;
import vn.racehorse.stable.entity.SupplyCategory;
import vn.racehorse.stable.entity.SupplyItem;
import vn.racehorse.stable.service.SupplyService;
import vn.racehorse.stock.dto.StockForecast;
import vn.racehorse.stock.service.StockForecastService;

import java.util.Map;

@Tag(name = "Vật tư", description = "Vật tư, đề xuất nhập thêm và duyệt")
@RestController
@RequestMapping("/api")
public class SupplyController {

    private final SupplyService service;
    private final AccessGuard access;
    private final StockForecastService forecasts;

    public SupplyController(SupplyService service, AccessGuard access, StockForecastService forecasts) {
        this.service = service;
        this.access = access;
        this.forecasts = forecasts;
    }

    private SupplyItemDto dto(SupplyItem item) {
        return Permission.VIEW_STOCK.allows(access.current().role()) && Permission.MANAGE_STOCK.allows(access.current().role())
                ? SupplyItemDto.full(item, forecasts.forecast().get(item.getId())) : SupplyItemDto.catalog(item);
    }

    @Operation(summary = "Danh sách vật tư")
    @GetMapping("/supplies")
    public PageResponse<SupplyItemDto> list(@RequestParam(required = false) SupplyCategory category,
                                      @RequestParam(required = false) Boolean active,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size,
                                      @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        boolean manager = Permission.MANAGE_STOCK.allows(access.current().role());
        Map<Long, StockForecast> forecast = manager ? forecasts.forecast() : Map.of();
        return Paging.page(service.list().stream()
                .filter(i -> category == null || i.getCategory() == category)
                .filter(i -> active == null || i.isActive() == active)
                .filter(i -> Search.matches(params.q(), i.getName()))
                .map(i -> manager ? SupplyItemDto.full(i, forecast.get(i.getId())) : SupplyItemDto.catalog(i)).toList(), params);
    }

    @Operation(summary = "Tạo vật tư")
    @PostMapping("/supplies")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplyItemDto create(@Valid @RequestBody ItemRequest request) {
        return dto(service.create(request.input()));
    }

    @Operation(summary = "Cập nhật vật tư")
    @PutMapping("/supplies/{id}")
    public SupplyItemDto update(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return dto(service.update(id, request.input()));
    }

    @Operation(summary = "Đề xuất nhập thêm vật tư")
    @PostMapping("/supplies/{id}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalDto propose(@PathVariable Long id, @Valid @RequestBody ProposalRequest request) {
        return ProposalDto.of(service.propose(id, request.quantity(), request.note()));
    }

    @Operation(summary = "Danh sách đề xuất nhập thêm")
    @GetMapping("/restock-proposals")
    public PageResponse<ProposalDto> proposals(@RequestParam(required = false) ProposalState state,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(service.listProposals().stream()
                .filter(p -> state == null || p.getState() == state)
                .filter(p -> Search.matches(params.q(), p.getItem().getName()))
                .map(ProposalDto::of).toList(), params);
    }

    @Operation(summary = "Duyệt đề xuất nhập thêm")
    @PostMapping("/restock-proposals/{id}/approve")
    public ProposalDto approve(@PathVariable Long id) {
        return ProposalDto.of(service.approve(id));
    }

    @Operation(summary = "Từ chối đề xuất nhập thêm")
    @PostMapping("/restock-proposals/{id}/reject")
    public ProposalDto reject(@PathVariable Long id, @RequestBody(required = false) RejectRequest request) {
        return ProposalDto.of(service.reject(id, request == null ? null : request.reason()));
    }
}
