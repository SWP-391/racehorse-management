package vn.racehorse.account.dto;

import jakarta.validation.constraints.NotNull;
import vn.racehorse.account.controller.AccountController;
import vn.racehorse.account.entity.Role;

import java.util.List;

public record RolesRequest(@NotNull(message = AccountController.REQUIRED) List<Role> roles) {
}
