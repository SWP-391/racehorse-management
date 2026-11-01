package vn.racehorse.veterinary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code bodyArea} is free text from an old client; it is refused, the catalog code is required (BR-110). */
public record ExaminationInput(String diagnosis, String bodyRegion, String bodyDetail, String bodyArea, BigDecimal weightKg,
                    String treatment, String prescription, LocalDate examDate, Long feeTypeId) {
}
