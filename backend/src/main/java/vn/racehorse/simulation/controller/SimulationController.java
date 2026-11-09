package vn.racehorse.simulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.simulation.dto.AlertDto;
import vn.racehorse.simulation.dto.LiveDto;
import vn.racehorse.simulation.dto.SampleDto;
import vn.racehorse.simulation.dto.SimulationLive;
import vn.racehorse.simulation.dto.SimulationThresholdDto;
import vn.racehorse.simulation.dto.SimulationThresholdRequest;
import vn.racehorse.simulation.dto.ThresholdLineDto;
import vn.racehorse.simulation.entity.Measure;
import vn.racehorse.simulation.service.SimulationService;
import vn.racehorse.simulation.service.TelemetrySource;

import java.util.List;

@Tag(name = "Mô phỏng", description = "Chỉ số thời gian thực, ngưỡng và cảnh báo của ngựa")
@RestController
@RequestMapping("/api/horses/{horseId}")
public class SimulationController {

    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    private static SampleDto dto(TelemetrySource.Sample s) {
        return new SampleDto(s.offsetSecond(), s.heartRate(), s.speed());
    }

    @Operation(summary = "Chỉ số thời gian thực")
    @GetMapping("/live")
    public LiveDto live(@PathVariable Long horseId, @RequestParam(required = false) SimulationService.Window window) {
        SimulationLive l = service.live(horseId, window);
        return new LiveDto(l.active(), l.sessionId(), l.dataSource(), l.label(), l.elapsedSeconds(),
                l.current() == null ? null : dto(l.current()), l.recent().stream().map(SimulationController::dto).toList(),
                l.alerts().stream().map(AlertDto::of).toList(),
                l.thresholds().stream().map(t -> new ThresholdLineDto(t.getMeasure(), t.getLimit())).toList(),
                l.summary());
    }

    @Operation(summary = "Xem ngưỡng cảnh báo")
    @GetMapping("/thresholds")
    public List<SimulationThresholdDto> thresholds(@PathVariable Long horseId) {
        return service.thresholds(horseId).stream().map(SimulationThresholdDto::of).toList();
    }

    @Operation(summary = "Đặt ngưỡng cảnh báo")
    @PutMapping("/thresholds")
    public List<SimulationThresholdDto> setThreshold(@PathVariable Long horseId, @RequestBody SimulationThresholdRequest request) {
        return service.setThreshold(horseId, request.measure(), request.limit()).stream().map(SimulationThresholdDto::of).toList();
    }

    @Operation(summary = "Danh sách cảnh báo")
    @GetMapping("/alerts")
    public PageResponse<AlertDto> alerts(@PathVariable Long horseId,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(service.alertsOf(horseId).stream().map(AlertDto::of)
                .filter(a -> vn.racehorse.common.paging.Search.matches(params.q(), a.measure().name(),
                        a.measure() == Measure.HEART_RATE ? "Nhịp tim" : "Vận tốc", a.value().toPlainString(),
                        a.limit().toPlainString()))
                .toList(), params);
    }
}
