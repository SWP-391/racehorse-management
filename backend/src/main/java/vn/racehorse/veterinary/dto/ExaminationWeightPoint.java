package vn.racehorse.veterinary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExaminationWeightPoint(LocalDate date, BigDecimal weightKg) {
}
