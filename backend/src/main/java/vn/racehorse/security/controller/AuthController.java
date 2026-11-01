package vn.racehorse.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.dto.LoginRequest;
import vn.racehorse.security.dto.Me;

/** Sign-in, sign-out and the current user with their permissions (BR-090, BR-091). */
@Tag(name = "Đăng nhập", description = "Đăng nhập, đăng xuất và người dùng hiện tại")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final AccessGuard access;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                          AccessGuard access) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.access = access;
    }

    /** Lets the client obtain the CSRF cookie before signing in. */
    @Operation(summary = "Lấy cookie CSRF trước khi đăng nhập")
    @GetMapping("/csrf")
    public void csrf(@Parameter(hidden = true) CsrfToken token) {
        token.getToken();
    }

    @Operation(summary = "Đăng nhập")
    @PostMapping("/login")
    public Me login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
                    HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Sai tên đăng nhập hoặc mật khẩu, hoặc tài khoản đã bị vô hiệu hóa.");
        }
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return me();
    }

    @Operation(summary = "Đăng xuất")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Operation(summary = "Người dùng hiện tại và quyền")
    @GetMapping("/me")
    public Me me() {
        AccountPrincipal user = access.current();
        return new Me(user.id(), user.username(), user.displayName(), user.role(), access.permissionsOf(user));
    }
}
