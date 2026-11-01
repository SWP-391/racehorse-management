package vn.racehorse.health.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.common.paging.Search;
import vn.racehorse.health.dto.HealthStatusRequest;
import vn.racehorse.health.dto.HealthSummary;
import vn.racehorse.health.dto.HorseHealthDto;
import vn.racehorse.health.dto.HorseHealthView;
import vn.racehorse.health.dto.LiftRequest;
import vn.racehorse.health.dto.LockRequest;
import vn.racehorse.health.dto.NoteRequest;
import vn.racehorse.health.dto.ReadinessDto;
import vn.racehorse.health.entity.ActivityStatus;
import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.health.entity.Readiness;
import vn.racehorse.health.service.HealthService;

import java.util.List;
import java.util.Map;

@Tag(name = "Sức khỏe", description = "Tình trạng sức khỏe, khóa tập và độ sẵn sàng của ngựa")
@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService service;

    public HealthController(HealthService service) {
        this.service = service;
    }

    @Operation(summary = "Tổng hợp sức khỏe cả đàn")
    @GetMapping("/health/summary")
    public HealthSummary summary() {
        List<HorseHealthView> herd = service.herd();
        Map<HealthStatus, Long> byStatus = new java.util.EnumMap<>(HealthStatus.class);
        for (HealthStatus s : HealthStatus.values()) {
            byStatus.put(s, herd.stream().filter(v -> v.health().getStatus() == s).count());
        }
        return new HealthSummary(herd.size(), byStatus, herd.stream().filter(v -> v.activeLock() != null).count());
    }

    @Operation(summary = "Danh sách sức khỏe cả đàn")
    @GetMapping("/health")
    public PageResponse<HorseHealthDto> herd(@RequestParam(required = false) HealthStatus status,
                                             @RequestParam(required = false) ActivityStatus activity,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(service.herd().stream()
                .filter(v -> status == null || v.health().getStatus() == status)
                .filter(v -> activity == null || v.activity() == activity)
                .filter(v -> Search.matches(params.q(), v.horse().getName(), v.horse().getRegistrationNumber()))
                .map(HorseHealthDto::of).toList(), params);
    }

    @Operation(summary = "Sức khỏe chi tiết của một con ngựa")
    @GetMapping("/horses/{id}/health")
    public HorseHealthDto detail(@PathVariable Long id) {
        return HorseHealthDto.of(service.detail(id));
    }

    @Operation(summary = "Đổi tình trạng sức khỏe")
    @PostMapping("/horses/{id}/health/status")
    public HorseHealthDto changeStatus(@PathVariable Long id, @Valid @RequestBody HealthStatusRequest request) {
        return HorseHealthDto.of(service.changeStatus(id, request.status(), request.reason()));
    }

    @Operation(summary = "Khóa tập luyện cho ngựa")
    @PostMapping("/horses/{id}/lock")
    public HorseHealthDto lock(@PathVariable Long id, @Valid @RequestBody LockRequest request) {
        return HorseHealthDto.of(service.lock(id, request.reason(), request.reviewDate()));
    }

    @Operation(summary = "Gỡ khóa tập luyện")
    @PostMapping("/horses/{id}/lock/lift")
    public HorseHealthDto lift(@PathVariable Long id, @RequestBody LiftRequest request) {
        return HorseHealthDto.of(service.lift(id, request.returnLevel()));
    }

    @Operation(summary = "Ghi chú yêu cầu xem xét lại khóa")
    @PostMapping("/horses/{id}/lock/review-notes")
    public HorseHealthDto requestReview(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return HorseHealthDto.of(service.requestReview(id, request.note()));
    }

    @Operation(summary = "Đề nghị khóa chờ duyệt")
    @PostMapping("/horses/{id}/pending-lock")
    public HorseHealthDto requestPendingLock(@PathVariable Long id, @Valid @RequestBody LockRequest request) {
        return HorseHealthDto.of(service.requestPendingLock(id, request.reason()));
    }

    @Operation(summary = "Rút đề nghị khóa chờ duyệt")
    @PostMapping("/horses/{id}/pending-lock/withdraw")
    public HorseHealthDto withdrawPendingLock(@PathVariable Long id) {
        return HorseHealthDto.of(service.withdrawPendingLock(id));
    }

    @Operation(summary = "Độ sẵn sàng tập của một con ngựa")
    @GetMapping("/horses/{id}/readiness")
    public ReadinessDto readiness(@PathVariable Long id) {
        return new ReadinessDto(id, service.readiness(id));
    }

    @Operation(summary = "Độ sẵn sàng của các ngựa người dùng được xem")
    @GetMapping("/readiness")
    public List<ReadinessDto> readinessOfVisibleHorses() {
        Map<Long, Readiness> all = service.readinessOfVisibleHorses();
        return all.entrySet().stream().map(e -> new ReadinessDto(e.getKey(), e.getValue())).toList();
    }
}
