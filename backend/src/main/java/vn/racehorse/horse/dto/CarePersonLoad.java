package vn.racehorse.horse.dto;

/** One person of a role with the horses they look after as main person and the limit, for the take-in and move dialogs. */
public record CarePersonLoad(Long id, String displayName, boolean active, long horses, int limit, boolean full) {
}
