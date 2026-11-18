package vn.racehorse.account.dto;

import jakarta.validation.constraints.NotBlank;
import vn.racehorse.account.controller.AccountController;

/** Change the display name of an account (BR-239). The length is judged by the service after the name is tidied. */
public record RenameRequest(@NotBlank(message = AccountController.REQUIRED) String displayName) {
}
