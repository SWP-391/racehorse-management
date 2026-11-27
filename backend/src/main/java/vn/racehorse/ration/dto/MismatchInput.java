package vn.racehorse.ration.dto;

/** The Veterinarian's confirmation of a ration that does not fit the horse's condition (BR-251). */
public record MismatchInput(Boolean confirmed, String reason) {
}
