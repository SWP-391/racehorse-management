package vn.racehorse.injury.dto;

import vn.racehorse.injury.entity.MarkStatus;

public record InjuryMarkStatusRequest(MarkStatus status, String note) {
}
