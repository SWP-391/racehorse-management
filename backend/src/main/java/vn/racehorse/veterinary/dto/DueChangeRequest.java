package vn.racehorse.veterinary.dto;

import vn.racehorse.common.validation.ClubDate;

import java.time.LocalDate;

public record DueChangeRequest(@ClubDate LocalDate newDue, String reason) {
}
