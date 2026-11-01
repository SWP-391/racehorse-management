package vn.racehorse.health.dto;

import jakarta.validation.constraints.Size;
import vn.racehorse.common.validation.ClubDate;

import java.time.LocalDate;

public record LockRequest(@Size(max = 2000) String reason, @ClubDate LocalDate reviewDate) {
}
