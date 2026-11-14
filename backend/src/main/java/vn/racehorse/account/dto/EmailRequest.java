package vn.racehorse.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.racehorse.account.controller.AccountController;

/** Enter the missing email of an account, or correct it with a reason (BR-212). */
public record EmailRequest(
        @NotBlank(message = AccountController.REQUIRED) @Size(max = 254, message = "Email không hợp lệ.") String email,
        @Size(max = 500, message = "Lý do tối đa 500 ký tự.") String reason) {
}
