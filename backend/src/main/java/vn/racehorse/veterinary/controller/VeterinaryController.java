package vn.racehorse.veterinary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import vn.racehorse.stable.repository.SupplyItemRepository;
import vn.racehorse.veterinary.dto.CareCompletionInput;
import vn.racehorse.veterinary.dto.CareDueView;
import vn.racehorse.veterinary.dto.CareEventDto;
import vn.racehorse.veterinary.dto.CareExtras;
import vn.racehorse.veterinary.dto.CareItemDto;
import vn.racehorse.veterinary.dto.CareItemInput;
import vn.racehorse.veterinary.dto.CareItemRequest;
import vn.racehorse.veterinary.dto.CompletionRequest;
import vn.racehorse.veterinary.dto.DueChangeRequest;
import vn.racehorse.veterinary.dto.ExaminationDto;
import vn.racehorse.veterinary.dto.ExaminationInput;
import vn.racehorse.veterinary.dto.ExaminationRequest;
import vn.racehorse.veterinary.dto.ExaminationWeightPoint;
import vn.racehorse.veterinary.dto.VeterinaryPlanRequest;
import vn.racehorse.veterinary.entity.CareItem;
import vn.racehorse.veterinary.entity.CareType;
import vn.racehorse.veterinary.service.CareService;
import vn.racehorse.veterinary.service.ExaminationService;

import java.util.List;
import java.util.Map;

@Tag(name = "Thú y", description = "Khám bệnh, cân nặng và kế hoạch chăm sóc")
@RestController
@RequestMapping("/api")
public class VeterinaryController {

    private final ExaminationService exams;
    private final CareService care;
    private final SupplyItemRepository supplies;

    public VeterinaryController(ExaminationService exams, CareService care, SupplyItemRepository supplies) {
        this.exams = exams;
        this.care = care;
        this.supplies = supplies;
    }

    // ---- examinations -------------------------------------------------------------------------------------

    @Operation(summary = "Ghi lần khám")
    @PostMapping("/horses/{id}/examinations")
    @ResponseStatus(HttpStatus.CREATED)
    public ExaminationDto record(@PathVariable Long id, @Valid @RequestBody ExaminationRequest r) {
        return ExaminationDto.of(exams.record(id, new ExaminationInput(r.diagnosis(), r.bodyRegion(),
                r.bodyDetail(), r.bodyArea(), r.weightKg(), r.treatment(), r.prescription(), r.examDate(), r.feeTypeId())));
    }

    @Operation(summary = "Lịch sử khám")
    @GetMapping("/horses/{id}/examinations")
    public PageResponse<ExaminationDto> history(@PathVariable Long id, @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return Paging.page(exams.history(id).stream().map(ExaminationDto::of).toList(), PageParams.of(page, size, null));
    }

    @Operation(summary = "Lịch sử cân nặng")
    @GetMapping("/horses/{id}/weights")
    public List<ExaminationWeightPoint> weights(@PathVariable Long id) {
        return exams.weights(id);
    }

    // ---- care ---------------------------------------------------------------------------------------------

    @Operation(summary = "Việc chăm sóc đến hạn")
    @GetMapping("/care/due")
    public PageResponse<CareItemDto> due(@RequestParam(required = false) CareType type,
                                         @RequestParam(required = false) CareService.DueState state,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String q) {
        return pageOfCare(care.dueList(), type, state, PageParams.of(page, size, q));
    }

    @Operation(summary = "Việc chăm sóc của một con ngựa")
    @GetMapping("/horses/{id}/care")
    public PageResponse<CareItemDto> careOf(@PathVariable Long id,
                                            @RequestParam(required = false) CareType type,
                                            @RequestParam(required = false) CareService.DueState state,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String q) {
        return pageOfCare(care.forHorse(id), type, state, PageParams.of(page, size, q));
    }

