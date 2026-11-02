package vn.racehorse.schedule.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.schedule.dto.TimetableResponse;
import vn.racehorse.schedule.service.TimetableService;

import java.time.LocalDate;

@Tag(name = "Thời gian biểu", description = "Thời gian biểu của Nhân viên Chăm sóc và HLV (chỉ xem, của chính mình)")
@RestController
@RequestMapping("/api")
public class TimetableController {

    private final TimetableService service;

    public TimetableController(TimetableService service) {
        this.service = service;
    }

    @Operation(summary = "Thời gian biểu của tôi theo khoảng ngày (tối đa 31 ngày)")
    @GetMapping("/my-timetable")
    public TimetableResponse mine(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.mine(from, to);
    }
}
