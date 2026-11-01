package vn.racehorse.health.dto;

import jakarta.validation.constraints.Size;

public record NoteRequest(@Size(max = 2000) String note) {
}
