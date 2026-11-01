package vn.racehorse.stable.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import vn.racehorse.stable.dto.BackfillRequest;
import vn.racehorse.stable.dto.ChoreCancelRequest;
import vn.racehorse.stable.dto.ChoreDay;
import vn.racehorse.stable.dto.ChoreDto;
import vn.racehorse.stable.dto.ChoreHistoryDto;
import vn.racehorse.stable.dto.ChoreHorseRange;
import vn.racehorse.stable.dto.ChoreMarkRequest;
import vn.racehorse.stable.dto.DayChoresDto;
import vn.racehorse.stable.dto.DayDto;
import vn.racehorse.stable.dto.HorseChoresDto;
import vn.racehorse.stable.dto.HorseRangeDto;
import vn.racehorse.stable.dto.StallDto;
import vn.racehorse.stable.service.ChoreService;

import java.time.LocalDate;

@Tag(name = "Việc chăm sóc", description = "Việc hằng ngày của người chăm ngựa")
@RestController
@RequestMapping("/api")
public class ChoreController {

    private final ChoreService service;

    public ChoreController(ChoreService service) {
        this.service = service;
    }

    /** The chore history of a date range, 20 horses a page (default: the last 7 days, read-only; BR-188). */
    @Operation(summary = "Lịch sử việc theo khoảng ngày")
    @GetMapping("/chores/range")
    public PageResponse<HorseRangeDto> range(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long horseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q) {
        PageResponse<ChoreHorseRange> paged = service.range(from, to, horseId, PageParams.of(page, size, q));
        return new PageResponse<>(paged.items().stream().map(r -> new HorseRangeDto(r.horse().getId(), r.horse().getName(),
                r.horse().getRegistrationNumber(), r.days().stream().map(d -> new DayChoresDto(d.date(),
                d.chores().stream().map(ChoreDto::of).toList())).toList())).toList(),
                paged.page(), paged.size(), paged.total());
    }

    @Operation(summary = "Việc trong ngày")
    @GetMapping("/chores")
    public DayDto list(@RequestParam(required = false) String date,
                       @RequestParam(required = false) Boolean pending,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size,
                       @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        ChoreDay day = service.list(date);
        PageResponse<HorseChoresDto> paged = Paging.page(day.horses().stream()
                .filter(h -> !Boolean.TRUE.equals(pending) || h.chores().stream().anyMatch(c -> !c.done()))
                .filter(h -> Search.matches(params.q(), h.horse().getName(), h.horse().getRegistrationNumber()))
                .map(h -> new HorseChoresDto(h.horse().getId(), h.horse().getName(),
                        h.horse().getRegistrationNumber(), StallDto.of(h.horse()),
                        h.chores().stream().map(ChoreDto::of).toList())).toList(), params);
        return new DayDto(day.date(), day.today(), paged.items(), paged.page(), paged.size(), paged.total());
    }

    @Operation(summary = "Đánh dấu hoàn thành việc")
    @PostMapping("/horses/{id}/chores/{chore}/done")
    @ResponseStatus(HttpStatus.CREATED)
    public ChoreDto mark(@PathVariable Long id, @PathVariable String chore,
                         @RequestBody(required = false) ChoreMarkRequest request) {
        return ChoreDto.of(service.mark(id, chore, request == null ? null : request.date()));
    }

    /** The Club Manager marks a chore of a past day with a reason (BR-174). */
    @Operation(summary = "Bổ sung việc của ngày đã qua (kèm lý do)")
    @PostMapping("/chores/backfill")
    @ResponseStatus(HttpStatus.CREATED)
    public ChoreDto backfill(@RequestBody BackfillRequest request) {
        return ChoreDto.of(service.backfill(request.horseId(), request.chore(), request.date(), request.reason()));
    }

    @Operation(summary = "Hủy đánh dấu hoàn thành")
    @PostMapping("/chore-entries/{id}/cancel")
    public ChoreDto cancel(@PathVariable Long id, @RequestBody(required = false) ChoreCancelRequest request) {
        return ChoreDto.of(service.cancel(id, request == null ? null : request.reason()));
    }

    @Operation(summary = "Lịch sử việc của một con ngựa")
    @GetMapping("/horses/{id}/chores/history")
    public PageResponse<ChoreHistoryDto> history(@PathVariable Long id, @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return Paging.page(service.history(id).stream().map(ChoreHistoryDto::of).toList(), PageParams.of(page, size, null));
    }
}
