package vn.racehorse.security.recovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodeCompleteRequest(
        @NotBlank(message = PasswordRecoveryService.CODE_REFUSED) @Size(max = 200, message = PasswordRecoveryService.CODE_REFUSED) String token,
        @NotBlank(message = "Bắt buộc nhập.") @Size(min = 8, max = 100, message = "Mật khẩu cần 8 ký tự trở lên.")
        String newPassword) {
}
