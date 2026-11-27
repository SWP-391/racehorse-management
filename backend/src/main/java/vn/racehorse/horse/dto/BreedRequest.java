package vn.racehorse.horse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.racehorse.common.exception.GlobalExceptionHandler;

public record BreedRequest(@NotBlank(message = GlobalExceptionHandler.REQUIRED) @Size(max = 100) String name,
                           Boolean active) {
}
