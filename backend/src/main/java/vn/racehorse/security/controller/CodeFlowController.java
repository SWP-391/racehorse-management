package vn.racehorse.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.dto.Me;
import vn.racehorse.security.recovery.CodeCompleteRequest;
import vn.racehorse.security.recovery.CodeVerifyRequest;
import vn.racehorse.security.recovery.CodeVerifyResponse;
import vn.racehorse.security.recovery.PasswordRecoveryService;

/** The two steps of activation and recovery, open before signing in: code, then new password and sign-in (BR-255, TECH-F7). */
@Tag(name = "Mã xác nhận hai bước", description = "Kích hoạt và quên mật khẩu: nhập mã, rồi đặt mật khẩu và đăng nhập ngay")
@RestController
@RequestMapping("/api/auth/code")
public class CodeFlowController {

    private final PasswordRecoveryService service;
    private final SecurityContextRepository contextRepository;
    private final AccessGuard access;
    private final vn.racehorse.security.LoginSessionService loginSessions;

    public CodeFlowController(PasswordRecoveryService service, SecurityContextRepository contextRepository,
                              AccessGuard access, vn.racehorse.security.LoginSessionService loginSessions) {
        this.loginSessions = loginSessions;
        this.service = service;
        this.contextRepository = contextRepository;
        this.access = access;
    }

    @Operation(summary = "Bước 1: email và mã cho một mã thông hành dùng một lần, sống 10 phút")
    @PostMapping("/verify")
    public CodeVerifyResponse verify(@Valid @RequestBody CodeVerifyRequest request) {
        return new CodeVerifyResponse(service.verify(request.email(), request.code()));
    }

    /**
     * Step 2. The password is saved in the service; if the sign-in cannot be started after that, the answer is 204 and
     * the client sends the person to the sign-in page (BR-255).
     */
    @Operation(summary = "Bước 2: mật khẩu mới, dùng hết mã và đăng nhập ngay (204 nếu không mở được phiên)")
    @PostMapping("/complete")
    public ResponseEntity<Me> complete(@Valid @RequestBody CodeCompleteRequest request, HttpServletRequest http,
                                       HttpServletResponse response) {
        UserAccount account = service.complete(request.token(), request.newPassword());
        try {
            AccountPrincipal principal = AccountPrincipal.of(account);
            if (http.getSession(false) != null) {
                loginSessions.close(http.getSession(false));
                http.changeSessionId();
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, http, response);
            loginSessions.open(principal.id(), http);
            return ResponseEntity.ok(new Me(principal.id(), principal.email(), principal.displayName(), principal.role(),
                    access.permissionsOf(principal)));
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
            jakarta.servlet.http.HttpSession opened = http.getSession(false);
            if (opened != null) {
                opened.invalidate();
            }
            return ResponseEntity.noContent().build();
        }
    }
}
