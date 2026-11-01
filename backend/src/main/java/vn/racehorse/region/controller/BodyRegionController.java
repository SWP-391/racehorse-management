package vn.racehorse.region.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.region.dto.RegionDto;
import vn.racehorse.region.entity.BodyRegion;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.Permission;

import java.util.Arrays;
import java.util.List;

/** Serves the one body-region catalog that every screen names regions from (BR-110, TECH-052, TECH-053). */
@Tag(name = "Vùng cơ thể", description = "Danh mục vùng cơ thể dùng chung")
@RestController
@RequestMapping("/api")
public class BodyRegionController {

    private final AccessGuard access;

    public BodyRegionController(AccessGuard access) {
        this.access = access;
    }

    /** Readable by every role that records or reads a body region: examinations, incident reports, marks. */
    @Operation(summary = "Danh mục vùng cơ thể")
    @GetMapping("/body-regions")
    public List<RegionDto> regions() {
        access.requireAny(Permission.RECORD_EXAMINATIONS, Permission.VIEW_EXAMINATIONS, Permission.SEND_INCIDENTS,
                Permission.HANDLE_INCIDENTS, Permission.VIEW_INJURY_MARKS);
        return Arrays.stream(BodyRegion.values()).map(r -> new RegionDto(r.name(), r.vietnamese(), r.english())).toList();
    }
}
