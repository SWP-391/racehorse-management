package vn.racehorse.horse.event;

/** Published inside the take-in transaction (the intake lock and the starting status start there, BR-008, TECH-B2); the health module reacts synchronously (TECH-016). */
public record HorseRegistered(Long horseId, String horseName) {
}
