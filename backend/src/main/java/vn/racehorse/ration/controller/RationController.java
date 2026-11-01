package vn.racehorse.ration.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.ration.dto.ItemDto;
import vn.racehorse.ration.dto.MealDto;
import vn.racehorse.ration.dto.RationDto;
import vn.racehorse.ration.dto.RationOptionDto;
import vn.racehorse.ration.dto.RationView;
import vn.racehorse.ration.dto.SaveRequest;
import vn.racehorse.ration.dto.TodayDto;
import vn.racehorse.ration.dto.VersionDto;
import vn.racehorse.ration.entity.RationMeal;
import vn.racehorse.ration.service.RationService;

import java.util.List;

@Tag(name = "Khẩu phần", description = "Khẩu phần ăn đã duyệt của từng con ngựa")
@RestController
@RequestMapping("/api")
public class RationController {

    private final RationService service;

    public RationController(RationService service) {
        this.service = service;
    }

    private static RationDto dto(RationView v) {
        return new RationDto(v.horse().getId(), v.horse().getName(), v.horse().isArchived(),
                v.current() == null ? null : VersionDto.of(v.current()),
                v.history() == null ? null : v.history().stream().map(VersionDto::of).toList());
    }

    @Operation(summary = "Khẩu phần của một con ngựa")
    @GetMapping("/horses/{id}/ration")
    public RationDto get(@PathVariable Long id) {
        return dto(service.view(id));
    }

    @Operation(summary = "Lưu khẩu phần của một con ngựa")
    @PutMapping("/horses/{id}/ration")
    public RationDto save(@PathVariable Long id, @RequestBody SaveRequest request) {
        return dto(service.save(id, request == null ? null : request.meals()));
    }

    /** Active Thức ăn items for the Veterinarian's picker (TECH-101). */
    @Operation(summary = "Thức ăn có thể chọn cho khẩu phần")
    @GetMapping("/rations/options")
    public List<RationOptionDto> options() {
        return service.supplyOptions().stream().map(i -> new RationOptionDto(i.getId(), i.getName(), i.getUnit())).toList();
    }

    /** Current meals of the horses the viewer may see, for the chores page (TECH-103). */
    @Operation(summary = "Bữa ăn hiện tại của các ngựa người dùng được xem")
    @GetMapping("/rations/today")
    public List<TodayDto> today() {
        return service.currentForVisibleHorses().entrySet().stream()
                .map(e -> new TodayDto(e.getKey().getId(), e.getValue().getMeals().stream()
                        .map(MealDto::of).toList())).toList();
    }
}
