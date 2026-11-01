package vn.racehorse.veterinary.dto;

import vn.racehorse.veterinary.entity.Examination;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** {@code bodyAreaLegacy} is the unchanged free text of an examination recorded before the catalog (AC-171). */
public record ExaminationDto(Long id, Long horseId, LocalDate examDate, String diagnosis, String bodyRegion,
                             String bodyDetail, String bodyAreaLegacy, BigDecimal weightKg, String treatment,
                             String prescription, String recordedBy, Instant recordedAt) {
    public static ExaminationDto of(Examination e) {
        return new ExaminationDto(e.getId(), e.getHorseId(), e.getExamDate(), e.getDiagnosis(),
                e.getBodyRegion() == null ? null : e.getBodyRegion().name(), e.getBodyDetail(), e.getBodyArea(),
                e.getWeightKg(), e.getTreatment(), e.getPrescription(), e.getRecordedBy().getDisplayName(),
                e.getRecordedAt());
    }
}