    private PageResponse<CareItemDto> pageOfCare(List<CareDueView> views, CareType type,
                                                 CareService.DueState state, PageParams params) {
        PageResponse<CareDueView> paged = Paging.page(views.stream()
                .filter(v -> type == null || v.item().getType() == type)
                .filter(v -> state == null || v.state() == state)
                .filter(v -> Search.matches(params.q(), v.item().getName(), v.horse().getName()))
                .toList(), params);
        Map<Long, CareExtras> extras = care.extras(paged.items().stream().map(v -> v.item().getId()).toList());
        Map<Long, String> names = supplyNames(paged.items().stream().map(v -> v.item().getPlannedSupplyItemId()).toList());
        return new PageResponse<>(paged.items().stream()
                .map(v -> CareItemDto.of(v, extras.get(v.item().getId()), names)).toList(),
                paged.page(), paged.size(), paged.total());
    }

    @Operation(summary = "Tạo việc chăm sóc")
    @PostMapping("/horses/{id}/care")
    @ResponseStatus(HttpStatus.CREATED)
    public CareItemDto create(@PathVariable Long id, @Valid @RequestBody CareItemRequest r) {
        CareItem item = care.create(id, new CareItemInput(r.type(), r.name(), r.intervalDays(), r.firstDue(),
                r.plannedSupplyItemId(), r.plannedQuantity()));
        return view(item);
    }

    @Operation(summary = "Hoàn thành việc chăm sóc")
    @PostMapping("/care/{id}/complete")
    public CareItemDto complete(@PathVariable Long id, @Valid @RequestBody CompletionRequest r) {
        return view(care.complete(id, new CareCompletionInput(r.dateDone(), r.performer(), r.product(),
                r.batch(), r.note(), r.supplyItemId(), r.quantity())));
    }

    @Operation(summary = "Đặt kế hoạch lặp lại")
    @PutMapping("/care-items/{id}/plan")
    public CareItemDto setPlan(@PathVariable Long id, @Valid @RequestBody VeterinaryPlanRequest r) {
        return view(care.setPlan(id, r.supplyItemId(), r.quantity()));
    }

    @Operation(summary = "Bỏ kế hoạch lặp lại")
    @DeleteMapping("/care-items/{id}/plan")
    public CareItemDto clearPlan(@PathVariable Long id) {
        return view(care.clearPlan(id));
    }

    @Operation(summary = "Ghi đè việc chăm sóc")
    @PostMapping("/care/{id}/override")
    public CareItemDto override(@PathVariable Long id, @Valid @RequestBody DueChangeRequest r) {
        return view(care.overrideDue(id, r.newDue(), r.reason()));
    }

    @Operation(summary = "Hoãn việc chăm sóc")
    @PostMapping("/care/{id}/postpone")
    public CareItemDto postpone(@PathVariable Long id, @Valid @RequestBody DueChangeRequest r) {
        return view(care.postpone(id, r.newDue(), r.reason()));
    }

    @Operation(summary = "Lịch sử việc chăm sóc")
    @GetMapping("/care/{id}/history")
    public PageResponse<CareEventDto> careHistory(@PathVariable Long id,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(care.history(id).stream().map(CareEventDto::of)
                .filter(e -> Search.matches(params.q(), e.performer(), e.product(), e.batch(), e.note(), e.by()))
                .toList(), params);
    }

    private CareItemDto view(CareItem item) {
        CareDueView v = care.forHorse(item.getHorseId()).stream()
                .filter(x -> x.item().getId().equals(item.getId())).findFirst().orElseThrow();
        return CareItemDto.of(v, care.extras(List.of(item.getId())).get(item.getId()),
                supplyNames(java.util.Collections.singletonList(item.getPlannedSupplyItemId())));
    }

    private Map<Long, String> supplyNames(List<Long> ids) {
        Map<Long, String> names = new java.util.HashMap<>();
        ids.stream().filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> supplies.findById(id).ifPresent(i -> names.put(id, i.getName())));
        return names;
    }
}
