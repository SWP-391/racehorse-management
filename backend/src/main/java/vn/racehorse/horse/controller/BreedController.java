package vn.racehorse.horse.controller;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.horse.dto.BreedDto;
import vn.racehorse.horse.dto.BreedRequest;
import vn.racehorse.horse.service.BreedService;

import java.util.List;

@Tag(name = "Giống ngựa", description = "Danh mục giống ngựa do Quản lý CLB quản lý")
@RestController
@RequestMapping("/api/breeds")
public class BreedController {

    private final BreedService service;

    public BreedController(BreedService service) {
        this.service = service;
    }

    @Operation(summary = "Danh mục giống ngựa")
    @GetMapping
    public List<BreedDto> list() {
        return service.list().stream().map(BreedDto::of).toList();
    }

    @Operation(summary = "Thêm giống ngựa")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BreedDto add(@Valid @RequestBody BreedRequest request) {
        return BreedDto.of(service.add(request.name()));
    }

    @Operation(summary = "Đổi tên hoặc ngừng dùng giống ngựa")
    @PutMapping("/{id}")
    public BreedDto update(@PathVariable Long id, @Valid @RequestBody BreedRequest request) {
        return BreedDto.of(service.update(id, request.name(), request.active()));
    }
}
