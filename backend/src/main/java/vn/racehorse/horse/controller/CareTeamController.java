package vn.racehorse.horse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.horse.dto.CarePersonLoad;
import vn.racehorse.horse.dto.MoveCareRequest;
import vn.racehorse.horse.dto.UnassignedHorse;
import vn.racehorse.horse.entity.CareRole;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.service.CareTeamService;

import java.util.List;
import java.util.Map;

@Tag(name = "Đội ngũ phụ trách ngựa", description = "Giới hạn 10 ngựa mỗi người, chuyển hàng loạt, ngựa chưa gán")
@RestController
@RequestMapping("/api/care-team")
public class CareTeamController {

    private final CareTeamService service;

    public CareTeamController(CareTeamService service) {
        this.service = service;
    }

    /** People of a role with the horses each looks after as main person and the limit (take-in and move dialogs). */
    @Operation(summary = "Người của một vai trò và số ngựa đang phụ trách (tối đa 10)")
    @GetMapping("/loads")
    public List<CarePersonLoad> loads(@RequestParam CareRole role) {
        return service.loads(role);
    }

    /** Taken-in horses that still miss a person: the warning of the Club Manager ("Chưa gán", BR-260). */
    @Operation(summary = "Ngựa đã tiếp nhận nhưng chưa gán đủ người phụ trách")
    @GetMapping("/unassigned")
    public Map<String, Object> unassigned() {
        List<UnassignedHorse> horses = service.unassigned();
        return Map.of("count", horses.size(), "horses", horses);
    }

    /** Moves every horse a person looks after as main person to another person of the same role, or none (BR-259). */
    @Operation(summary = "Chuyển toàn bộ ngựa của một người sang người khác cùng vai trò")
    @PostMapping("/people/{fromUserId}/move-horses")
    public Map<String, Object> moveAll(@PathVariable Long fromUserId, @RequestBody MoveCareRequest request) {
        List<Horse> moved = service.moveAll(fromUserId, request.role(), request.toUserId());
        return Map.of("moved", moved.size(), "horseIds", moved.stream().map(Horse::getId).toList());
    }
}
