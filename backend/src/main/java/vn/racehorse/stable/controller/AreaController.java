package vn.racehorse.stable.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.stable.dto.AreaDto;
import vn.racehorse.stable.dto.AreaRequest;
import vn.racehorse.stable.service.AreaService;

import java.util.List;

@Tag(name = "Khu chuồng", description = "Danh mục khu chuồng")
@RestController
@RequestMapping("/api/areas")
public class AreaController {

    private final AreaService service;

    public AreaController(AreaService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách khu chuồng")
    @GetMapping
    public List<AreaDto> list() {
        return service.list().stream().map(AreaDto::of).toList();
    }

    @Operation(summary = "Tạo khu chuồng")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AreaDto create(@RequestBody AreaRequest request) {
        return AreaDto.of(service.create(request.name(), request.capacity()));
    }

    @Operation(summary = "Cập nhật khu chuồng")
    @PutMapping("/{id}")
    public AreaDto update(@PathVariable Long id, @RequestBody AreaRequest request) {
        return AreaDto.of(service.update(id, request.name(), request.capacity()));
    }

    @Operation(summary = "Xóa khu chuồng")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
