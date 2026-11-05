package vn.racehorse.incident.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.common.paging.Search;
import vn.racehorse.incident.dto.CloseRequest;
import vn.racehorse.incident.dto.IncidentDto;
import vn.racehorse.incident.dto.IncidentHorseOption;
import vn.racehorse.incident.entity.IncidentPhoto;
import vn.racehorse.incident.entity.IncidentState;
import vn.racehorse.incident.service.IncidentService;

import java.util.List;

@Tag(name = "Sự cố", description = "Báo cáo sự cố về ngựa kèm ảnh")
@RestController
@RequestMapping("/api")
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @Operation(summary = "Ngựa có thể chọn khi báo sự cố")
    @GetMapping("/horse-options")
    public List<IncidentHorseOption> horseOptions() {
        return service.horseOptions();
    }

    @Operation(summary = "Gửi báo cáo sự cố")
    @PostMapping(value = "/incidents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentDto send(@RequestParam(required = false) Long horseId,
                            @RequestParam(required = false) String description,
                            @RequestParam(required = false) String bodyRegion,
                            @RequestParam(name = "photos", required = false) List<MultipartFile> photos) {
        return IncidentDto.of(service.send(horseId, description, bodyRegion, photos));
    }

    @Operation(summary = "Danh sách báo cáo sự cố")
    @GetMapping("/incidents")
    public PageResponse<IncidentDto> list(@RequestParam(required = false) IncidentState state,
                                          @RequestParam(required = false) Long horseId,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(service.list().stream()
                .filter(d -> state == null || d.report().getState() == state)
                .filter(d -> horseId == null || horseId.equals(d.report().getHorseId()))
                .filter(d -> Search.matches(params.q(), d.horse().getName(), d.report().getDescription()))
                .map(IncidentDto::of).toList(), params);
    }

    @Operation(summary = "Chi tiết một báo cáo sự cố")
    @GetMapping("/incidents/{id}")
    public IncidentDto open(@PathVariable Long id) {
        return IncidentDto.of(service.open(id));
    }

    @Operation(summary = "Đóng báo cáo sự cố")
    @PostMapping("/incidents/{id}/close")
    public IncidentDto close(@PathVariable Long id, @RequestBody CloseRequest request) {
        return IncidentDto.of(service.close(id, request.note(), request.examinationId()));
    }

    @Operation(summary = "Tải ảnh của báo cáo sự cố")
    @GetMapping("/incidents/{id}/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id, @PathVariable Long photoId) {
        IncidentPhoto p = service.photo(id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + p.getFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(p.getContent());
    }
}
