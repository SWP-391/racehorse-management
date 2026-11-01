package vn.racehorse.horse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import vn.racehorse.common.exception.GlobalExceptionHandler;
import vn.racehorse.horse.entity.Sex;

import java.time.LocalDate;

/**
 * Mandatory fields per BR-003; the missing field is named in the response (AC-004). The registration number is
 * generated (BR-192), so a request that carries one is refused (AC-386).
 */
public record HorseRequest(
        @NotBlank(message = GlobalExceptionHandler.REQUIRED) @Size(max = 200) String name,
        @Null(message = "Số đăng ký do hệ thống cấp, không nhập tay.") String registrationNumber,
        @NotNull(message = GlobalExceptionHandler.REQUIRED) Sex sex,
        @NotNull(message = GlobalExceptionHandler.REQUIRED) @PastOrPresent(message = "Ngày sinh không được ở tương lai.") LocalDate birthDate,
        @Size(max = 100) String coatColour,
        @Size(max = 100) String breed,
        @NotNull(message = GlobalExceptionHandler.REQUIRED) Long ownerId,
        Long trainerId) {
    public HorseProfile toProfile() {
        return new HorseProfile(name.trim(), sex, birthDate, coatColour, breed,
                ownerId, trainerId);
    }
}
