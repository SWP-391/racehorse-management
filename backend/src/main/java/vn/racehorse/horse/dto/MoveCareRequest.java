package vn.racehorse.horse.dto;

import vn.racehorse.horse.entity.CareRole;

/** Move one horse (or all horses of a person) to another person of the same role (BR-259); `MoveCareRequest` of types.ts. */
public record MoveCareRequest(CareRole role, Long toUserId) {
}
