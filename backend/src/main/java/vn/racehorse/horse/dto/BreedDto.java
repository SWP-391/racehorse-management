package vn.racehorse.horse.dto;

import vn.racehorse.horse.entity.Breed;

public record BreedDto(Long id, String name, boolean active, boolean protectedEntry) {
    public static BreedDto of(Breed b) {
        return new BreedDto(b.getId(), b.getName(), b.isActive(), b.isProtectedEntry());
    }
}
