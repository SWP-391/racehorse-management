package vn.racehorse.horse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.common.paging.Search;
import vn.racehorse.health.service.HealthService;
import vn.racehorse.health.entity.HealthStatus;
import vn.racehorse.horse.dto.AncestorDto;
import vn.racehorse.horse.dto.AncestorRef;
import vn.racehorse.horse.dto.ArchiveRequest;
import vn.racehorse.horse.dto.HorseDto;
import vn.racehorse.horse.dto.HorsePhotoContent;
import vn.racehorse.horse.dto.HorseRequest;
import vn.racehorse.horse.dto.LoanRef;
import vn.racehorse.horse.dto.LoanRequest;
import vn.racehorse.horse.dto.OwnerRef;
import vn.racehorse.horse.dto.PedigreeRequest;
import vn.racehorse.horse.dto.TrainerRef;
import vn.racehorse.horse.dto.TrainerRequest;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.entity.PedigreeAncestor;
import vn.racehorse.horse.entity.PedigreePosition;
import vn.racehorse.horse.service.HorsePhotoService;
import vn.racehorse.horse.service.HorseService;
import vn.racehorse.horse.service.HorseTrainerService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Ngựa", description = "Hồ sơ ngựa, ảnh, phả hệ và huấn luyện viên phụ trách")
@RestController
@RequestMapping("/api/horses")
public class HorseController {

    private final HorseService service;
    private final HorsePhotoService photos;
    private final HealthService health;
    private final HorseTrainerService trainers;
    private final AccessGuard access;

    public HorseController(HorseService service, HorsePhotoService photos, HealthService health,
                           HorseTrainerService trainers, AccessGuard access) {
        this.service = service;
        this.photos = photos;
        this.health = health;
        this.trainers = trainers;
        this.access = access;
    }

    private HorseDto dto(Horse h) {
        AccountPrincipal user = access.current();
        UserAccount effective = trainers.effectiveTrainer(h);
        return new HorseDto(h.getId(), h.getName(), h.getRegistrationNumber(), h.getSex(), h.getBirthDate(),
                h.getCoatColour(), h.getBreed(), new OwnerRef(h.getOwner().getId(), h.getOwner().getDisplayName()),
                h.isArchived(), h.getArchiveReason(), h.getArchiveNote(), h.getArchivedAt(),
                h.getPhotoUpdatedAt() == null ? null : h.getPhotoUpdatedAt().toEpochMilli(),
                TrainerRef.of(effective), TrainerRef.of(h.getMainTrainer()),
                h.getTempTrainer() == null ? null
                        : new LoanRef(h.getTempTrainer().getId(), h.getTempTrainer().getDisplayName(), h.getTempUntil()),
                trainers.canWrite(h, user) && !h.isArchived());
    }

