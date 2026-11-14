package vn.racehorse.security.recovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryRequest(
        @NotBlank(message = "Bắt buộc nhập.") @Size(max = 254, message = "Email không hợp lệ.") String email) {
}
