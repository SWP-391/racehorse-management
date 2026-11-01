package vn.racehorse.horse.event;

/** Published inside the registration transaction; the health module reacts synchronously (TECH-016). */
public record HorseRegistered(Long horseId, String horseName) {
}
