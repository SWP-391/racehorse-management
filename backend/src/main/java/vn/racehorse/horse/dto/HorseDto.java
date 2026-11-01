package vn.racehorse.horse.dto;

import vn.racehorse.horse.entity.ArchiveReason;
import vn.racehorse.horse.entity.Sex;

import java.time.Instant;
import java.time.LocalDate;

public record HorseDto(Long id, String name, String registrationNumber, Sex sex, LocalDate birthDate,
                       String coatColour, String breed, OwnerRef owner, boolean archived,
                       ArchiveReason archiveReason, String archiveNote, Instant archivedAt, Long photoVersion,
                       TrainerRef assignedTrainer, TrainerRef mainTrainer, LoanRef temporaryTrainer,
                       boolean canWrite) {
}
