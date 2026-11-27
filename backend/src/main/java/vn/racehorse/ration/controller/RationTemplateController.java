package vn.racehorse.ration.controller;

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
import vn.racehorse.ration.dto.TemplateDto;
import vn.racehorse.ration.dto.TemplateRequest;
import vn.racehorse.ration.service.RationTemplateService;

import java.util.List;

@Tag(name = "Mẫu khẩu phần", description = "Mẫu khẩu phần của Bác sĩ thú y")
@RestController
@RequestMapping("/api/ration-templates")
public class RationTemplateController {

    private final RationTemplateService service;

    public RationTemplateController(RationTemplateService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách mẫu khẩu phần")
    @GetMapping
    public List<TemplateDto> list() {
        return service.list().stream().map(TemplateDto::of).toList();
    }

    @Operation(summary = "Tạo mẫu khẩu phần")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateDto create(@RequestBody TemplateRequest request) {
        return TemplateDto.of(service.create(request));
    }

    @Operation(summary = "Sửa mẫu khẩu phần")
    @PutMapping("/{id}")
    public TemplateDto update(@PathVariable Long id, @RequestBody TemplateRequest request) {
        return TemplateDto.of(service.update(id, request));
    }

    @Operation(summary = "Xác nhận mẫu khẩu phần (bỏ dấu chờ xác nhận)")
    @PostMapping("/{id}/confirm")
    public TemplateDto confirm(@PathVariable Long id) {
        return TemplateDto.of(service.confirm(id));
    }

    @Operation(summary = "Ngừng dùng mẫu khẩu phần (không xóa)")
    @PostMapping("/{id}/deactivate")
    public TemplateDto deactivate(@PathVariable Long id) {
        return TemplateDto.of(service.deactivate(id));
    }
}
