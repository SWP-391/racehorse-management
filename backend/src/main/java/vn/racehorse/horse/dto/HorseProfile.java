package vn.racehorse.horse.dto;

import vn.racehorse.horse.entity.Sex;

import java.time.LocalDate;

public record HorseProfile(String name, Sex sex, LocalDate birthDate, String coatColour,
                      String breed, Long ownerId, Long trainerId) {
}
