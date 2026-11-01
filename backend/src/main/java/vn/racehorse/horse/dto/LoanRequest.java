package vn.racehorse.horse.dto;

import vn.racehorse.common.validation.ClubDate;

import java.time.LocalDate;

public record LoanRequest(Long trainerId, @ClubDate LocalDate until) {
}
