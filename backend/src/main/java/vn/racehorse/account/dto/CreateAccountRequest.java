package vn.racehorse.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.racehorse.account.controller.AccountController;
import vn.racehorse.account.entity.Role;
import vn.racehorse.schedule.service.GroomScheduleService;
import vn.racehorse.schedule.dto.GroomScheduleRangeInput;

import java.util.List;

public record CreateAccountRequest(
        @NotBlank(message = AccountController.REQUIRED) @Size(max = 100) String username,
        @NotBlank(message = AccountController.REQUIRED) @Size(max = 200) String displayName,
        @NotBlank(message = AccountController.REQUIRED) @Size(min = 8, max = 100, message = "Mật khẩu cần 8 ký tự trở lên.")
        String password,
        @NotNull(message = AccountController.REQUIRED) List<Role> roles,
        List<GroomScheduleRangeInput> schedule) {
}
