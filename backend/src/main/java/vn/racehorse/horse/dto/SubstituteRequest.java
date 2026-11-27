package vn.racehorse.horse.dto;

import vn.racehorse.common.validation.ClubDate;

import java.time.LocalDate;

/** A substitute Veterinarian or Groom until a return date (BR-258), like {@link LoanRequest} for the trainer. */
public record SubstituteRequest(Long userId, @ClubDate LocalDate until) {
}
