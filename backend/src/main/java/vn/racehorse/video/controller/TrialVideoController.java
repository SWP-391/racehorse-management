package vn.racehorse.video.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.racehorse.video.dto.TrialVideoContent;
import vn.racehorse.video.dto.VideoDto;
import vn.racehorse.video.service.TrialVideoService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Video chạy thử", description = "Video chạy thử của buổi tập (chỉ nhận tệp, không nhận đường dẫn)")
@RestController
@RequestMapping("/api")
public class TrialVideoController {

    private final TrialVideoService service;

    public TrialVideoController(TrialVideoService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách video của buổi tập")
    @GetMapping("/sessions/{id}/videos")
    public List<VideoDto> list(@PathVariable Long id) {
        return service.list(id).stream().map(VideoDto::of).toList();
    }

    /** Only a file is accepted; a {@code url} parameter is refused (BR-108). */
    @Operation(summary = "Tải video lên")
    @PostMapping(value = "/sessions/{id}/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VideoDto upload(@PathVariable Long id, @RequestParam(name = "file", required = false) MultipartFile file,
                           @RequestParam(name = "url", required = false) String url) {
        return VideoDto.of(service.upload(id, file, url));
    }

    /** A link instead of a file, sent as JSON or form fields, is refused the same way (BR-108). */
    @Operation(summary = "Từ chối video gửi bằng đường dẫn")
    @PostMapping("/sessions/{id}/videos")
    @ResponseStatus(HttpStatus.CREATED)
    public VideoDto uploadWithoutFile(@PathVariable Long id, @RequestParam(name = "url", required = false) String url) {
        return VideoDto.of(service.upload(id, null, url));
    }

    /** Plays the file; byte ranges are served by Spring for {@link Resource} bodies so the player can seek. */
    @Operation(summary = "Phát video")
    @GetMapping("/videos/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id) {
        TrialVideoContent c = service.content(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(c.video().getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .cacheControl(CacheControl.noStore())
                .body(c.resource());
    }

    @Operation(summary = "Xóa video")
    @DeleteMapping("/videos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
