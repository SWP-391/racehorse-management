package vn.racehorse.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "Bắt buộc nhập.") @Size(max = 100, message = "Mật khẩu tối đa 100 ký tự.") String oldPassword,
        @NotBlank(message = "Bắt buộc nhập.") @Size(min = 8, max = 100, message = "Mật khẩu cần 8 ký tự trở lên.")
        String newPassword) {
}
