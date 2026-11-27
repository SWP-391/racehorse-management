package vn.racehorse.horse.dto;

/**
 * The three people are checked by the service so that a missing one is named (AC-284, AC-541, AC-580): the Head
 * Trainer, the Veterinarian and the Groom are all mandatory (BR-256).
 */
public record TakeInRequest(Long trainerId, Long veterinarianId, Long groomId) {
}
