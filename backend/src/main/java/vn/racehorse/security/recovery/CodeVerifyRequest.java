package vn.racehorse.security.recovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodeVerifyRequest(
        @NotBlank(message = "Bắt buộc nhập.") @Size(max = 254, message = "Email không hợp lệ.") String email,
        @NotBlank(message = "Bắt buộc nhập.") @Size(max = 20, message = PasswordRecoveryService.CODE_REFUSED) String code) {
}