    /** Paged and searchable by name, registration number and owner; the service already reduces it to what the caller may see. */
    @Operation(summary = "Danh sách ngựa (phân trang, tìm theo tên, số đăng ký, chủ)")
    @GetMapping
    public PageResponse<HorseDto> list(@RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) HealthStatus status,
                                       @RequestParam(required = false) Long ownerId,
                                       @RequestParam(required = false) String area,
                                       @RequestParam(required = false) Boolean archived,
                                       @RequestParam(required = false) Long trainerId,
                                       @RequestParam(required = false) Long areaId,
                                       @RequestParam(required = false) Boolean noTrainer) {
        PageParams params = PageParams.of(page, size, q);
        // The health filter needs PERM-020, so an Owner who sends it is refused like on the health page.
        Set<Long> withStatus = status == null ? null : health.herd().stream()
                .filter(v -> v.health().getStatus() == status).map(v -> v.horse().getId()).collect(Collectors.toSet());
        List<Horse> visible = service.list().stream()
                .filter(h -> withStatus == null || withStatus.contains(h.getId()))
                .filter(h -> ownerId == null || h.getOwner().getId().equals(ownerId))
                .filter(h -> area == null || area.isBlank()
                        || (h.getArea() != null && area.trim().equalsIgnoreCase(h.getArea().getName())))
                .filter(h -> areaId == null || (h.getArea() != null && h.getArea().getId().equals(areaId)))
                .filter(h -> archived == null || h.isArchived() == archived)
                .filter(h -> trainerId == null || isAssignedTo(h, trainerId))
                .filter(h -> !Boolean.TRUE.equals(noTrainer) || noActiveTrainer(h))
                .filter(h -> Search.matches(params.q(), h.getName(), h.getRegistrationNumber(),
                        h.getOwner().getDisplayName()))
                .toList();
        return Paging.page(visible.stream().map(this::dto).toList(), params);
    }

    private boolean isAssignedTo(Horse h, Long trainerId) {
        UserAccount effective = trainers.effectiveTrainer(h);
        return effective != null && effective.getId().equals(trainerId);
    }

    /** No trainer at all, or one whose account was deactivated: the Club Manager's "Chưa có HLV" filter (TECH-129). */
    private boolean noActiveTrainer(Horse h) {
        UserAccount effective = trainers.effectiveTrainer(h);
        return effective == null || !effective.isActive();
    }

    /** Changes the main Head Trainer (PERM-045, BR-155). */
    @Operation(summary = "Đổi huấn luyện viên chính")
    @PutMapping("/{id}/trainer")
    public HorseDto changeTrainer(@PathVariable Long id, @RequestBody TrainerRequest request) {
        return dto(service.changeTrainer(id, request.trainerId()));
    }

    /** Lends the horse to another Head Trainer until the return date (BR-156). */
    @Operation(summary = "Cho huấn luyện viên khác mượn ngựa đến ngày trả")
    @PutMapping("/{id}/trainer/temporary")
    public HorseDto lendTrainer(@PathVariable Long id, @Valid @RequestBody LoanRequest request) {
        return dto(service.lendTrainer(id, request.trainerId(), request.until()));
    }

    @Operation(summary = "Kết thúc việc cho mượn ngựa")
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/trainer/temporary")
    public HorseDto endLoan(@PathVariable Long id) {
        return dto(service.endLoan(id));
    }

    /** Photo version by horse id, only for the horses the viewer may see and that have a photo (BR-184: avatars in lists). */
    @Operation(summary = "Phiên bản ảnh theo mã ngựa")
    @GetMapping("/photo-versions")
    public Map<Long, Long> photoVersions() {
        Map<Long, Long> versions = new LinkedHashMap<>();
        service.list().forEach(h -> {
            if (h.getPhotoUpdatedAt() != null) {
                versions.put(h.getId(), h.getPhotoUpdatedAt().toEpochMilli());
            }
        });
        return versions;
    }

    @Operation(summary = "Chi tiết một con ngựa")
    @GetMapping("/{id}")
    public HorseDto get(@PathVariable Long id) {
        return dto(service.get(id));
    }

    /** Adds or replaces the one photo of a horse (BR-184); the file is checked from its content. */
    @Operation(summary = "Thêm hoặc thay ảnh ngựa")
    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HorseDto putPhoto(@PathVariable Long id, @RequestParam(name = "file", required = false) MultipartFile file) {
        return dto(photos.put(id, file));
    }

    /** `variant=thumb` for lists, anything else the full rendition; the URL carries the version, so it never goes stale. */
    @Operation(summary = "Tải ảnh ngựa")
    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> photo(@PathVariable Long id, @RequestParam(required = false) String variant) {
        HorsePhotoContent c = photos.open(id, variant);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .contentType(MediaType.parseMediaType(c.contentType()))
                .body(c.resource());
    }

    @Operation(summary = "Đăng ký ngựa mới")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HorseDto register(@Valid @RequestBody HorseRequest request) {
        return dto(service.register(request.toProfile()));
    }

    @Operation(summary = "Cập nhật hồ sơ ngựa")
    @PutMapping("/{id}")
    public HorseDto update(@PathVariable Long id, @Valid @RequestBody HorseRequest request) {
        return dto(service.update(id, request.toProfile()));
    }

    /** All six positions; unknown ones have neither horse nor name. */
    @Operation(summary = "Xem phả hệ")
    @GetMapping("/{id}/pedigree")
    public List<AncestorDto> pedigree(@PathVariable Long id) {
        return toDtos(service.pedigree(id));
    }

    @Operation(summary = "Cập nhật phả hệ")
    @PutMapping("/{id}/pedigree")
    public List<AncestorDto> updatePedigree(@PathVariable Long id, @RequestBody PedigreeRequest request) {
        return toDtos(service.updatePedigree(id, request.ancestors()));
    }

    @Operation(summary = "Lưu trữ ngựa")
    @PostMapping("/{id}/archive")
    public HorseDto archive(@PathVariable Long id, @Valid @RequestBody ArchiveRequest request) {
        return dto(service.archive(id, request.reason(), request.note()));
    }

    @Operation(summary = "Khôi phục ngựa đã lưu trữ")
    @PostMapping("/{id}/restore")
    public HorseDto restore(@PathVariable Long id) {
        return dto(service.restore(id));
    }

    private static List<AncestorDto> toDtos(Map<PedigreePosition, PedigreeAncestor> known) {
        return Arrays.stream(PedigreePosition.values()).map(position -> {
            PedigreeAncestor a = known.get(position);
            if (a == null) {
                return new AncestorDto(position, null, null);
            }
            Horse h = a.getAncestorHorse();
            return new AncestorDto(position, h == null ? null : new AncestorRef(h.getId(), h.getName()),
                    a.getAncestorName());
        }).toList();
    }
}
