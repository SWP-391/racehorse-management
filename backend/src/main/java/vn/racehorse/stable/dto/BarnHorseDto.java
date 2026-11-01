package vn.racehorse.stable.dto;

public record BarnHorseDto(Long horseId, String horseName, String registrationNumber, int choresDone,
                           int choresTotal, String legacyStall) {
    public static BarnHorseDto of(StallBarnRow r) {
        return new BarnHorseDto(r.horse().getId(), r.horse().getName(), r.horse().getRegistrationNumber(),
                r.choresDone(), r.choresTotal(), r.horse().getLegacyStall());
    }
}
