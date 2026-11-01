package vn.racehorse.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import vn.racehorse.account.dto.AccountDto;
import vn.racehorse.account.dto.CreateAccountRequest;
import vn.racehorse.account.dto.RolesRequest;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.service.AccountService;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.common.paging.Search;
import vn.racehorse.schedule.service.GroomScheduleService;

@Tag(name = "Tài khoản", description = "Quản lý tài khoản và vai trò (Quản lý CLB)")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    public static final String REQUIRED = "Bắt buộc nhập.";

    private final AccountService service;
    private final GroomScheduleService schedules;

    public AccountController(AccountService service, GroomScheduleService schedules) {
        this.service = service;
        this.schedules = schedules;
    }

    @Operation(summary = "Danh sách tài khoản")
    @GetMapping
    public PageResponse<AccountDto> list(@RequestParam(required = false) Role role,
                                         @RequestParam(required = false) Boolean active,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String q) {
        PageParams params = PageParams.of(page, size, q);
        return Paging.page(service.list(role).stream()
                .filter(a -> active == null || a.isActive() == active)
                .filter(a -> Search.matches(params.q(), a.getUsername(), a.getDisplayName()))
                .map(a -> AccountDto.of(a, a.getRole() == Role.GROOM ? schedules.hasSchedule(a.getId()) : null))
                .toList(), params);
    }

    @Operation(summary = "Tạo tài khoản")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto create(@Valid @RequestBody CreateAccountRequest request) {
        return AccountDto.of(service.create(request.username().trim(), request.displayName().trim(),
                request.password(), request.roles(), request.schedule()));
    }

    @Operation(summary = "Đổi vai trò của tài khoản")
    @PutMapping("/{id}/roles")
    public AccountDto changeRole(@PathVariable Long id, @Valid @RequestBody RolesRequest request) {
        return AccountDto.of(service.changeRole(id, request.roles()));
    }

    @Operation(summary = "Vô hiệu hóa tài khoản")
    @PostMapping("/{id}/deactivate")
    public AccountDto deactivate(@PathVariable Long id) {
        return AccountDto.of(service.deactivate(id));
    }
}
