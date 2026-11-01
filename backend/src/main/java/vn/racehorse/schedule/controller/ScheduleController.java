package vn.racehorse.schedule.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.common.paging.Search;
import vn.racehorse.schedule.dto.LeaveDto;
import vn.racehorse.schedule.dto.LeaveNoticeDto;
import vn.racehorse.schedule.dto.LeaveNoticeRequest;
import vn.racehorse.schedule.dto.LeaveRequest;
import vn.racehorse.schedule.dto.RangeDto;
import vn.racehorse.schedule.dto.ScheduleDto;
import vn.racehorse.schedule.dto.ScheduleRequest;
import vn.racehorse.schedule.service.GroomScheduleService;
import vn.racehorse.training.service.ClubSettings;

import java.time.LocalDate;

/** Groom barn schedule, leave and the leave notice setting (BR-166, BR-167, TECH-144, TECH-145). */
@Tag(name = "Lịch trực", description = "Lịch làm việc, nghỉ phép của người chăm ngựa và thời hạn báo nghỉ")
@RestController
@RequestMapping("/api")
public class ScheduleController {

    private final GroomScheduleService service;
    private final ClubSettings settings;

    public ScheduleController(GroomScheduleService service, ClubSettings settings) {
        this.service = service;
        this.settings = settings;
    }

    @Operation(summary = "Lịch trực của một tài khoản")
    @GetMapping("/accounts/{id}/schedule")
    public ScheduleDto schedule(@PathVariable Long id) {
        return new ScheduleDto(service.view(id).stream().map(RangeDto::of).toList());
    }

    @Operation(summary = "Thay lịch trực của một tài khoản")
    @PutMapping("/accounts/{id}/schedule")
    public ScheduleDto replace(@PathVariable Long id, @RequestBody ScheduleRequest request) {
        return new ScheduleDto(service.replace(id, request.ranges()).stream().map(RangeDto::of).toList());
    }

    @Operation(summary = "Danh sách nghỉ phép")
    @GetMapping("/accounts/{id}/leave")
    public PageResponse<LeaveDto> leave(@PathVariable Long id,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size,
                                        @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(service.leaveOf(id, from, to).stream().map(LeaveDto::of)
                .filter(l -> Search.matches(params.q(), l.reason(), l.recordedBy(), l.date().toString()))
                .toList(), params);
    }

    @Operation(summary = "Ghi nghỉ phép")
    @PostMapping("/accounts/{id}/leave")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveDto recordLeave(@PathVariable Long id, @Valid @RequestBody LeaveRequest request) {
        return LeaveDto.of(service.recordLeave(id, request.date(), request.reason()));
    }

    @Operation(summary = "Hủy nghỉ phép")
    @PostMapping("/leave/{leaveId}/cancel")
    public LeaveDto cancelLeave(@PathVariable Long leaveId) {
        return LeaveDto.of(service.cancelLeave(leaveId));
    }

    @Operation(summary = "Xem thời hạn báo nghỉ")
    @GetMapping("/settings/leave-notice")
    public LeaveNoticeDto leaveNotice() {
        return new LeaveNoticeDto(settings.leaveNoticeDaysForManager());
    }

    @Operation(summary = "Đặt thời hạn báo nghỉ")
    @PutMapping("/settings/leave-notice")
    public LeaveNoticeDto setLeaveNotice(@Valid @RequestBody LeaveNoticeRequest request) {
        return new LeaveNoticeDto(settings.setLeaveNoticeDays(request.days()));
    }
}
