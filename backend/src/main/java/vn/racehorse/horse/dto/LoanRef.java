package vn.racehorse.horse.dto;

import java.time.LocalDate;

public record LoanRef(Long id, String displayName, LocalDate until) {
}
