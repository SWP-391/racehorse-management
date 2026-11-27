package vn.racehorse.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.account.dto.PasswordChangeRequest;
import vn.racehorse.account.dto.ProfileDto;
import vn.racehorse.account.dto.ProfileRequest;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.service.ProfileService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.LoginSessionService;
import vn.racehorse.security.Permission;
import vn.racehorse.account.dto.LoginSessionDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@Tag(name = "Hồ sơ cá nhân", description = "Họ tên, số điện thoại và mật khẩu của chính mình (mọi vai trò)")
@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final ProfileService service;
    private final SecurityContextRepository contextRepository;
    private final LoginSessionService loginSessions;
    private final AccessGuard access;

    public ProfileController(ProfileService service, SecurityContextRepository contextRepository,
                             LoginSessionService loginSessions, AccessGuard access) {
        this.loginSessions = loginSessions;
        this.access = access;
        this.service = service;
        this.contextRepository = contextRepository;
    }

    @Operation(summary = "Hồ sơ của tôi")
    @GetMapping("/profile")
    public ProfileDto get() {
        return service.get();
    }

    @Operation(summary = "Sửa họ tên và số điện thoại; email chỉ đọc")
    @PutMapping("/profile")
    public ProfileDto update(@RequestBody ProfileRequest request) {
        return service.update(request);
    }

    @Operation(summary = "Đổi mật khẩu: cần mật khẩu hiện tại, các phiên khác bị đăng xuất")
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request, HttpServletRequest http,
                               HttpServletResponse response) {
        UserAccount account = service.changePassword(request.oldPassword(), request.newPassword());
        // The current session moves to the new password version, so it is the only one that stays.
        AccountPrincipal principal = AccountPrincipal.of(account);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, http, response);
        loginSessions.endOthers(account.getId(), http.getSession(false));
    }

    @Operation(summary = "Các phiên đăng nhập của tôi (thiết bị, giờ mở, lần dùng cuối)")
    @GetMapping("/login-sessions")
    public List<LoginSessionDto> loginSessions(HttpServletRequest http) {
        AccountPrincipal me = access.require(Permission.EDIT_OWN_PROFILE);
        HttpSession session = http.getSession();
        return loginSessions.list(me.id(), session).stream().map(LoginSessionDto::of).toList();
    }

    @Operation(summary = "Đăng xuất một phiên khác của tôi")
    @DeleteMapping("/login-sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endLoginSession(@PathVariable long id, HttpServletRequest http) {
        AccountPrincipal me = access.require(Permission.EDIT_OWN_PROFILE);
        loginSessions.end(me.id(), id, http.getSession());
    }
}
