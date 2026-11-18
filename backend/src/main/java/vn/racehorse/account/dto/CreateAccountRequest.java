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
        @NotBlank(message = AccountController.REQUIRED) @Size(max = 254, message = "Email không hợp lệ.") String email,
        @NotBlank(message = AccountController.REQUIRED) @Size(max = 200) String displayName,
        /** Absent or blank: the account is pending and its owner activates it with a mailed code (BR-235). */
        @Size(max = 100) String password,
        @NotNull(message = AccountController.REQUIRED) List<Role> roles,
        List<GroomScheduleRangeInput> schedule) {
}
