package vn.racehorse.stable.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.stable.dto.BarnAreaDto;
import vn.racehorse.stable.dto.BarnDto;
import vn.racehorse.stable.dto.BarnHorseDto;
import vn.racehorse.stable.dto.SlotDto;
import vn.racehorse.stable.dto.StallBarnView;
import vn.racehorse.stable.dto.StallDto;
import vn.racehorse.stable.dto.StallRequest;
import vn.racehorse.stable.service.StallService;

@Tag(name = "Chuồng", description = "Chuồng nuôi và sơ đồ chuồng trại")
@RestController
@RequestMapping("/api")
public class StallController {

    private final StallService service;

    public StallController(StallService service) {
        this.service = service;
    }

    @Operation(summary = "Gán chuồng cho ngựa")
    @PutMapping("/horses/{id}/stall")
    public StallDto set(@PathVariable Long id, @RequestBody StallRequest request) {
        return StallDto.of(service.set(id, request.areaId(), request.slot()));
    }

    @Operation(summary = "Bỏ gán chuồng")
    @DeleteMapping("/horses/{id}/stall")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable Long id) {
        service.clear(id);
    }

    @Operation(summary = "Sơ đồ chuồng trại")
    @GetMapping("/barn")
    public BarnDto barn() {
        StallBarnView view = service.barn();
        return new BarnDto(view.areas().stream().map(a -> new BarnAreaDto(a.area().getId(), a.area().getName(),
                a.area().getCapacity(), a.slots().stream().map(s -> new SlotDto(s.slot(),
                s.horse() == null ? null : BarnHorseDto.of(s.horse()))).toList())).toList(),
                view.unplaced().stream().map(BarnHorseDto::of).toList(), view.canPlace());
    }
}
