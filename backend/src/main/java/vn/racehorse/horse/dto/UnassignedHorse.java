package vn.racehorse.horse.dto;

import vn.racehorse.horse.entity.CareRole;

import java.util.List;

/** A taken-in horse that still misses a person of some role: "Chưa gán" (BR-260). */
public record UnassignedHorse(Long id, String name, List<CareRole> missing) {
}
