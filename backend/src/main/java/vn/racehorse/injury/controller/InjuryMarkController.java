package vn.racehorse.injury.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import vn.racehorse.common.paging.Search;
import vn.racehorse.injury.dto.CancelRequest;
import vn.racehorse.injury.dto.InjuryMarkStatusRequest;
import vn.racehorse.injury.dto.MarkDto;
import vn.racehorse.injury.dto.MarkRequest;
import vn.racehorse.injury.entity.MarkStatus;
import vn.racehorse.injury.service.InjuryMarkService;

@Tag(name = "Vết thương", description = "Đánh dấu vết thương trên cơ thể ngựa")
@RestController
@RequestMapping("/api")
public class InjuryMarkController {

    private final InjuryMarkService service;

    public InjuryMarkController(InjuryMarkService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách vết thương của ngựa")
    @GetMapping("/horses/{id}/injury-marks")
    public PageResponse<MarkDto> list(@PathVariable Long id,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) Boolean cancelled) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(service.list(id).stream().map(MarkDto::of)
                .filter(m -> cancelled == null || cancelled == (m.status() == MarkStatus.CANCELLED))
                .filter(m -> Search.matches(params.q(), m.regionCode(), m.examinationDiagnosis(), m.createdBy()))
                .toList(), params);
    }

    @Operation(summary = "Đánh dấu vết thương mới")
    @PostMapping("/horses/{id}/injury-marks")
    @ResponseStatus(HttpStatus.CREATED)
    public MarkDto create(@PathVariable Long id, @RequestBody MarkRequest r) {
        return MarkDto.of(service.create(id, r.regionCode(), r.examinationId()));
    }

    @Operation(summary = "Đổi trạng thái vết thương")
    @PostMapping("/injury-marks/{id}/status")
    public MarkDto status(@PathVariable Long id, @RequestBody InjuryMarkStatusRequest r) {
        return MarkDto.of(service.changeStatus(id, r.status(), r.note()));
    }

    @Operation(summary = "Hủy đánh dấu vết thương")
    @PostMapping("/injury-marks/{id}/cancel")
    public MarkDto cancel(@PathVariable Long id, @RequestBody CancelRequest r) {
        return MarkDto.of(service.cancel(id, r.reason()));
    }

    @Operation(summary = "Xóa đánh dấu vết thương")
    @DeleteMapping("/injury-marks/{id}")
    public void delete(@PathVariable Long id) {
        service.refuseDelete(id);
    }
}
