package vn.racehorse.stable.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.stable.dto.SupplyUnitRequest;
import vn.racehorse.stable.dto.SupplyUnitResponse;
import vn.racehorse.stable.service.SupplyUnitService;

import java.util.List;

/** The catalog of units of supplies (PERM-055). There is no delete: a unit in use is never removed (BR-194). */
@Tag(name = "Đơn vị vật tư", description = "Danh mục đơn vị tính của vật tư")
@RestController
@RequestMapping("/api/supply-units")
public class SupplyUnitController {

    private final SupplyUnitService service;

    public SupplyUnitController(SupplyUnitService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách đơn vị kèm số vật tư đang dùng")
    @GetMapping
    public List<SupplyUnitResponse> list() {
        return service.list();
    }

    @Operation(summary = "Thêm đơn vị")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplyUnitResponse add(@RequestBody SupplyUnitRequest r) {
        return SupplyUnitResponse.of(service.add(r.name()), 0);
    }

    @Operation(summary = "Đổi tên đơn vị")
    @PutMapping("/{id}")
    public SupplyUnitResponse rename(@PathVariable Long id, @RequestBody SupplyUnitRequest r) {
        return SupplyUnitResponse.of(service.rename(id, r.name()), 0);
    }

    @Operation(summary = "Ngừng dùng đơn vị")
    @PostMapping("/{id}/deactivate")
    public SupplyUnitResponse deactivate(@PathVariable Long id) {
        return SupplyUnitResponse.of(service.setActive(id, false), 0);
    }

    @Operation(summary = "Dùng lại đơn vị")
    @PostMapping("/{id}/activate")
    public SupplyUnitResponse activate(@PathVariable Long id) {
        return SupplyUnitResponse.of(service.setActive(id, true), 0);
    }
}
