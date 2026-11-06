package vn.racehorse.injury.dto;

import vn.racehorse.injury.entity.MarkStatus;

import java.time.Instant;
import java.time.LocalDate;

public record EventDto(MarkStatus from, MarkStatus to, LocalDate date, String note, String by, Instant at) {
}
