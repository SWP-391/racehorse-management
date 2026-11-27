package vn.racehorse.horse.dto;

import jakarta.validation.constraints.Size;

/** The reason is checked by the service so that a missing one is named (AC-543). */
public record RefuseRequest(@Size(max = 1000) String reason) {
}
