package vn.racehorse.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.racehorse.security.recovery.PasswordRecoveryService;
import vn.racehorse.security.recovery.RecoveryConfirmRequest;
import vn.racehorse.security.recovery.RecoveryRequest;

/** The two steps of the recovery of a forgotten password, open before signing in (PERM-058, BR-214 to BR-217). */
@Tag(name = "Quên mật khẩu", description = "Lấy lại mật khẩu bằng mã gửi về email")
@RestController
@RequestMapping("/api/auth/recovery")
public class RecoveryController {

    private final PasswordRecoveryService service;

    public RecoveryController(PasswordRecoveryService service) {
        this.service = service;
    }

    @Operation(summary = "Xin mã xác nhận qua email (luôn trả lời giống nhau)")
    @PostMapping("/request")
    public PasswordRecoveryService.Answer request(@Valid @RequestBody RecoveryRequest request) {
        return service.request(request.email());
    }

    @Operation(summary = "Đặt mật khẩu mới bằng mã xác nhận")
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody RecoveryConfirmRequest request) {
        service.confirm(request.email(), request.code(), request.newPassword());
    }
}
