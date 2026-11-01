package vn.racehorse.horse.dto;

import jakarta.validation.constraints.Size;
import vn.racehorse.horse.entity.ArchiveReason;

public record ArchiveRequest(ArchiveReason reason, @Size(max = 1000) String note) {
}
