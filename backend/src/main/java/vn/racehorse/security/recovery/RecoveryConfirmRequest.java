package vn.racehorse.security.recovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryConfirmRequest(
        @NotBlank(message = "Bắt buộc nhập.") @Size(max = 254, message = "Email không hợp lệ.") String email,
        @NotBlank(message = "Bắt buộc nhập.") @Size(max = 20, message = PasswordRecoveryService.CODE_REFUSED) String code,
        @NotBlank(message = "Bắt buộc nhập.") @Size(min = 8, max = 100, message = "Mật khẩu cần 8 ký tự trở lên.")
        String newPassword) {
}
